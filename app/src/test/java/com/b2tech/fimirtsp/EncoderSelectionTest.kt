package com.b2tech.fimirtsp

import com.b2tech.fimirtsp.streaming.BitrateMode
import com.b2tech.fimirtsp.streaming.EncoderCandidate
import com.b2tech.fimirtsp.streaming.EncoderChoice
import com.b2tech.fimirtsp.streaming.EncoderSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EncoderSelectionTest {

    private fun enc(name: String, hw: Boolean, cbr: Boolean, surface: Boolean = true) =
        EncoderCandidate(name, hardware = hw, supportsCbr = cbr, supportsSurface = surface)

    @Test fun hardware_com_cbr_vence_tudo() {
        val choice = EncoderSelection.pick(
            listOf(
                enc("sw.cbr", hw = false, cbr = true),
                enc("hw.vbr", hw = true, cbr = false),
                enc("hw.cbr", hw = true, cbr = true),
            )
        )
        assertEquals(EncoderChoice("hw.cbr", hardware = true, mode = BitrateMode.CBR), choice)
    }

    @Test fun software_com_cbr_vence_hardware_sem_cbr() {
        // Exatamente o caso que o padrão da lib (FIRST_COMPATIBLE_FOUND) resolvia ao contrário.
        val choice = EncoderSelection.pick(
            listOf(
                enc("hw.vbr", hw = true, cbr = false),
                enc("sw.cbr", hw = false, cbr = true),
            )
        )
        assertEquals(EncoderChoice("sw.cbr", hardware = false, mode = BitrateMode.CBR), choice)
    }

    @Test fun sem_cbr_nenhum_hardware_vence_software_e_modo_e_vbr() {
        val choice = EncoderSelection.pick(
            listOf(
                enc("sw.vbr", hw = false, cbr = false),
                enc("hw.vbr", hw = true, cbr = false),
            )
        )
        assertEquals(EncoderChoice("hw.vbr", hardware = true, mode = BitrateMode.VBR), choice)
    }

    @Test fun encoder_sem_entrada_surface_e_ignorado() {
        val choice = EncoderSelection.pick(
            listOf(
                enc("hw.cbr.buffer", hw = true, cbr = true, surface = false),
                enc("hw.vbr", hw = true, cbr = false),
            )
        )
        assertEquals("hw.vbr", choice?.name)
        assertEquals(BitrateMode.VBR, choice?.mode)
    }

    @Test fun mesma_classe_respeita_ordem_do_aparelho() {
        val choice = EncoderSelection.pick(
            listOf(
                enc("c2.qti.avc.encoder", hw = true, cbr = true),
                enc("c2.exynos.h264.encoder", hw = true, cbr = true),
            )
        )
        assertEquals("c2.qti.avc.encoder", choice?.name)
    }

    @Test fun nenhum_candidato_utilizavel_devolve_nulo() {
        assertNull(EncoderSelection.pick(emptyList()))
        assertNull(EncoderSelection.pick(listOf(enc("x", hw = true, cbr = true, surface = false))))
    }
}
