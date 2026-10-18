package com.b2tech.fimirtsp.util

/**
 * Nunca deixamos credenciais vazarem em logs, toasts ou mensagens de erro.
 * Substitui userinfo (user:pass@) e valores de header/URL que pareçam senha
 * por reticências entre asteriscos.
 */
object LogSanitizer {

    private val userInfoInUrl = Regex("""([A-Za-z][A-Za-z0-9+.\-]*://)[^/@\s]+@""")
    private val basicAuthHeader = Regex("""(?i)(Authorization:\s*Basic\s+)\S+""")
    // "key" cobre a stream key em query string (?key=sk_...), que servidores RTMP usam como senha;
    // "passphrase" é a senha de criptografia do SRT.
    private val genericPasswordKV =
        Regex("""(?i)\b((?:pass(?:word|phrase)?|senha|pwd|token|secret|key)\s*[:=]\s*)("[^"]+"|[^\s&"]+)""")
    // SRT no MediaMTX: streamid=acao:caminho:usuario:senha — a senha é o 4º campo.
    private val srtStreamIdPassword = Regex("""(?i)(streamid=(?:[^:&\s]*:){3})[^&\s]+""")

    fun sanitize(input: String?): String {
        if (input.isNullOrEmpty()) return ""
        var out = input
        out = userInfoInUrl.replace(out) { it.groupValues[1] + "***@" }
        out = basicAuthHeader.replace(out) { it.groupValues[1] + "***" }
        out = genericPasswordKV.replace(out) { it.groupValues[1] + "***" }
        out = srtStreamIdPassword.replace(out) { it.groupValues[1] + "***" }
        return out
    }
}
