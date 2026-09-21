package com.b2tech.fimirtsp

import com.b2tech.fimirtsp.audio.VolumeMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeMathTest {

    @Test fun passo_nunca_e_zero_em_aparelho_com_escala_curta() {
        // max 7 (comum em tablets): 10% daria 0 no arredondamento e o botão não faria nada.
        assertEquals(1, VolumeMath.stepIndexes(maxIndex = 7, stepPercent = 10))
        assertEquals(1, VolumeMath.stepIndexes(maxIndex = 15, stepPercent = 5))
    }

    @Test fun passo_acompanha_escalas_longas() {
        assertEquals(10, VolumeMath.stepIndexes(maxIndex = 100, stepPercent = 10))
        assertEquals(3, VolumeMath.stepIndexes(maxIndex = 15, stepPercent = 25))
    }

    @Test fun passo_fora_da_faixa_e_limitado() {
        assertEquals(
            VolumeMath.stepIndexes(100, VolumeMath.MAX_STEP_PERCENT),
            VolumeMath.stepIndexes(100, 90),
        )
        assertEquals(
            VolumeMath.stepIndexes(100, VolumeMath.MIN_STEP_PERCENT),
            VolumeMath.stepIndexes(100, 0),
        )
    }

    @Test fun sem_controle_de_volume_nada_explode() {
        assertEquals(0, VolumeMath.stepIndexes(maxIndex = 0, stepPercent = 10))
        assertEquals(0, VolumeMath.raised(currentIndex = 0, maxIndex = 0, stepPercent = 10))
        assertEquals(0, VolumeMath.lowered(currentIndex = 0, maxIndex = 0, stepPercent = 10))
        assertEquals(0, VolumeMath.percent(index = 3, maxIndex = 0))
        assertEquals(0, VolumeMath.indexForPercent(percent = 50, maxIndex = 0))
    }

    @Test fun subir_respeita_o_teto() {
        assertEquals(15, VolumeMath.raised(currentIndex = 14, maxIndex = 15, stepPercent = 25))
        assertEquals(15, VolumeMath.raised(currentIndex = 15, maxIndex = 15, stepPercent = 10))
    }

    @Test fun descer_respeita_o_piso() {
        assertEquals(0, VolumeMath.lowered(currentIndex = 1, maxIndex = 15, stepPercent = 25))
        assertEquals(0, VolumeMath.lowered(currentIndex = 0, maxIndex = 15, stepPercent = 10))
    }

    @Test fun subir_um_passo_muda_mesmo_o_indice() {
        val antes = 5
        val depois = VolumeMath.raised(antes, maxIndex = 15, stepPercent = 10)
        assertTrue("o comando precisa mover pelo menos 1 índice", depois > antes)
    }

    @Test fun porcentagem_nas_pontas() {
        assertEquals(0, VolumeMath.percent(index = 0, maxIndex = 15))
        assertEquals(100, VolumeMath.percent(index = 15, maxIndex = 15))
        assertEquals(100, VolumeMath.percent(index = 99, maxIndex = 15))
    }

    @Test fun porcentagem_arredonda_para_o_mais_proximo() {
        // 8/15 = 53,3%
        assertEquals(53, VolumeMath.percent(index = 8, maxIndex = 15))
        // 1/7 = 14,28%
        assertEquals(14, VolumeMath.percent(index = 1, maxIndex = 7))
    }

    @Test fun ida_e_volta_percentual_nao_desloca() {
        val max = 15
        for (index in 0..max) {
            val voltou = VolumeMath.indexForPercent(VolumeMath.percent(index, max), max)
            assertEquals("índice $index não sobreviveu ao round-trip", index, voltou)
        }
    }

    @Test fun percentual_fora_da_faixa_e_limitado() {
        assertEquals(0, VolumeMath.indexForPercent(percent = -20, maxIndex = 15))
        assertEquals(15, VolumeMath.indexForPercent(percent = 300, maxIndex = 15))
    }
}
