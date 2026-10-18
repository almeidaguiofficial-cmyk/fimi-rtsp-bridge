package com.b2tech.fimirtsp.streaming

/** Qualidade da subida, lida pela fila de envio (não por velocímetro de operadora). */
enum class LinkQuality { UNKNOWN, GOOD, UNSTABLE, BAD }

/** Retrato por segundo da saída de rede, publicado para a UI e a notificação. */
data class NetworkStats(
    /** Bitrate pedido ao encoder agora (já adaptado, se o modo estiver ligado). */
    val targetBitrateBps: Int,
    /** Teto escolhido pelo usuário. */
    val maxBitrateBps: Int,
    /** Segundos de vídeo parados na fila de envio — é o atraso que o espectador sente a mais. */
    val queueSeconds: Float,
    val quality: LinkQuality,
    /** Quantas vezes a fila foi descartada para voltar ao ao vivo nesta sessão. */
    val liveSkips: Int,
    val adaptive: Boolean,
)

/**
 * Quando a subida não dá conta, a fila da RootEncoder (400 frames ≈ 13 s a 30 fps)
 * vai enchendo: o vídeo chega cada vez mais atrasado e, com a fila cheia, a lib passa
 * a jogar fora frames soltos no meio do GOP — é o "travou" que o espectador vê.
 *
 * O guarda manda descartar a fila de uma vez quando ela passa de [maxQueueSeconds]
 * (o motor pede um keyframe logo em seguida): perde-se um trecho, mas a imagem volta
 * ao ao vivo em vez de virar um replay cada vez mais velho. [cooldownMs] dá tempo ao
 * bitrate adaptativo de reagir antes de um novo descarte.
 */
class LatencyGuard(
    private val maxQueueSeconds: Float = DEFAULT_MAX_QUEUE_SECONDS,
    private val cooldownMs: Long = DEFAULT_COOLDOWN_MS,
) {
    var skips: Int = 0
        private set
    private var lastSkipAtMs: Long? = null

    fun shouldSkip(queueSeconds: Float, nowMs: Long): Boolean {
        if (queueSeconds < maxQueueSeconds) return false
        val last = lastSkipAtMs
        if (last != null && nowMs - last < cooldownMs) return false
        lastSkipAtMs = nowMs
        skips++
        return true
    }

    companion object {
        const val DEFAULT_MAX_QUEUE_SECONDS = 2f
        const val DEFAULT_COOLDOWN_MS = 5_000L
    }
}

object LinkQualityClassifier {
    private const val BAD_QUEUE_SECONDS = 1f
    private const val UNSTABLE_QUEUE_SECONDS = 0.3f
    // Fração do teto em que o bitrate adaptado foi parar.
    private const val BAD_RATIO = 0.35f
    private const val UNSTABLE_RATIO = 0.8f

    fun classify(
        hasMeasurement: Boolean,
        insufficient: Boolean,
        queueSeconds: Float,
        targetBitrateBps: Int,
        maxBitrateBps: Int,
    ): LinkQuality {
        if (!hasMeasurement) return LinkQuality.UNKNOWN
        val ratio = if (maxBitrateBps > 0) targetBitrateBps.toFloat() / maxBitrateBps else 1f
        return when {
            queueSeconds >= BAD_QUEUE_SECONDS || ratio <= BAD_RATIO -> LinkQuality.BAD
            insufficient || queueSeconds >= UNSTABLE_QUEUE_SECONDS || ratio < UNSTABLE_RATIO ->
                LinkQuality.UNSTABLE
            else -> LinkQuality.GOOD
        }
    }
}
