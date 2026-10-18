package com.b2tech.fimirtsp.streaming

/**
 * O SRT da RootEncoder negocia 120 ms de latência quando a URL não diz nada — curto
 * demais para retransmitir pacote perdido no 4G (RTT de 50–200 ms; a regra do SRT é
 * latência ≥ 4× RTT). Aqui só completamos a URL com `latency=` (em ms, como a lib lê)
 * se o usuário não tiver escolhido um valor.
 */
object SrtUrl {
    const val DEFAULT_LATENCY_MS = 800

    fun withDefaultLatency(url: String, latencyMs: Int = DEFAULT_LATENCY_MS): String {
        if (!url.startsWith("srt://", ignoreCase = true)) return url
        val queryStart = url.indexOf('?')
        val query = if (queryStart < 0) "" else url.substring(queryStart + 1)
        // A lib casa a chave exatamente ("latency"), então a checagem também.
        if (query.split('&').any { it.substringBefore('=') == "latency" }) return url
        val separator = when {
            queryStart < 0 -> "?"
            url.endsWith("?") || url.endsWith("&") -> ""
            else -> "&"
        }
        return "$url${separator}latency=$latencyMs"
    }
}
