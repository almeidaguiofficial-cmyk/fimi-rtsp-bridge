package com.b2tech.fimirtsp.audio

/**
 * Aritmética do comando de volume, sem depender do Android — assim dá para testar na JVM.
 *
 * O Android não expõe volume em porcentagem: expõe um índice inteiro 0..max, e esse max
 * muda por aparelho e por saída de áudio (7, 15, 25, 100...). Tudo aqui trabalha em
 * porcentagem e converte para índice só na hora de aplicar.
 */
object VolumeMath {

    const val MIN_STEP_PERCENT = 5
    const val MAX_STEP_PERCENT = 25
    const val DEFAULT_STEP_PERCENT = 10

    /**
     * Passo em índices. Nunca devolve zero quando há controle: 10% de um max 7 daria 0
     * no arredondamento e o botão viraria enfeite.
     */
    fun stepIndexes(maxIndex: Int, stepPercent: Int): Int {
        if (maxIndex <= 0) return 0
        val clamped = stepPercent.coerceIn(MIN_STEP_PERCENT, MAX_STEP_PERCENT)
        return ((maxIndex * clamped) / 100).coerceAtLeast(1)
    }

    fun raised(currentIndex: Int, maxIndex: Int, stepPercent: Int): Int =
        clampIndex(currentIndex + stepIndexes(maxIndex, stepPercent), maxIndex)

    fun lowered(currentIndex: Int, maxIndex: Int, stepPercent: Int): Int =
        clampIndex(currentIndex - stepIndexes(maxIndex, stepPercent), maxIndex)

    /** Índice → porcentagem, com arredondamento pro inteiro mais próximo. */
    fun percent(index: Int, maxIndex: Int): Int {
        if (maxIndex <= 0) return 0
        val clamped = clampIndex(index, maxIndex)
        return (clamped * 100 + maxIndex / 2) / maxIndex
    }

    /** Porcentagem → índice, com arredondamento pro inteiro mais próximo. */
    fun indexForPercent(percent: Int, maxIndex: Int): Int {
        if (maxIndex <= 0) return 0
        val p = percent.coerceIn(0, 100)
        return (p * maxIndex + 50) / 100
    }

    private fun clampIndex(index: Int, maxIndex: Int): Int =
        index.coerceIn(0, maxIndex.coerceAtLeast(0))
}
