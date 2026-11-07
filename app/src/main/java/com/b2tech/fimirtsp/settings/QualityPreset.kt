package com.b2tech.fimirtsp.settings

/**
 * Modo "qualidade máxima": 1920×1080, 30 fps, 8 Mbps. O bitrate constante (CBR)
 * não é configuração: o motor sempre escolhe o encoder com CBR_PRIORITY.
 */
object QualityPreset {
    val RESOLUTION = VideoResolution.R1080P
    const val FPS = 30
    const val BITRATE_BPS = 8_000_000

    /** Troca só codificação; URL, credenciais e transporte ficam como estão. */
    fun applyTo(settings: StreamSettings): StreamSettings =
        settings.copy(resolution = RESOLUTION, fps = FPS, bitrateBps = BITRATE_BPS)

    fun isActive(settings: StreamSettings): Boolean =
        settings.resolution == RESOLUTION && settings.fps == FPS && settings.bitrateBps == BITRATE_BPS
}
