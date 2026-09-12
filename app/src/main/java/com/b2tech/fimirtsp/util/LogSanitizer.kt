package com.b2tech.fimirtsp.util

/**
 * Nunca deixamos credenciais vazarem em logs, toasts ou mensagens de erro.
 * Substitui userinfo (user:pass@) e valores de header/URL que pareçam senha
 * por reticências entre asteriscos.
 */
object LogSanitizer {

    private val userInfoInUrl = Regex("""([A-Za-z][A-Za-z0-9+.\-]*://)[^/@\s]+@""")
    private val basicAuthHeader = Regex("""(?i)(Authorization:\s*Basic\s+)\S+""")
    private val genericPasswordKV =
        Regex("""(?i)((?:pass(?:word)?|senha|pwd|token|secret)\s*[:=]\s*)("[^"]+"|\S+)""")

    fun sanitize(input: String?): String {
        if (input.isNullOrEmpty()) return ""
        var out = input
        out = userInfoInUrl.replace(out) { it.groupValues[1] + "***@" }
        out = basicAuthHeader.replace(out) { it.groupValues[1] + "***" }
        out = genericPasswordKV.replace(out) { it.groupValues[1] + "***" }
        return out
    }
}
