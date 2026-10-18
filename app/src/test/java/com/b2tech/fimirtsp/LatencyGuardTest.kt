package com.b2tech.fimirtsp

import com.b2tech.fimirtsp.streaming.LatencyGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LatencyGuardTest {

    @Test fun nao_descarta_abaixo_do_limite() {
        val guard = LatencyGuard(maxQueueSeconds = 2f, cooldownMs = 5_000)
        assertFalse(guard.shouldSkip(0f, 0))
        assertFalse(guard.shouldSkip(1.99f, 1_000))
        assertEquals(0, guard.skips)
    }

    @Test fun descarta_ao_atingir_o_limite() {
        val guard = LatencyGuard(maxQueueSeconds = 2f, cooldownMs = 5_000)
        assertTrue(guard.shouldSkip(2f, 10_000))
        assertEquals(1, guard.skips)
    }

    @Test fun respeita_cooldown_entre_descartes() {
        val guard = LatencyGuard(maxQueueSeconds = 2f, cooldownMs = 5_000)
        assertTrue(guard.shouldSkip(3f, 10_000))
        assertFalse(guard.shouldSkip(3f, 11_000))
        assertFalse(guard.shouldSkip(3f, 14_999))
        assertTrue(guard.shouldSkip(3f, 15_000))
        assertEquals(2, guard.skips)
    }

    @Test fun primeiro_descarte_nao_depende_do_relogio() {
        // elapsedRealtime pode estar perto de zero logo após o boot.
        val guard = LatencyGuard(maxQueueSeconds = 2f, cooldownMs = 5_000)
        assertTrue(guard.shouldSkip(2.5f, 100))
    }
}
