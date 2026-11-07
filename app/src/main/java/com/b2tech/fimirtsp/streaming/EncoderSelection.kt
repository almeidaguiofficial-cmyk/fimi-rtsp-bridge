package com.b2tech.fimirtsp.streaming

/**
 * Modo de controle de bitrate que o encoder de vídeo efetivamente usa.
 *  - CBR: o encoder escolhido aceita BITRATE_MODE_CBR e a RootEncoder o ativa.
 *  - VBR: nenhum encoder H.264 com entrada por Surface suporta CBR neste aparelho;
 *    a lib cai no modo padrão do encoder, normalmente variável.
 */
enum class BitrateMode { CBR, VBR }

data class EncoderCandidate(
    val name: String,
    val hardware: Boolean,
    val supportsCbr: Boolean,
    val supportsSurface: Boolean,
)

data class EncoderChoice(
    val name: String,
    val hardware: Boolean,
    val mode: BitrateMode,
)

/**
 * Espelha a escolha da RootEncoder 2.8.1 com CodecType.CBR_PRIORITY
 * (VideoEncoder.chooseEncoder + CodecUtil.getAllEncodersCbrPriority):
 * hardware com CBR > software com CBR > hardware > software, e vence o primeiro
 * que aceita entrada por Surface — a captura de tela só entrega frames assim.
 *
 * O padrão da lib (FIRST_COMPATIBLE_FOUND) põe hardware sem CBR na frente de
 * software com CBR; por isso o motor força CBR_PRIORITY.
 */
object EncoderSelection {

    fun pick(candidates: List<EncoderCandidate>): EncoderChoice? {
        val chosen = candidates
            .sortedBy(::rank)
            .firstOrNull { it.supportsSurface }
            ?: return null
        return EncoderChoice(
            name = chosen.name,
            hardware = chosen.hardware,
            mode = if (chosen.supportsCbr) BitrateMode.CBR else BitrateMode.VBR,
        )
    }

    // sortedBy é estável: dentro da mesma classe vale a ordem do MediaCodecList.
    private fun rank(c: EncoderCandidate): Int = when {
        c.hardware && c.supportsCbr -> 0
        c.supportsCbr -> 1
        c.hardware -> 2
        else -> 3
    }
}
