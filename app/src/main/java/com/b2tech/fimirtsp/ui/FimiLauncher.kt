package com.b2tech.fimirtsp.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Localiza o aplicativo da FIMI (qualquer modelo) para o botão "Abrir FIMI Navi".
 *
 * A transmissão em si NÃO depende do modelo: o app captura a tela do celular
 * enquanto o FIMI Navi roda, então serve para X8 Pro, X8 SE, X8 Mini, Tele Max
 * e qualquer outro. Este detector só decide qual ícone o botão abre.
 *
 * Reconhece por, em ordem de prioridade:
 *  1. pacote conhecido da FIMI (KNOWN_FIMI_PACKAGES);
 *  2. qualquer pacote `com.fimi.*` (robusto a modelos novos e a rename de app);
 *  3. nome visível contendo "fimi" (fallback por idioma/variação de marca).
 */
object FimiLauncher {

    /** Pacotes conhecidos do app de voo da FIMI, por família de modelo. */
    val KNOWN_FIMI_PACKAGES = linkedSetOf(
        "com.fimi.app.x8p",  // FIMI Navi 3.0: X8 Pro, X8 Tele, X8 Tele Max
        "com.fimi.app.x8s",  // X8 SE / SE 2020 / SE 2022
        "com.fimi.x8s23",    // variações mais novas da linha SE
        "com.fimi.mini",     // X8 Mini / Mini V2 (FIMI Navi mini)
        "com.fimi.soul",     // linha antiga (X8 2018, A3)
    )

    private const val FIMI_PACKAGE_PREFIX = "com.fimi."
    private const val NAME_HINT = "fimi"

    data class Candidate(val packageName: String, val label: String)

    /** Lógica pura (sem Android) para facilitar o teste da detecção. */
    fun matches(packageName: String, label: String): Boolean {
        val pkg = packageName.lowercase()
        if (pkg in KNOWN_FIMI_PACKAGES) return true
        if (pkg.startsWith(FIMI_PACKAGE_PREFIX)) return true
        return label.lowercase().contains(NAME_HINT)
    }

    /** Menor é melhor: pacote conhecido < prefixo com.fimi. < só nome. */
    fun rank(packageName: String, label: String): Int {
        val pkg = packageName.lowercase()
        return when {
            pkg in KNOWN_FIMI_PACKAGES -> 0
            pkg.startsWith(FIMI_PACKAGE_PREFIX) -> 1
            label.lowercase().contains(NAME_HINT) -> 2
            else -> Int.MAX_VALUE
        }
    }

    fun findCandidates(context: Context): List<Candidate> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION") pm.queryIntentActivities(intent, 0)
        }
        return activities
            .mapNotNull { info ->
                val pkg = info.activityInfo.packageName
                val label = info.loadLabel(pm)?.toString().orEmpty()
                if (matches(pkg, label)) Candidate(pkg, label.ifBlank { pkg }) else null
            }
            .distinctBy { it.packageName }
            .sortedBy { rank(it.packageName, it.label) }
    }

    fun launchIntentFor(context: Context, candidate: Candidate): Intent? {
        return context.packageManager.getLaunchIntentForPackage(candidate.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
