package com.b2tech.fimirtsp

import com.b2tech.fimirtsp.ui.FimiLauncher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cobre a lógica pura de detecção do app FIMI (sem tocar em PackageManager).
 * Garante que o app do X8 Tele Max (FIMI Navi 3.0 = com.fimi.app.x8p) é reconhecido.
 */
class FimiLauncherTest {

    @Test
    fun teleMaxPackageIsKnownAndHasPriority() {
        // O X8 Tele Max usa o FIMI Navi 3.0. Deve estar na lista e ser o primeiro (prioridade).
        assertTrue(
            "com.fimi.app.x8p deve estar entre os pacotes FIMI conhecidos",
            FimiLauncher.KNOWN_FIMI_PACKAGES.contains("com.fimi.app.x8p"),
        )
        assertEquals("com.fimi.app.x8p", FimiLauncher.KNOWN_FIMI_PACKAGES.first())
    }

    @Test
    fun matchesFimiLabelAcceptsNavi3AndVariants() {
        assertTrue(FimiLauncher.matchesFimiLabel("Fimi Navi 3.0"))
        assertTrue(FimiLauncher.matchesFimiLabel("FIMI Navi"))
        assertTrue(FimiLauncher.matchesFimiLabel("fimi navi 3"))
    }

    @Test
    fun matchesFimiLabelRejectsUnrelatedApps() {
        assertFalse(FimiLauncher.matchesFimiLabel("WhatsApp"))
        assertFalse(FimiLauncher.matchesFimiLabel("DJI Fly"))
        assertFalse(FimiLauncher.matchesFimiLabel(""))
    }

    @Test
    fun fallbackLabelNamesNavi3ForTeleMaxPackage() {
        assertEquals("FIMI Navi 3.0", FimiLauncher.fallbackLabel("com.fimi.app.x8p"))
    }
}
