package com.b2tech.fimirtsp.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Localiza o(s) aplicativo(s) do drone FIMI usando package visibility segura:
 * enumera atividades LAUNCHER e filtra pelo nome visível "FIMI Navi".
 */
object FimiLauncher {

    private val NAME_HINTS = listOf(
        "fimi navi",
        "fimi navi 3",
    )

    data class Candidate(val packageName: String, val label: String)

    fun findCandidates(context: Context): List<Candidate> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION") pm.queryIntentActivities(intent, 0)
        }
        return activities.mapNotNull { info ->
            val label = info.loadLabel(pm)?.toString().orEmpty()
            val lower = label.lowercase()
            if (NAME_HINTS.any { lower.contains(it) }) {
                Candidate(info.activityInfo.packageName, label)
            } else null
        }.distinctBy { it.packageName }
    }

    fun launchIntentFor(context: Context, candidate: Candidate): Intent? {
        return context.packageManager.getLaunchIntentForPackage(candidate.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
