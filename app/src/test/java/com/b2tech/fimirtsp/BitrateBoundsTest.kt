package com.b2tech.fimirtsp

import com.b2tech.fimirtsp.settings.StreamSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BitrateBoundsTest {

    @Test fun clamp_bitrate_no_minimo() {
        val clamped = 1000.coerceIn(StreamSettings.MIN_BITRATE_BPS, StreamSettings.MAX_BITRATE_BPS)
        assertEquals(StreamSettings.MIN_BITRATE_BPS, clamped)
    }

    @Test fun clamp_bitrate_no_maximo() {
        val clamped = 99_000_000.coerceIn(StreamSettings.MIN_BITRATE_BPS, StreamSettings.MAX_BITRATE_BPS)
        assertEquals(StreamSettings.MAX_BITRATE_BPS, clamped)
    }

    @Test fun bitrate_valido_permanece() {
        val v = 3_000_000
        assertEquals(v, v.coerceIn(StreamSettings.MIN_BITRATE_BPS, StreamSettings.MAX_BITRATE_BPS))
    }

    @Test fun default_dentro_dos_limites() {
        assertTrue(StreamSettings.DEFAULT_BITRATE_BPS in StreamSettings.MIN_BITRATE_BPS..StreamSettings.MAX_BITRATE_BPS)
    }
}
