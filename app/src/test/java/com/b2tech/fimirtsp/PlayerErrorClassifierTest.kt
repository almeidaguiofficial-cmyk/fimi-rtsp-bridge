package com.b2tech.fimirtsp

import com.b2tech.fimirtsp.player.PlayerErrorClassifier
import com.b2tech.fimirtsp.player.PlayerErrorClassifier.Kind
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerErrorClassifierTest {

    @Test fun behind_live_window_reconecta_imediato() {
        assertEquals(
            Kind.RETRY_IMMEDIATE,
            PlayerErrorClassifier.classify(PlayerErrorClassifier.BEHIND_LIVE_WINDOW, 1),
        )
    }

    @Test fun erros_de_rede_sao_retry_mesmo_apos_muitas_falhas() {
        listOf(
            PlayerErrorClassifier.TIMEOUT,
            PlayerErrorClassifier.IO_UNSPECIFIED,
            PlayerErrorClassifier.IO_NETWORK_CONNECTION_FAILED,
            PlayerErrorClassifier.IO_NETWORK_CONNECTION_TIMEOUT,
            PlayerErrorClassifier.PARSING_CONTAINER_MALFORMED,
        ).forEach { code ->
            assertEquals("code=$code", Kind.RETRY, PlayerErrorClassifier.classify(code, 10))
        }
    }

    @Test fun erros_de_configuracao_sao_fatais() {
        listOf(
            PlayerErrorClassifier.IO_BAD_HTTP_STATUS,
            PlayerErrorClassifier.IO_FILE_NOT_FOUND,
            PlayerErrorClassifier.IO_NO_PERMISSION,
            PlayerErrorClassifier.IO_CLEARTEXT_NOT_PERMITTED,
            PlayerErrorClassifier.PARSING_MANIFEST_MALFORMED,
            PlayerErrorClassifier.PARSING_CONTAINER_UNSUPPORTED,
            PlayerErrorClassifier.PARSING_MANIFEST_UNSUPPORTED,
        ).forEach { code ->
            assertEquals("code=$code", Kind.FATAL, PlayerErrorClassifier.classify(code, 1))
        }
    }

    @Test fun aparelho_incapaz_e_fatal() {
        listOf(
            PlayerErrorClassifier.DECODER_INIT_FAILED,
            PlayerErrorClassifier.DECODER_QUERY_FAILED,
            PlayerErrorClassifier.DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            PlayerErrorClassifier.DECODING_FORMAT_UNSUPPORTED,
        ).forEach { code ->
            assertEquals("code=$code", Kind.FATAL, PlayerErrorClassifier.classify(code, 1))
        }
    }

    @Test fun decoding_failed_tenta_e_depois_desiste() {
        assertEquals(Kind.RETRY, PlayerErrorClassifier.classify(PlayerErrorClassifier.DECODING_FAILED, 1))
        assertEquals(Kind.RETRY, PlayerErrorClassifier.classify(PlayerErrorClassifier.DECODING_FAILED, 2))
        assertEquals(Kind.FATAL, PlayerErrorClassifier.classify(PlayerErrorClassifier.DECODING_FAILED, 3))
    }

    @Test fun codigo_desconhecido_e_retry() {
        // ERROR_CODE_UNSPECIFIED (1000) e qualquer código futuro: postura de monitor.
        assertEquals(Kind.RETRY, PlayerErrorClassifier.classify(1000, 1))
        assertEquals(Kind.RETRY, PlayerErrorClassifier.classify(-1, 1))
    }
}
