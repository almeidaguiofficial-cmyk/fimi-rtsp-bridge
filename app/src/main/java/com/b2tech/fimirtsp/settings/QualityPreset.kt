package com.b2tech.fimirtsp.settings

/**
 * Modo "qualidade máxima": 1920×1080, 30 fps, 8 Mbps em H.265. O bitrate constante
 * (CBR) não é configuração: o motor sempre escolhe o encoder com CBR_PRIORITY. Sem
 * encoder H.265 no aparelho, o motor cai para H.264 sozinho (CodecPlan).
 */
object QualityPreset {
    val RESOLUTION = VideoResolution.R1080P
    const val FPS = 30
    const val BITRATE_BPS = 8_000_000
    val CODEC = VideoCodecPref.H265

    /** Troca só codificação; URL, credenciais e transporte ficam como estão. */
    fun applyTo(settings: StreamSettings): StreamSettings =
        settings.copy(resolution = RESOLUTION, fps = FPS, bitrateBps = BITRATE_BPS, videoCodec = CODEC)

    fun isActive(settings: StreamSettings): Boolean =
        settings.resolution == RESOLUTION && settings.fps == FPS &&
            settings.bitrateBps == BITRATE_BPS && settings.videoCodec == CODEC
}
