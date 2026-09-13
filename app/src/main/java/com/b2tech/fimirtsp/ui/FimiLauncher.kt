package com.b2tech.fimirtsp.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Localiza o(s) aplicativo(s) do drone FIMI.
 *
 * Duas estratégias combinadas (a de pacote tem prioridade):
 *  1. Pacotes conhecidos — robusto a idioma/rename. Consulta direta dos
 *     applicationId oficiais declarados em `<queries>` no manifesto. O X8 Tele Max
 *     (como o X8 Pro e o X8 Tele) usa o FIMI Navi 3.0 = `com.fimi.app.x8p`.
 *  2. Nome visível — fallback para apps FIMI futuros/não listados. Enumera
 *     atividades LAUNCHER e filtra pelo rótulo "FIMI Navi", respeitando o package
 *     visibility do Android 11+ (sem QUERY_ALL_PACKAGES).
 */
object FimiLauncher {

    /**
     * applicationId oficiais dos apps de controle FIMI, em ordem de prioridade.
     * Precisam estar declarados em `<queries>` no AndroidManifest para ficarem
     * visíveis sem QUERY_ALL_PACKAGES.
     */
    val KNOWN_FIMI_PACKAGES: List<String> = listOf(
        "com.fimi.app.x8p", // FIMI Navi 3.0 — X8 Pro / X8 Tele / X8 Tele Max
        "com.fimi.app.x8s", // FIMI Navi — família X8 SE
    )

    private val NAME_HINTS = listOf(
        "fimi navi",
    )

    data class Candidate(val packageName: String, val label: String)

    /** Rótulo amigável de fallback quando não dá pra carregar o label do pacote. */
    fun fallbackLabel(packageName: String): String = when (packageName) {
        "com.fimi.app.x8p" -> "FIMI Navi 3.0"
        else -> "FIMI Navi"
    }

    /** True se o rótulo visível parece ser um app FIMI Navi. Puro e testável. */
    fun matchesFimiLabel(label: String): Boolean {
        val lower = label.lowercase()
        return NAME_HINTS.any { lower.contains(it) }
    }

    fun findCandidates(context: Context): List<Candidate> {
        val pm = context.packageManager
        // LinkedHashMap preserva a ordem: pacotes conhecidos primeiro, depois o fallback.
        val found = LinkedHashMap<String, Candidate>()

        // 1) Pacotes conhecidos. getLaunchIntentForPackage != null => instalado e lançável.
        for (pkg in KNOWN_FIMI_PACKAGES) {
            if (pm.getLaunchIntentForPackage(pkg) == null) continue
            val label = loadPackageLabel(pm, pkg) ?: fallbackLabel(pkg)
            found[pkg] = Candidate(pkg, label)
        }

        // 2) Fallback por rótulo visível (apps FIMI Navi não listados acima).
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION") pm.queryIntentActivities(intent, 0)
        }
        for (info in activities) {
            val pkg = info.activityInfo.packageName
            if (found.containsKey(pkg)) continue
            val label = info.loadLabel(pm)?.toString().orEmpty()
            if (matchesFimiLabel(label)) {
                found[pkg] = Candidate(pkg, label)
            }
        }

        return found.values.toList()
    }

    fun launchIntentFor(context: Context, candidate: Candidate): Intent? {
        return context.packageManager.getLaunchIntentForPackage(candidate.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun loadPackageLabel(pm: PackageManager, packageName: String): String? = runCatching {
        val appInfo = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION") pm.getApplicationInfo(packageName, 0)
        }
        pm.getApplicationLabel(appInfo).toString()
    }.getOrNull()?.takeIf { it.isNotBlank() }
}
