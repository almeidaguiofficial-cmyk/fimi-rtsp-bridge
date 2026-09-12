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
    )

    /** Prepara vídeo + captura de tela; devolve a resolução que foi aceita. */
    fun prepare(request: PrepareRequest): PrepareResult

    /** Publica no endpoint RTSP. Não bloqueia. */
    fun startStream(endpoint: String)

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
