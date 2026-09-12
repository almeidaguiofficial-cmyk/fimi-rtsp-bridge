package com.b2tech.fimirtsp

import com.b2tech.fimirtsp.validation.StreamUrlValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamUrlValidatorTest {

    @Test fun aceita_rtsp_com_caminho_e_porta() {
        val r = StreamUrlValidator.validate("rtsp://192.168.1.100:8554/fimi")
        assertTrue(r.ok)
        assertEquals("rtsp", r.scheme)
        assertEquals(StreamUrlValidator.Family.RTSP, r.family)
    }

    @Test fun aceita_rtsps() {
        assertTrue(StreamUrlValidator.isValid("rtsps://media.exemplo.com/fimi"))
    }

    @Test fun aceita_rtmp_com_app_e_chave() {
        val r = StreamUrlValidator.validate("rtmp://media.exemplo.com:1935/live/1b2c3d4e-0000-4aaa-8bbb-c0ffee123456")
        assertTrue(r.ok)
        assertEquals("rtmp", r.scheme)
        assertEquals(StreamUrlValidator.Family.RTMP, r.family)
    }

    @Test fun aceita_rtmp_com_query_key() {
        assertTrue(StreamUrlValidator.isValid("rtmp://host:1935/live/abc?key=sk_123"))
    }

    @Test fun aceita_rtmps_porta_443() {
        val r = StreamUrlValidator.validate("rtmps://rtmps.exemplo.com:443/live/abc?key=sk_1")
        assertTrue(r.ok)
        assertEquals(StreamUrlValidator.Family.RTMP, r.family)
    }

    @Test fun aceita_userinfo() {
        assertTrue(StreamUrlValidator.isValid("rtsp://claude:s3nha@media.exemplo.com:8554/fimi"))
    }

    @Test fun aceita_ipv6() {
        assertTrue(StreamUrlValidator.isValid("rtsp://[2001:db8::1]:8554/fimi"))
    }

    @Test fun rejeita_esquema_desconhecido() {
        assertFalse(StreamUrlValidator.isValid("http://x/y"))
        assertFalse(StreamUrlValidator.isValid("srt://x:9000/y"))
        assertFalse(StreamUrlValidator.isValid("x/y"))
    }

    @Test fun rejeita_vazio_ou_branco() {
        assertFalse(StreamUrlValidator.isValid(null))
        assertFalse(StreamUrlValidator.isValid(""))
        assertFalse(StreamUrlValidator.isValid("   "))
    }

    @Test fun rejeita_sem_caminho() {
        assertFalse(StreamUrlValidator.isValid("rtsp://192.168.1.100:8554"))
        assertFalse(StreamUrlValidator.isValid("rtsp://192.168.1.100:8554/"))
        assertFalse(StreamUrlValidator.isValid("rtmp://host:1935/?key=x"))
    }

    @Test fun rejeita_porta_invalida() {
        assertFalse(StreamUrlValidator.isValid("rtsp://host:0/fimi"))
        assertFalse(StreamUrlValidator.isValid("rtsp://host:99999/fimi"))
        assertFalse(StreamUrlValidator.isValid("rtsp://host:abc/fimi"))
    }

    @Test fun rejeita_host_vazio() {
        assertFalse(StreamUrlValidator.isValid("rtsp:///fimi"))
    }

    @Test fun aceita_host_sem_porta() {
        assertTrue(StreamUrlValidator.isValid("rtsp://mediamtx.local/fimi"))
        assertTrue(StreamUrlValidator.isValid("rtmp://a.b.c/live/x"))
    }

    @Test fun family_nula_quando_invalido() {
        assertEquals(null, StreamUrlValidator.validate("http://x/y").family)
    }
}
