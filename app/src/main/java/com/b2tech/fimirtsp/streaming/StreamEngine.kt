package com.b2tech.fimirtsp.streaming

import android.content.Intent
import com.b2tech.fimirtsp.settings.RtspTransport

/**
 * Contrato do motor de transmissão. A implementação atual usa a RootEncoder,
 * mas todo o resto do app fala só com esta interface — trocar depois é local.
 */
interface StreamEngine {

    /** Callbacks emitidos pela biblioteca (rede/encoder). Sempre em thread do serviço. */
    interface Listener {
        fun onConnecting()
        fun onConnected(fps: Int, bitrateBps: Int)
        fun onDisconnected()
        fun onConnectionFailed(reason: String)
        fun onAuthError()
        fun onBitrateSample(bps: Long)
        /** Estado da fila de envio e do bitrate adaptado, uma vez por segundo (main thread). */
        fun onNetworkStats(stats: NetworkStats)
        /** O sistema encerrou a MediaProjection (chip "Parar compartilhamento", revogação, bloqueio). */
        fun onCaptureStopped()
    }

    data class PrepareRequest(
        val projectionResultCode: Int,
        val projectionData: Intent,
        val targetWidth: Int,
        val targetHeight: Int,
        val fps: Int,
        val bitrateBps: Int,
        val transport: RtspTransport,
        val user: String,
        val password: String,
        /** Pares (w, h) que o motor pode tentar caso o encoder recuse a resolução alvo. */
        val fallbackSizes: List<Pair<Int, Int>>,
        /** Baixa o bitrate e descarta atraso acumulado quando a subida não dá conta. */
        val adaptToNetwork: Boolean,
    )

    /** Prepara vídeo + captura de tela; devolve a resolução que foi aceita. */
    fun prepare(request: PrepareRequest): PrepareResult

    /**
     * Publica no endpoint (rtsp/rtsps/rtmp/rtmps/srt). Não bloqueia na rede, mas sobe
     * captura e encoders: devolve false (já desfeito) se algum deles falhar.
     */
    fun startStream(endpoint: String): Boolean

    /**
     * Pede à biblioteca que reconecte após [delayMs] mantendo encoder e captura vivos.
     * Devolve false quando a lib recusa (ex.: endpoint malformado) — aí é erro fatal.
     */
    fun retry(delayMs: Long, reason: String): Boolean

    /** Encerra a transmissão. Idempotente. */
    fun stopStream()

    /** Libera recursos (encoder, VirtualDisplay, projeção). */
    fun release()

    /** Verdadeiro enquanto o encoder está publicando. */
    fun isStreaming(): Boolean

    /** Redimensiona o VirtualDisplay quando a orientação muda. */
    fun onOrientationChanged(landscape: Boolean)

    fun setListener(listener: Listener?)

    sealed interface PrepareResult {
        data class Ok(val width: Int, val height: Int) : PrepareResult
        data class Failed(val reason: String) : PrepareResult
    }
}
