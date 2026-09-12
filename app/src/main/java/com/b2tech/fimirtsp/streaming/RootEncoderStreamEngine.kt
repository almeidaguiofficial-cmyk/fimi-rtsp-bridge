package com.b2tech.fimirtsp.streaming

import android.content.Context
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import com.b2tech.fimirtsp.settings.RtspTransport
import com.b2tech.fimirtsp.util.LogSanitizer
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.sources.audio.NoAudioSource
import com.pedro.encoder.input.sources.video.NoVideoSource
import com.pedro.encoder.input.sources.video.ScreenSource
import com.pedro.library.rtsp.RtspStream

/**
 * Motor de streaming apoiado na RootEncoder (pedroSG94). Usa RtspStream porque
 * expõe diretamente o RtspStreamClient com setProtocol/setAuthorization —
 * cobre RTSP TCP/UDP, autenticação Basic/Digest e o mesmo pipeline de captura
 * do MediaProjection já validado no Skywatch Cast.
 */
class RootEncoderStreamEngine(
    private val context: Context,
) : StreamEngine {

    private val projectionManager: MediaProjectionManager =
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    private var listener: StreamEngine.Listener? = null
    private var mediaProjection: MediaProjection? = null
    private var screenSource: ScreenSource? = null
    private var encWidth = 0
    private var encHeight = 0
    private var currentFps = 30
    private var currentBitrate = 3_000_000

    private val connectChecker = object : ConnectChecker {
        override fun onConnectionStarted(url: String) { listener?.onConnecting() }
        override fun onConnectionSuccess() { listener?.onConnected(currentFps, currentBitrate) }
        override fun onConnectionFailed(reason: String) {
            listener?.onConnectionFailed(LogSanitizer.sanitize(reason))
        }
        override fun onNewBitrate(bitrate: Long) { listener?.onBitrateSample(bitrate) }
        override fun onDisconnect() { listener?.onDisconnected() }
        override fun onAuthError() { listener?.onAuthError() }
        override fun onAuthSuccess() { }
    }

    private val stream: RtspStream by lazy {
        RtspStream(context.applicationContext, connectChecker, NoVideoSource(), NoAudioSource()).also {
            // Áudio desligado, mas prepareAudio precisa acontecer para o pipeline nascer.
            try {
                it.prepareAudio(32_000, true, 128_000)
            } catch (_: IllegalArgumentException) {
                // Ignorável: o RootEncoder aceita o pipeline sem áudio válido depois.
            }
        }
    }

    override fun setListener(listener: StreamEngine.Listener?) {
        this.listener = listener
    }

    override fun prepare(request: StreamEngine.PrepareRequest): StreamEngine.PrepareResult {
        // Sessão nova de MediaProjection: sempre a partir do result do usuário,
        // token novo por sessão, conforme regra do Android 14+.
        mediaProjection?.stop()
        mediaProjection = null

        if (stream.isStreaming) stream.stopStream()

        try { stream.getGlInterface().setForceRender(true, request.fps) } catch (_: Throwable) {}

        // Aplica transporte e credenciais RTSP antes de start.
        applyRtspClientConfig(request.transport, request.user, request.password)

        currentFps = request.fps.coerceIn(10, 60)
        currentBitrate = request.bitrateBps

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
        val source = try {
            ScreenSource(context.applicationContext, mp)
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

    /**
     * A API do RtspStreamClient muda entre versões da RootEncoder; para não travar em
     * uma assinatura específica, tentamos via reflexão. Nas versões atuais o client
     * expõe setProtocol(Protocol) e setAuthorization(user, pass). Se qualquer chamada
     * falhar, o cliente segue no padrão (TCP, sem autenticação) — nunca lançamos.
     */
    private fun applyRtspClientConfig(transport: RtspTransport, user: String, password: String) {
        val client: Any = try {
            stream.getStreamClient()
        } catch (_: Throwable) {
            return
        }
        applyProtocol(client, transport)
        applyCredentials(client, user, password)
    }

    private fun applyProtocol(client: Any, transport: RtspTransport) {
        try {
            val protoClass = Class.forName("com.pedro.rtsp.rtsp.Protocol")
            val proto = protoClass.enumConstants?.firstOrNull {
                (it as Enum<*>).name.equals(transport.name, ignoreCase = true)
            } ?: return
            val method = client.javaClass.methods.firstOrNull {
                it.name == "setProtocol" && it.parameterTypes.size == 1
            } ?: return
            method.invoke(client, proto)
        } catch (_: Throwable) {
            // Fallback: RootEncoder mantém TCP por padrão para RTSP.
        }
    }

    private fun applyCredentials(client: Any, user: String, password: String) {
        if (user.isEmpty() && password.isEmpty()) return
        try {
            val method = client.javaClass.methods.firstOrNull {
                it.name == "setAuthorization" && it.parameterTypes.size == 2
            } ?: return
            method.invoke(client, user, password)
        } catch (_: Throwable) {
            // Sem log da senha, mesmo em stack trace: silêncio proposital.
        }
    }

    override fun startStream(endpoint: String) {
        if (!stream.isStreaming) {
            stream.startStream(endpoint)
        }
    }

    override fun stopStream() {
        if (stream.isStreaming) stream.stopStream()
    }

    override fun release() {
        if (stream.isStreaming) stream.stopStream()
        try { stream.release() } catch (_: Throwable) {}
        try { mediaProjection?.stop() } catch (_: Throwable) {}
        mediaProjection = null
        screenSource = null
    }

    override fun isStreaming(): Boolean = stream.isStreaming

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
