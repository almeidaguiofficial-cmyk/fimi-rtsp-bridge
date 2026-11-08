package com.b2tech.fimirtsp.player

/**
 * Classifica códigos de PlaybackException do Media3 sem depender do Android
 * (recebe o Int cru pra ser testável em JVM pura).
 *
 * Filosofia: o reprodutor é um monitor de voo — o feed pode ainda não ter
 * subido, ou cair e voltar. Por padrão tudo é RETRY (com backoff saturado em
 * 15s e botão Parar sempre visível); só condições de configuração/dispositivo
 * comprovadamente permanentes são FATAL. Atenção: o librtmp joga quase toda
 * falha (servidor fora, path errado, credencial errada) em IO_UNSPECIFIED —
 * indistinguíveis, então RETRY, e a UI mostra uma dica após várias falhas.
 */
object PlayerErrorClassifier {

    enum class Kind { RETRY, RETRY_IMMEDIATE, FATAL }

    // Valores de androidx.media3.common.PlaybackException.ERROR_CODE_* — copiados
    // como Int pra manter este objeto livre de dependências Android.
    const val BEHIND_LIVE_WINDOW = 1002
    const val TIMEOUT = 1003
    const val IO_UNSPECIFIED = 2000
    const val IO_NETWORK_CONNECTION_FAILED = 2001
    const val IO_NETWORK_CONNECTION_TIMEOUT = 2002
    const val IO_BAD_HTTP_STATUS = 2004
    const val IO_FILE_NOT_FOUND = 2005
    const val IO_NO_PERMISSION = 2006
    const val IO_CLEARTEXT_NOT_PERMITTED = 2007
    const val PARSING_CONTAINER_MALFORMED = 3001
    const val PARSING_MANIFEST_MALFORMED = 3002
    const val PARSING_CONTAINER_UNSUPPORTED = 3003
    const val PARSING_MANIFEST_UNSUPPORTED = 3004
    const val DECODER_INIT_FAILED = 4001
    const val DECODER_QUERY_FAILED = 4002
    const val DECODING_FAILED = 4003
    const val DECODING_FORMAT_EXCEEDS_CAPABILITIES = 4004
    const val DECODING_FORMAT_UNSUPPORTED = 4005

    /** Falhas de decodificação seguidas até desistir (glitch pós-descontinuidade é comum). */
    const val MAX_DECODING_FAILURES = 3

    fun classify(errorCode: Int, consecutiveFailures: Int): Kind = when (errorCode) {
        // Caímos atrás da janela ao vivo: re-prepara na borda, sem backoff.
        BEHIND_LIVE_WINDOW -> Kind.RETRY_IMMEDIATE

        // Rede/transiente — inclui "feed ainda não começou" (o caso normal).
        TIMEOUT,
        IO_UNSPECIFIED,
        IO_NETWORK_CONNECTION_FAILED,
        IO_NETWORK_CONNECTION_TIMEOUT,
        // Pacote corrompido no meio do stream acontece em Wi-Fi com perda.
        PARSING_CONTAINER_MALFORMED,
        -> Kind.RETRY

        // Configuração errada (URL, credencial, TLS) ou stream que não é o nosso.
        IO_BAD_HTTP_STATUS,
        IO_FILE_NOT_FOUND,
        IO_NO_PERMISSION,
        IO_CLEARTEXT_NOT_PERMITTED,
        PARSING_MANIFEST_MALFORMED,
        PARSING_CONTAINER_UNSUPPORTED,
        PARSING_MANIFEST_UNSUPPORTED,
        -> Kind.FATAL

        // Decoder pode engasgar uma vez após descontinuidade; três seguidas é real.
        DECODING_FAILED ->
            if (consecutiveFailures >= MAX_DECODING_FAILURES) Kind.FATAL else Kind.RETRY

        // Este aparelho não reproduz esse formato.
        DECODER_INIT_FAILED,
        DECODER_QUERY_FAILED,
        DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        DECODING_FORMAT_UNSUPPORTED,
        -> Kind.FATAL

        else -> Kind.RETRY
    }
}
