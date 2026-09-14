package com.b2tech.fimirtsp.player

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.rtmp.RtmpDataSource
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource

/**
 * Escolhe o MediaSource pelo esquema da URL (já validada por
 * StreamUrlValidator.validateForPlayback antes de chegar aqui).
 */
object PlayerMediaSources {

    @OptIn(UnstableApi::class)
    fun create(url: String): MediaSource = when (url.substringBefore("://").lowercase()) {
        // RTMP entrega FLV; o DefaultExtractorsFactory do ProgressiveMediaSource sniffa.
        "rtmp" -> ProgressiveMediaSource.Factory(RtmpDataSource.Factory())
            .createMediaSource(MediaItem.fromUri(url))

        // TCP interleaved: atravessa NAT/Wi-Fi com perda melhor que RTP/UDP.
        "rtsp" -> RtspMediaSource.Factory()
            .setForceUseRtpTcp(true)
            .setTimeoutMs(10_000)
            .createMediaSource(MediaItem.fromUri(url))

        // Sem a URL na mensagem: ela pode carregar credencial (?pass=...).
        else -> error("Esquema não suportado pelo reprodutor")
    }

    /**
     * O RtmpDataSource carrega librtmp-jni no primeiro uso; o .so (de 2021) não é
     * alinhado a páginas de 16 KB e pode falhar o load em kernels novos. Checamos
     * aqui pra dar um erro amigável em vez de derrubar o thread do loader.
     */
    fun rtmpNativeLibAvailable(): Boolean = try {
        Class.forName("net.butterflytv.rtmp_client.RtmpClient")
        true
    } catch (t: Throwable) {
        false
    }
}
