package com.b2tech.fimirtsp.streaming

import android.media.MediaCodecInfo
import com.b2tech.fimirtsp.settings.VideoCodecPref
import com.pedro.encoder.utils.CodecUtil

/**
 * Lê os encoders do codec pedido com as mesmas funções da RootEncoder que o
 * VideoEncoder usa na escolha e aplica [EncoderSelection]. Serve só para mostrar
 * na tela qual encoder e qual modo de bitrate a transmissão vai usar; o motor não
 * depende deste resultado.
 */
object EncoderProbe {

    /** null = o aparelho não tem encoder desse codec que aceite captura de tela. */
    fun probe(codec: VideoCodecPref): EncoderChoice? = try {
        val mime = codec.mime
        val hardware = CodecUtil.getAllHardwareEncoders(mime).map { candidate(it, mime, hardware = true) }
        val software = CodecUtil.getAllSoftwareEncoders(mime).map { candidate(it, mime, hardware = false) }
        EncoderSelection.pick(CodecPlan.eligible(codec, hardware + software))
    } catch (_: Throwable) {
        null
    }

    private fun candidate(info: MediaCodecInfo, mime: String, hardware: Boolean): EncoderCandidate {
        val colors: IntArray? = CodecUtil.getCapabilities(info, mime)?.colorFormats
        return EncoderCandidate(
            name = info.name,
            hardware = hardware,
            supportsCbr = CodecUtil.isCBRModeSupported(info, mime),
            supportsSurface = colors?.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface) == true,
        )
    }
}
