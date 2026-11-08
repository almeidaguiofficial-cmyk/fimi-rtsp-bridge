package com.b2tech.fimirtsp

import com.b2tech.fimirtsp.util.LogSanitizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogSanitizerTest {

    @Test fun remove_userinfo_de_url_rtsp() {
        val out = LogSanitizer.sanitize("rtsp://user:sup3r@host:8554/fimi")
        assertFalse(out.contains("sup3r"))
        assertFalse(out.contains("user:"))
        assertTrue(out.contains("rtsp://***@host:8554/fimi"))
    }

    @Test fun remove_userinfo_de_rtsps() {
        val out = LogSanitizer.sanitize("rtsps://alice:secret@media.exemplo.com/fimi")
        assertFalse(out.contains("secret"))
    }

    @Test fun mascara_header_basic() {
        val out = LogSanitizer.sanitize("Authorization: Basic dXNlcjpwYXNz")
        assertFalse(out.contains("dXNlcjpwYXNz"))
        assertTrue(out.contains("Basic ***"))
    }

    @Test fun mascara_pares_chave_valor_de_senha() {
        val out = LogSanitizer.sanitize("password=hunter2 senha:abc token = xyzzy secret=\"y\"")
        listOf("hunter2", "abc", "xyzzy", "\"y\"").forEach { assertFalse(out.contains(it)) }
    }

    @Test fun mascara_stream_key_na_query_da_url_rtmp() {
        val out = LogSanitizer.sanitize(
            "Endpoint malformed rtmp://media.exemplo.com:1935/live/9ea2113a?key=sk_PgLwABC123&x=1"
        )
        assertFalse(out.contains("sk_PgLwABC123"))
        assertTrue(out.contains("/live/9ea2113a?key=***&x=1"))
    }

    @Test fun mascara_pass_na_query_da_url_rtmp_do_player() {
        // A URL de leitura RTMP do MediaMTX leva a senha na query (?user=...&pass=...).
        val out = LogSanitizer.sanitize("rtmp://192.168.1.100:1935/fimi?user=reader&pass=abc123")
        assertFalse(out.contains("abc123"))
        assertTrue(out.contains("pass=***"))
    }

    @Test fun mascara_userinfo_na_url_rtsp_do_player() {
        val out = LogSanitizer.sanitize("Falha em rtsp://reader:s3nha@192.168.1.100:8554/fimi")
        assertFalse(out.contains("s3nha"))
        assertTrue(out.contains("rtsp://***@192.168.1.100:8554/fimi"))
    }

    @Test fun devolve_vazio_pra_nulo() {
        assertEquals("", LogSanitizer.sanitize(null))
    }

    @Test fun preserva_conteudo_sem_credencial() {
        val texto = "Conexão recusada em host:8554"
        assertEquals(texto, LogSanitizer.sanitize(texto))
    }
}
