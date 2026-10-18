package com.b2tech.fimirtsp.streaming

import android.content.Context
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.b2tech.fimirtsp.settings.RtspTransport
import com.b2tech.fimirtsp.util.LogSanitizer
import com.pedro.common.ConnectChecker
import com.pedro.common.StreamingStatsReport
import com.pedro.common.Throughput
import com.pedro.encoder.input.sources.audio.NoAudioSource
import com.pedro.encoder.input.sources.video.NoVideoSource
import com.pedro.encoder.input.sources.video.ScreenSource
import com.pedro.library.generic.GenericStream
import com.pedro.library.util.QueueAwareBitrateAdapter
import com.pedro.rtsp.rtsp.Protocol

/**
 * Motor de streaming apoiado na RootEncoder (pedroSG94). GenericStream escolhe o
 * cliente pelo esquema da URL (rtsp/rtsps/rtmp/rtmps/srt), e o GenericStreamClient
 * expõe setProtocol (TCP/UDP do RTSP), setAuthorization (RTMP e RTSP) e
 * setOnlyVideo — o mesmo pipeline de captura já validado no Skywatch Cast.
 */
class RootEncoderStreamEngine(
    private val context: Context,
) : StreamEngine {

    private companion object {
        // Abaixo disso a tela do FIMI vira borrão; melhor segurar e deixar o guarda de atraso agir.
        const val MIN_ADAPTIVE_BITRATE_BPS = 300_000
        // De quanto em quanto tempo o adaptador volta a testar o teto depois de uma queda (s).
        const val ADAPTIVE_CEILING_TTL_S = 60
    }

    private val projectionManager: MediaProjectionManager =
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    private var listener: StreamEngine.Listener? = null
    private var mediaProjection: MediaProjection? = null
    private var screenSource: ScreenSource? = null
    private var encWidth = 0
    private var encHeight = 0
    private var currentFps = 30
    private var currentBitrate = 3_000_000
    private var adaptToNetwork = true
    private var adaptedBitrate = currentBitrate
    private var bitrateAdapter: QueueAwareBitrateAdapter? = null
    private var latencyGuard = LatencyGuard()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val connectChecker = object : ConnectChecker {
        override fun onConnectionStarted(url: String) { listener?.onConnecting() }
        override fun onConnectionSuccess() { listener?.onConnected(currentFps, currentBitrate) }
        override fun onConnectionFailed(reason: String) {
            listener?.onConnectionFailed(LogSanitizer.sanitize(reason))
        }
        override fun onNewBitrate(bitrate: Long) { listener?.onBitrateSample(bitrate) }
        // A lib entrega na main thread, uma vez por segundo, com o estado da fila de envio.
        override fun onStreamingStats(report: StreamingStatsReport) { handleStreamingStats(report) }
        override fun onDisconnect() { listener?.onDisconnected() }
        override fun onAuthError() { listener?.onAuthError() }
        override fun onAuthSuccess() { }
    }

    // Lazy explícito: stop/release/isStreaming não devem construir 4 clients + codec AAC
    // só para desmontar quando o serviço morre antes de qualquer prepare().
    private val streamLazy = lazy {
        GenericStream(context.applicationContext, connectChecker, NoVideoSource(), NoAudioSource()).also {
            prepareSilentAudio(it)
        }
    }
    private val stream: GenericStream by streamLazy

    // StreamBase exige prepareAudio para o pipeline nascer, mesmo sem microfone;
    // setOnlyVideo(true) no client evita anunciar faixa de áudio ao servidor.
    private fun prepareSilentAudio(s: GenericStream) {
        try {
            s.prepareAudio(32_000, true, 128_000)
        } catch (_: Throwable) {
        }
    }

    override fun setListener(listener: StreamEngine.Listener?) {
        this.listener = listener
    }

    override fun prepare(request: StreamEngine.PrepareRequest): StreamEngine.PrepareResult {
        // stopStream() devolve o resultado de prepareEncoders(); se falhar, o áudio
        // precisa ser preparado de novo antes do próximo startStream.
        if (stream.isStreaming && !stream.stopStream()) {
            prepareSilentAudio(stream)
        }

        // Sessão nova de MediaProjection: sempre a partir do result do usuário,
        // token novo por sessão, conforme regra do Android 14+.
        try { mediaProjection?.stop() } catch (_: Throwable) {}
        mediaProjection = null
        screenSource = null

        try { stream.getGlInterface().setForceRender(true, request.fps) } catch (_: Throwable) {}

        val client = stream.getStreamClient()
        client.setOnlyVideo(true)
        client.setProtocol(if (request.transport == RtspTransport.UDP) Protocol.UDP else Protocol.TCP)
        if (request.user.isNotEmpty() || request.password.isNotEmpty()) {
            client.setAuthorization(request.user, request.password)
        } else {
            client.setAuthorization(null, null)
        }
        // Reconexão é decidida pelo serviço (backoff próprio) via retry(); a lib só precisa
        // de crédito de tentativas para aceitar o reTry.
        client.setReTries(Int.MAX_VALUE)

        currentFps = request.fps.coerceIn(10, 60)
        currentBitrate = request.bitrateBps
        adaptedBitrate = currentBitrate
        adaptToNetwork = request.adaptToNetwork
        latencyGuard = LatencyGuard()
        // Guiado pela fila (não pelo bitrate medido): quando frames começam a empilhar, a rede é
        // o limite, então ele fica abaixo do que a rede entregou em vez de voltar a forçar o teto.
        // Sobrevive aos reTry da sessão — reconectar num link ruim não deve recomeçar no máximo.
        bitrateAdapter = if (adaptToNetwork) {
            QueueAwareBitrateAdapter(
                currentBitrate,
                minOf(MIN_ADAPTIVE_BITRATE_BPS, currentBitrate),
                ADAPTIVE_CEILING_TTL_S,
            ) { bps -> applyAdaptedBitrate(bps) }
        } else {
            null
        }

        val candidates = buildList {
            add(request.targetWidth to request.targetHeight)
            addAll(request.fallbackSizes)
        }.distinct()

        var accepted: Pair<Int, Int>? = null
        for ((w, h) in candidates) {
            val ok = try {
                stream.prepareVideo(w, h, currentBitrate, currentFps, iFrameInterval = 1, rotation = 0)
            } catch (_: IllegalArgumentException) {
                false
            } catch (_: IllegalStateException) {
                false
            }
            if (ok) {
                accepted = w to h
                break
            }
        }
        if (accepted == null) {
            return StreamEngine.PrepareResult.Failed("Nenhuma resolução aceita pelo encoder")
        }
        encWidth = accepted.first
        encHeight = accepted.second

        val mp = try {
            projectionManager.getMediaProjection(request.projectionResultCode, request.projectionData)
        } catch (t: Throwable) {
            return StreamEngine.PrepareResult.Failed("MediaProjection recusou: ${LogSanitizer.sanitize(t.message)}")
        } ?: return StreamEngine.PrepareResult.Failed("MediaProjection nula")

        mediaProjection = mp
        // O sistema pode encerrar a captura (chip "Parar compartilhamento", bloqueio de tela,
        // revogação): sem este callback o encoder seguiria publicando o último frame congelado.
        val projectionCallback = object : MediaProjection.Callback() {
            override fun onStop() {
                mainHandler.post {
                    if (mediaProjection === mp) listener?.onCaptureStopped()
                }
            }
        }
        val source = try {
            ScreenSource(context.applicationContext, mp, projectionCallback)
        } catch (t: Throwable) {
            mp.stop()
            mediaProjection = null
            return StreamEngine.PrepareResult.Failed("Falha ao iniciar captura: ${LogSanitizer.sanitize(t.message)}")
        }
        screenSource = source

        return try {
            stream.changeVideoSource(source)
            StreamEngine.PrepareResult.Ok(encWidth, encHeight)
        } catch (t: Throwable) {
            mp.stop()
            mediaProjection = null
            screenSource = null
            StreamEngine.PrepareResult.Failed("Falha ao ligar captura: ${LogSanitizer.sanitize(t.message)}")
        }
    }

    override fun startStream(endpoint: String): Boolean {
        if (stream.isStreaming) return true
        return try {
            stream.startStream(SrtUrl.withDefaultLatency(endpoint))
            true
        } catch (t: Throwable) {
            // A lib já marcou isStreaming=true antes de subir captura/encoder: desfaz.
            try { stream.stopStream() } catch (_: Throwable) {}
            listener?.onConnectionFailed("Falha ao iniciar captura/encoder: ${LogSanitizer.sanitize(t.message)}")
            false
        }
    }

    override fun retry(delayMs: Long, reason: String): Boolean {
        if (!streamLazy.isInitialized() || !stream.isStreaming) return false
        return try {
            stream.getStreamClient().reTry(delayMs, reason)
        } catch (_: Throwable) {
            false
        }
    }

    override fun stopStream() {
        if (streamLazy.isInitialized() && stream.isStreaming) stream.stopStream()
    }

    override fun release() {
        listener = null
        if (streamLazy.isInitialized()) {
            if (stream.isStreaming) try { stream.stopStream() } catch (_: Throwable) {}
            try { stream.release() } catch (_: Throwable) {}
        }
        try { mediaProjection?.stop() } catch (_: Throwable) {}
        mediaProjection = null
        screenSource = null
        bitrateAdapter = null
    }

    override fun isStreaming(): Boolean = streamLazy.isInitialized() && stream.isStreaming

    private fun applyAdaptedBitrate(bps: Int) {
        adaptedBitrate = bps
        try { stream.setVideoBitrateOnFly(bps) } catch (_: Throwable) {}
    }

    private fun handleStreamingStats(report: StreamingStatsReport) {
        if (!isStreaming()) return
        val client = stream.getStreamClient()
        // setOnlyVideo(true) desliga o áudio no client: tudo na fila é frame de vídeo.
        val queueSeconds = client.getItemsInCache().toFloat() / currentFps
        if (adaptToNetwork) {
            bitrateAdapter?.onStreamingStats(report)
            if (latencyGuard.shouldSkip(queueSeconds, SystemClock.elapsedRealtime())) {
                try {
                    client.clearCache()
                    // Os P-frames seguintes apontam para frames descartados; o keyframe encurta o
                    // borrão até o próximo GOP (que já é de 1 s).
                    stream.requestKeyframe()
                } catch (_: Throwable) {
                }
            }
        }
        listener?.onNetworkStats(
            NetworkStats(
                targetBitrateBps = adaptedBitrate,
                maxBitrateBps = currentBitrate,
                queueSeconds = queueSeconds,
                quality = LinkQualityClassifier.classify(
                    hasMeasurement = report.smoothedBitrate > 0,
                    insufficient = report.throughput == Throughput.INSUFFICIENT,
                    queueSeconds = queueSeconds,
                    targetBitrateBps = adaptedBitrate,
                    maxBitrateBps = currentBitrate,
                ),
                liveSkips = latencyGuard.skips,
                adaptive = adaptToNetwork,
            )
        )
    }

    override fun onOrientationChanged(landscape: Boolean) {
        val src = screenSource ?: return
        if (encWidth == 0 || encHeight == 0) return
        try {
            if (landscape) src.resize(encWidth, encHeight)
            else src.resize(encHeight, encWidth)
        } catch (_: Throwable) {
            // Se a lib recusar o resize, mantemos o VirtualDisplay atual.
        }
    }
}
