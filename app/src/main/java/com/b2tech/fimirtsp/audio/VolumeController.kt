package com.b2tech.fimirtsp.audio

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

/** Foto do volume de mídia do aparelho no instante da leitura. */
data class VolumeLevel(
    val index: Int,
    val maxIndex: Int,
    val percent: Int,
    val fixed: Boolean,
) {
    /** Falso em dock/HDMI/TV com volume travado, ou quando o aparelho não expõe a faixa. */
    val controllable: Boolean get() = maxIndex > 0 && !fixed
}

sealed interface VolumeOutcome {
    data class Applied(val level: VolumeLevel) : VolumeOutcome
    data class Rejected(val level: VolumeLevel, val reason: String) : VolumeOutcome
}

/**
 * Comando de volume do app. Mexe em STREAM_MUSIC — o canal que o FIMI Navi usa para
 * alertas sonoros — e não exige permissão nenhuma no manifesto.
 *
 * Importante: isto controla o alto-falante do celular, não uma faixa de áudio da
 * transmissão. O pipeline publica só vídeo (setOnlyVideo=true no motor).
 */
class VolumeController(context: Context) {

    private val appContext = context.applicationContext
    private val audio = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun level(): VolumeLevel {
        val max = runCatching { audio.getStreamMaxVolume(STREAM) }.getOrDefault(0)
        val index = runCatching { audio.getStreamVolume(STREAM) }.getOrDefault(0)
        val fixed = runCatching { audio.isVolumeFixed }.getOrDefault(false)
        return VolumeLevel(
            index = index,
            maxIndex = max,
            percent = VolumeMath.percent(index, max),
            fixed = fixed,
        )
    }

    fun raise(stepPercent: Int, showSystemUi: Boolean = true): VolumeOutcome {
        val current = level()
        return apply(VolumeMath.raised(current.index, current.maxIndex, stepPercent), showSystemUi)
    }

    fun lower(stepPercent: Int, showSystemUi: Boolean = true): VolumeOutcome {
        val current = level()
        return apply(VolumeMath.lowered(current.index, current.maxIndex, stepPercent), showSystemUi)
    }

    fun setPercent(percent: Int, showSystemUi: Boolean = false): VolumeOutcome {
        val current = level()
        return apply(VolumeMath.indexForPercent(percent, current.maxIndex), showSystemUi)
    }

    /**
     * Emite o volume atual e cada mudança — inclusive as feitas fora do app (teclas
     * físicas com o FIMI em primeiro plano, painel do sistema, fone Bluetooth).
     */
    fun observe(): Flow<VolumeLevel> = callbackFlow {
        trySend(level())
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(level())
            }
        }
        appContext.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
        awaitClose { appContext.contentResolver.unregisterContentObserver(observer) }
    }.distinctUntilChanged().conflate()

    private fun apply(targetIndex: Int, showSystemUi: Boolean): VolumeOutcome {
        val before = level()
        if (before.maxIndex <= 0) {
            return VolumeOutcome.Rejected(before, "Este aparelho não expõe controle de volume de mídia.")
        }
        if (before.fixed) {
            return VolumeOutcome.Rejected(before, "Volume travado pela saída de áudio atual (dock, HDMI ou política do sistema).")
        }
        val target = targetIndex.coerceIn(0, before.maxIndex)
        val flags = if (showSystemUi) AudioManager.FLAG_SHOW_UI else 0
        return try {
            audio.setStreamVolume(STREAM, target, flags)
            VolumeOutcome.Applied(level())
        } catch (_: SecurityException) {
            // Não Perturbe restringe o volume de mídia e o app não tem acesso à política.
            VolumeOutcome.Rejected(before, "O modo Não Perturbe está bloqueando a mudança de volume.")
        } catch (t: Throwable) {
            VolumeOutcome.Rejected(before, "Não consegui mudar o volume (${t.javaClass.simpleName}).")
        }
    }

    private companion object {
        val STREAM = AudioManager.STREAM_MUSIC
    }
}
