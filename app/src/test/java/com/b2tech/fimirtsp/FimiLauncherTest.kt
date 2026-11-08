package com.b2tech.fimirtsp

import com.b2tech.fimirtsp.ui.FimiLauncher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FimiLauncherTest {

    @Test fun reconhece_pacotes_conhecidos_de_todos_os_modelos() {
        // X8 Pro/Tele/Tele Max, SE, SE novo, Mini, linha antiga.
        listOf(
            "com.fimi.app.x8p",
            "com.fimi.app.x8s",
            "com.fimi.x8s23",
            "com.fimi.mini",
            "com.fimi.soul",
        ).forEach { pkg ->
            assertTrue(pkg, FimiLauncher.matches(pkg, label = ""))
        }
    }

    @Test fun reconhece_qualquer_pacote_com_fimi_mesmo_desconhecido() {
        // Modelo futuro cujo pacote ainda não listamos: o prefixo basta.
        assertTrue(FimiLauncher.matches("com.fimi.navi.x9", label = "Qualquer Nome"))
    }

    @Test fun reconhece_pelo_nome_visivel_quando_o_pacote_nao_e_com_fimi() {
        // Alguns rebrands/variações não usam o pacote com.fimi.
        assertTrue(FimiLauncher.matches("com.example.store", label = "FIMI Navi mini"))
        assertTrue(FimiLauncher.matches("br.loja.app", label = "FIMI Navi SE"))
    }

    @Test fun nao_reconhece_apps_sem_relacao() {
        assertFalse(FimiLauncher.matches("com.google.android.youtube", label = "YouTube"))
        assertFalse(FimiLauncher.matches("com.android.settings", label = "Configurações"))
    }

    @Test fun prioridade_pacote_conhecido_antes_de_prefixo_antes_de_nome() {
        assertTrue(
            FimiLauncher.rank("com.fimi.app.x8p", "FIMI Navi") <
                FimiLauncher.rank("com.fimi.outro", "Outro"),
        )
        assertTrue(
            FimiLauncher.rank("com.fimi.outro", "Outro") <
                FimiLauncher.rank("com.terceiro.app", "FIMI Navi mini"),
        )
    }

    @Test fun sem_relacao_recebe_rank_maximo() {
        assertEquals(Int.MAX_VALUE, FimiLauncher.rank("com.google.android.youtube", "YouTube"))
    }
}
