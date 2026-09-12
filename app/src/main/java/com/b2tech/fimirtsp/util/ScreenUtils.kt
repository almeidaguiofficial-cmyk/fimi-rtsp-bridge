package com.b2tech.fimirtsp.util

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import com.b2tech.fimirtsp.settings.VideoResolution

object ScreenUtils {

    /** Tamanho real da tela (largura × altura) em pixels. */
    fun screenSizePx(context: Context): Pair<Int, Int> {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.maximumWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(dm)
            dm.widthPixels to dm.heightPixels
        }
    }

    /** DPI real da tela — MediaProjection precisa disso pro VirtualDisplay. */
    fun screenDensity(context: Context): Int {
        return context.resources.displayMetrics.densityDpi
    }

    /**
     * Devolve candidatos de resolução do encoder respeitando a proporção da tela
     * do celular, com letterbox implícito. Ordem:
     *   1) alvo solicitado (720 ou 1080)
     *   2) alvo reduzido pro maior lado
     *   3) 1280×720 (fallback garantido)
     * O maior lado sempre é a largura (landscape), pois o FIMI Navi roda deitado.
     */
    fun encoderCandidates(context: Context, target: VideoResolution): List<Pair<Int, Int>> {
        val (screenW, screenH) = screenSizePx(context)
        val longSide = maxOf(screenW, screenH)
        val shortSide = minOf(screenW, screenH)
        val aspect = shortSide.toFloat() / longSide.toFloat()

        val list = mutableListOf<Pair<Int, Int>>()
        val targetLong = target.width
        val targetShort = (targetLong * aspect).toInt().let(::even)
        list += targetLong to targetShort

        if (longSide < targetLong) {
            val scaledLong = even(longSide)
            list += scaledLong to even((scaledLong * aspect).toInt())
        }

        list += 1280 to even((1280 * aspect).toInt())
        list += 1280 to 720
        return list.distinct().filter { it.first >= 320 && it.second >= 240 }
    }

    private fun even(v: Int): Int = if (v % 2 == 0) v else v - 1
}
