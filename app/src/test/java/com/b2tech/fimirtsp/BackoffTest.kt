package com.b2tech.fimirtsp

import com.b2tech.fimirtsp.streaming.Backoff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackoffTest {

    @Test fun sequencia_esperada() {
        assertEquals(1_000L, Backoff.delayMs(1))
        assertEquals(2_000L, Backoff.delayMs(2))
        assertEquals(4_000L, Backoff.delayMs(3))
        assertEquals(8_000L, Backoff.delayMs(4))
        assertEquals(15_000L, Backoff.delayMs(5))
        assertEquals(15_000L, Backoff.delayMs(6))
        assertEquals(15_000L, Backoff.delayMs(999))
    }

    @Test fun rejeita_tentativa_zero_ou_negativa() {
        assertThrows(IllegalArgumentException::class.java) { Backoff.delayMs(0) }
        assertThrows(IllegalArgumentException::class.java) { Backoff.delayMs(-1) }
    }
}
