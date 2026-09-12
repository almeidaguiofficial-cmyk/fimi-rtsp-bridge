package com.b2tech.fimirtsp

import com.b2tech.fimirtsp.validation.RtspUrlValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RtspUrlValidatorTest {

    @Test fun aceita_rtsp_com_caminho_e_porta() {
        val r = RtspUrlValidator.validate("rtsp://192.168.1.100:8554/fimi")
        assertTrue(r.ok)
        assertEquals("rtsp", r.scheme)
    }

    @Test fun aceita_rtsps() {
        assertTrue(RtspUrlValidator.isValid("rtsps://media.exemplo.com/fimi"))
    }

    @Test fun aceita_userinfo() {
        assertTrue(RtspUrlValidator.isValid("rtsp://claude:s3nha@media.exemplo.com:8554/fimi"))
    }

    @Test fun aceita_ipv6() {
        assertTrue(RtspUrlValidator.isValid("rtsp://[2001:db8::1]:8554/fimi"))
    }

    @Test fun rejeita_esquema_desconhecido() {
        assertFalse(RtspUrlValidator.isValid("http://x/y"))
        assertFalse(RtspUrlValidator.isValid("rtmp://x/y"))
    }

    @Test fun rejeita_vazio_ou_branco() {
        assertFalse(RtspUrlValidator.isValid(null))
        assertFalse(RtspUrlValidator.isValid(""))
        assertFalse(RtspUrlValidator.isValid("   "))
    }

    @Test fun rejeita_sem_caminho() {
        assertFalse(RtspUrlValidator.isValid("rtsp://192.168.1.100:8554"))
        assertFalse(RtspUrlValidator.isValid("rtsp://192.168.1.100:8554/"))
    }

    @Test fun rejeita_porta_invalida() {
        assertFalse(RtspUrlValidator.isValid("rtsp://host:0/fimi"))
        assertFalse(RtspUrlValidator.isValid("rtsp://host:99999/fimi"))
        assertFalse(RtspUrlValidator.isValid("rtsp://host:abc/fimi"))
    }

    @Test fun rejeita_host_vazio() {
        assertFalse(RtspUrlValidator.isValid("rtsp:///fimi"))
    }

    @Test fun aceita_host_sem_porta() {
        assertTrue(RtspUrlValidator.isValid("rtsp://mediamtx.local/fimi"))
    }
}
