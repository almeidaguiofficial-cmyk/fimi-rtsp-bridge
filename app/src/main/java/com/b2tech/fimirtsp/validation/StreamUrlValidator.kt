package com.b2tech.fimirtsp.validation

/**
 * Valida URLs de publicação. Aceita:
 *  - rtsp://host[:porta]/caminho  e  rtsps://...
 *  - rtmp://host[:porta]/app/chave[?key=...]  e  rtmps://...
 *  - srt://host[:porta]?streamid=...  ou  srt://host[:porta]/streamid
 *  - userinfo (user:pass@) opcional — apesar de recomendarmos os campos separados
 *  - host IPv4, hostname ou colchetes IPv6
 *
 * Rejeita: URLs sem esquema, portas fora de 1..65535, caminho vazio (no SRT,
 * caminho e streamid vazios).
 * Não faz DNS lookup: só valida a forma.
 */
object StreamUrlValidator {

    enum class Family { RTSP, RTMP, SRT }

    data class Result(
        val ok: Boolean,
        val error: String? = null,
        val scheme: String? = null,
    ) {
        val family: Family? get() = when (scheme) {
            "rtsp", "rtsps" -> Family.RTSP
            "rtmp", "rtmps" -> Family.RTMP
            "srt" -> Family.SRT
            else -> null
        }
    }

    private val ALLOWED_SCHEMES = setOf("rtsp", "rtsps", "rtmp", "rtmps", "srt")

    fun validate(input: String?): Result {
        val raw = input?.trim().orEmpty()
        if (raw.isEmpty()) return Result(false, "Endereço vazio")

        val schemeSep = raw.indexOf("://")
        if (schemeSep <= 0) return Result(false, "Falta o esquema (rtsp://, rtmp:// ou srt://)")

        val scheme = raw.substring(0, schemeSep).lowercase()
        if (scheme !in ALLOWED_SCHEMES) {
            return Result(false, "Esquema não suportado: $scheme (use rtsp, rtsps, rtmp, rtmps ou srt)")
        }

        val rest = raw.substring(schemeSep + 3)
        if (rest.isBlank()) return Result(false, "Falta o host")

        // A autoridade acaba no primeiro '/' ou '?': o SRT do MediaMTX não tem caminho
        // (srt://host:8890?streamid=publish:fimi).
        val authorityEnd = rest.indexOfFirst { it == '/' || it == '?' }.let { if (it < 0) rest.length else it }
        val authority = rest.substring(0, authorityEnd)
        val path = rest.substring(authorityEnd)

        // Remove userinfo, se houver.
        val hostPort = authority.substringAfterLast('@')

        val (host, portStr) = when {
            hostPort.startsWith("[") -> {
                val closing = hostPort.indexOf(']')
                if (closing <= 0) return Result(false, "IPv6 mal formado")
                val h = hostPort.substring(1, closing)
                val p = hostPort.substring(closing + 1)
                    .removePrefix(":")
                    .ifEmpty { null }
                h to p
            }
            else -> {
                val colon = hostPort.indexOf(':')
                if (colon < 0) hostPort to null
                else hostPort.substring(0, colon) to hostPort.substring(colon + 1)
            }
        }

        if (host.isBlank()) return Result(false, "Host vazio")
        if (host.any { it.isWhitespace() }) return Result(false, "Host inválido")

        if (portStr != null) {
            val port = portStr.toIntOrNull() ?: return Result(false, "Porta inválida")
            if (port !in 1..65535) return Result(false, "Porta fora do intervalo 1..65535")
        }

        val pathOnly = path.substringBefore('?')
        val hasPath = pathOnly.isNotBlank() && pathOnly != "/"
        if (scheme == "srt") {
            val hasStreamId = path.substringAfter('?', "").split('&').any {
                it.startsWith("streamid=") && it.length > "streamid=".length
            }
            if (!hasPath && !hasStreamId) {
                return Result(false, "Falta o streamid (ex.: srt://IP:8890?streamid=publish:fimi)")
            }
        } else if (!hasPath) {
            return Result(false, "Falta o caminho após o host (ex.: /fimi ou /live/ID)")
        }

        return Result(true, scheme = scheme)
    }

    fun isValid(input: String?): Boolean = validate(input).ok
}
