package com.b2tech.fimirtsp.streaming

/**
 * Backoff exponencial saturado: 1, 2, 4, 8, 15, 15, 15, ...
 * Devolve o próximo delay em milissegundos para a tentativa 1..N.
 */
object Backoff {
    private val STEPS_SECONDS = intArrayOf(1, 2, 4, 8)
    private const val CAP_SECONDS = 15

    fun delayMs(attempt: Int): Long {
        require(attempt >= 1) { "attempt deve ser >= 1" }
        val secs = if (attempt <= STEPS_SECONDS.size) STEPS_SECONDS[attempt - 1] else CAP_SECONDS
        return secs * 1000L
    }
}
