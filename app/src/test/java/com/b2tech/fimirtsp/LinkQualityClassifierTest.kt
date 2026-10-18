package com.b2tech.fimirtsp

import com.b2tech.fimirtsp.streaming.LinkQuality
import com.b2tech.fimirtsp.streaming.LinkQualityClassifier
import org.junit.Assert.assertEquals
import org.junit.Test

class LinkQualityClassifierTest {

    private fun classify(
        hasMeasurement: Boolean = true,
        insufficient: Boolean = false,
        queueSeconds: Float = 0f,
        target: Int = 3_000_000,
        max: Int = 3_000_000,
    ) = LinkQualityClassifier.classify(hasMeasurement, insufficient, queueSeconds, target, max)

    @Test fun desconhecida_sem_medicao() {
        assertEquals(LinkQuality.UNKNOWN, classify(hasMeasurement = false, queueSeconds = 5f))
    }

    @Test fun boa_com_fila_vazia_e_bitrate_no_teto() {
        assertEquals(LinkQuality.GOOD, classify())
    }

    @Test fun instavel_quando_a_lib_aponta_vazao_insuficiente() {
        assertEquals(LinkQuality.UNSTABLE, classify(insufficient = true))
    }

    @Test fun instavel_com_fila_curta_ou_bitrate_reduzido() {
        assertEquals(LinkQuality.UNSTABLE, classify(queueSeconds = 0.5f))
        assertEquals(LinkQuality.UNSTABLE, classify(target = 2_000_000))
    }

    @Test fun ruim_com_fila_longa_ou_bitrate_no_chao() {
        assertEquals(LinkQuality.BAD, classify(queueSeconds = 1.5f))
        assertEquals(LinkQuality.BAD, classify(target = 900_000))
    }

    @Test fun teto_zero_nao_divide_por_zero() {
        assertEquals(LinkQuality.GOOD, classify(target = 0, max = 0))
    }
}
