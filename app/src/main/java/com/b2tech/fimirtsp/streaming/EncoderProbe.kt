package com.b2tech.fimirtsp.streaming

import android.media.MediaCodecInfo
import com.pedro.encoder.utils.CodecUtil

/**
 * Lê os encoders H.264 do aparelho com as mesmas funções da RootEncoder que o
 * VideoEncoder usa na escolha e aplica [EncoderSelection]. Serve só para mostrar
 * na tela qual encoder e qual modo de bitrate a transmissão vai usar; o motor não
 * depende deste resultado.
 */
object EncoderProbe {

    private const val MIME_AVC = "video/avc"

    fun probe(): EncoderChoice? = try {
        val hardware = CodecUtil.getAllHardwareEncoders(MIME_AVC).map { candidate(it, hardware = true) }
        val software = CodecUtil.getAllSoftwareEncoders(MIME_AVC).map { candidate(it, hardware = false) }
        EncoderSelection.pick(hardware + software)
    } catch (_: Throwable) {
        null
    }

    private fun candidate(info: MediaCodecInfo, hardware: Boolean): EncoderCandidate {
        val colors: IntArray? = CodecUtil.getCapabilities(info, MIME_AVC)?.colorFormats
        return EncoderCandidate(
            name = info.name,
            hardware = hardware,
            supportsCbr = CodecUtil.isCBRModeSupported(info, MIME_AVC),
            supportsSurface = colors?.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface) == true,
        )
    }
}
