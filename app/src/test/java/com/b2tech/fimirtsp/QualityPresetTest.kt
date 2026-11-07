package com.b2tech.fimirtsp

import com.b2tech.fimirtsp.settings.QualityPreset
import com.b2tech.fimirtsp.settings.RtspTransport
import com.b2tech.fimirtsp.settings.StreamSettings
import com.b2tech.fimirtsp.settings.VideoResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityPresetTest {

    private val base = StreamSettings(
        serverUrl = "rtmp://servidor:1935/live/abc?key=sk_1",
        rtspUser = "u",
        rtspPassword = "p",
        resolution = VideoResolution.R720P,
        fps = 24,
        bitrateBps = 3_000_000,
        transport = RtspTransport.UDP,
        stopOnTaskRemoved = false,
    )

    @Test fun aplica_1080p_30fps_8mbps() {
        val s = QualityPreset.applyTo(base)
        assertEquals(VideoResolution.R1080P, s.resolution)
        assertEquals(30, s.fps)
        assertEquals(8_000_000, s.bitrateBps)
    }

    @Test fun nao_mexe_em_url_credenciais_nem_transporte() {
        val s = QualityPreset.applyTo(base)
        assertEquals(base.serverUrl, s.serverUrl)
        assertEquals(base.rtspUser, s.rtspUser)
        assertEquals(base.rtspPassword, s.rtspPassword)
        assertEquals(base.transport, s.transport)
        assertEquals(base.stopOnTaskRemoved, s.stopOnTaskRemoved)
    }

    @Test fun fica_ativo_so_com_os_tres_valores() {
        val max = QualityPreset.applyTo(base)
        assertTrue(QualityPreset.isActive(max))
        assertFalse(QualityPreset.isActive(base))
        assertFalse(QualityPreset.isActive(max.copy(fps = 24)))
        assertFalse(QualityPreset.isActive(max.copy(bitrateBps = 7_500_000)))
        assertFalse(QualityPreset.isActive(max.copy(resolution = VideoResolution.R720P)))
    }

    @Test fun bitrate_do_preset_dentro_dos_limites() {
        assertTrue(
            QualityPreset.BITRATE_BPS in StreamSettings.MIN_BITRATE_BPS..StreamSettings.MAX_BITRATE_BPS,
        )
    }
}
