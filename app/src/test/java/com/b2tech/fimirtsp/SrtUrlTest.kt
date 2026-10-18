package com.b2tech.fimirtsp

import com.b2tech.fimirtsp.streaming.SrtUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class SrtUrlTest {

    @Test fun acrescenta_latencia_na_query_existente() {
        assertEquals(
            "srt://host:8890?streamid=publish:fimi&latency=800",
            SrtUrl.withDefaultLatency("srt://host:8890?streamid=publish:fimi", 800),
        )
    }

    @Test fun cria_query_quando_nao_ha() {
        assertEquals("srt://host:9000/live?latency=800", SrtUrl.withDefaultLatency("srt://host:9000/live", 800))
    }

    @Test fun respeita_latencia_escolhida_pelo_usuario() {
        val url = "srt://host:8890?streamid=publish:fimi&latency=2000"
        assertEquals(url, SrtUrl.withDefaultLatency(url, 800))
    }

    @Test fun nao_duplica_separador() {
        assertEquals("srt://host:8890?latency=800", SrtUrl.withDefaultLatency("srt://host:8890?", 800))
        assertEquals("srt://h:1?streamid=x&latency=800", SrtUrl.withDefaultLatency("srt://h:1?streamid=x&", 800))
    }

    @Test fun nao_mexe_em_outros_protocolos() {
        val rtmp = "rtmp://host:1935/live/abc?key=sk_1"
        assertEquals(rtmp, SrtUrl.withDefaultLatency(rtmp, 800))
        assertEquals("rtsp://h:8554/fimi", SrtUrl.withDefaultLatency("rtsp://h:8554/fimi", 800))
    }

    @Test fun usa_800ms_por_padrao() {
        assertEquals("srt://h:8890?streamid=s&latency=800", SrtUrl.withDefaultLatency("srt://h:8890?streamid=s"))
    }
}
