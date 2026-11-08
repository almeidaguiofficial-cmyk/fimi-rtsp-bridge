package com.b2tech.fimirtsp.streaming

import com.b2tech.fimirtsp.settings.VideoCodecPref

/**
 * Qual codec tentar e qual vai sair. H.265 sem encoder no aparelho cai para H.264 em vez
 * de falhar a transmissão; H.264 nunca sobe para H.265 sozinho, porque o servidor pode
 * não aceitar.
 */
object CodecPlan {

    /** Ordem em que o motor tenta preparar o encoder. */
    fun attemptOrder(requested: VideoCodecPref): List<VideoCodecPref> = when (requested) {
        VideoCodecPref.H265 -> listOf(VideoCodecPref.H265, VideoCodecPref.H264)
        VideoCodecPref.H264 -> listOf(VideoCodecPref.H264)
    }

    /**
     * H.265 só em hardware. O HEVC de software do Android anuncia CBR e passaria na frente
     * do hardware sem CBR, mas não aguenta 1080p30 (e em várias versões aceita no máximo
     * 512×512). Com hardware-only o motor usa CodecType.HARDWARE: hardware CBR > hardware.
     */
    fun hardwareOnly(codec: VideoCodecPref): Boolean = codec == VideoCodecPref.H265

    /** Encoders que o motor pode escolher para o codec (espelha o CodecType usado no motor). */
    fun eligible(codec: VideoCodecPref, candidates: List<EncoderCandidate>): List<EncoderCandidate> =
        if (hardwareOnly(codec)) candidates.filter { it.hardware } else candidates

    /** Codec que a tela anuncia antes de transmitir, a partir da sondagem dos encoders. */
    fun expected(requested: VideoCodecPref, hevcAvailable: Boolean): VideoCodecPref =
        if (requested == VideoCodecPref.H265 && !hevcAvailable) VideoCodecPref.H264 else requested
}
