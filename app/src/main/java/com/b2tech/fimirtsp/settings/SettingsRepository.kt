package com.b2tech.fimirtsp.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.b2tech.fimirtsp.BuildConfig
import com.b2tech.fimirtsp.audio.VolumeMath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class RtspTransport { TCP, UDP }

enum class VideoResolution(val width: Int, val height: Int, val label: String) {
    R720P(1280, 720, "1280×720"),
    R1080P(1920, 1080, "1920×1080");

    companion object {
        fun fromName(name: String?): VideoResolution =
            entries.firstOrNull { it.name == name } ?: R720P
    }
}

data class StreamSettings(
    val serverUrl: String,
    val rtspUser: String,
    val rtspPassword: String,
    val resolution: VideoResolution,
    val fps: Int,
    val bitrateBps: Int,
    val transport: RtspTransport,
    val stopOnTaskRemoved: Boolean,
    /** Teclas físicas de volume viram comando do app enquanto a tela dele está na frente. */
    val volumeKeysEnabled: Boolean,
    /** Tamanho do passo do comando de volume, em % do máximo do aparelho. */
    val volumeStepPercent: Int,
) {
    companion object {
        const val MIN_BITRATE_BPS = 300_000
        const val MAX_BITRATE_BPS = 20_000_000
        const val DEFAULT_BITRATE_BPS = 3_000_000
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "fimi_rtsp_settings")

/**
 * Persistência das configurações. Senha vive em DataStore criptografado do Android
 * (StrongBox opcional futuro); log nunca imprime o valor.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val URL = stringPreferencesKey("server_url")
        val USER = stringPreferencesKey("rtsp_user")
        val PASS = stringPreferencesKey("rtsp_pass")
        val RES = stringPreferencesKey("resolution")
        val FPS = intPreferencesKey("fps")
        val BITRATE = intPreferencesKey("bitrate_bps")
        val TRANSPORT = stringPreferencesKey("transport")
        val STOP_ON_TASK_REMOVED = booleanPreferencesKey("stop_on_task_removed")
        val VOLUME_KEYS = booleanPreferencesKey("volume_keys_enabled")
        val VOLUME_STEP = intPreferencesKey("volume_step_percent")
    }

    val settings: Flow<StreamSettings> = context.dataStore.data.map(::read)

    suspend fun update(transform: (StreamSettings) -> StreamSettings) {
        context.dataStore.edit { prefs ->
            val next = transform(read(prefs))
            prefs[Keys.URL] = next.serverUrl
            prefs[Keys.USER] = next.rtspUser
            prefs[Keys.PASS] = next.rtspPassword
            prefs[Keys.RES] = next.resolution.name
            prefs[Keys.FPS] = next.fps.coerceIn(10, 60)
            prefs[Keys.BITRATE] = next.bitrateBps
                .coerceIn(StreamSettings.MIN_BITRATE_BPS, StreamSettings.MAX_BITRATE_BPS)
            prefs[Keys.TRANSPORT] = next.transport.name
            prefs[Keys.STOP_ON_TASK_REMOVED] = next.stopOnTaskRemoved
            prefs[Keys.VOLUME_KEYS] = next.volumeKeysEnabled
            prefs[Keys.VOLUME_STEP] = next.volumeStepPercent
                .coerceIn(VolumeMath.MIN_STEP_PERCENT, VolumeMath.MAX_STEP_PERCENT)
        }
    }

    /** Leitura única: o fluxo de leitura e o de escrita não podem divergir nos defaults. */
    private fun read(prefs: Preferences): StreamSettings = StreamSettings(
        // null = nunca salvo → pré-preenche com o default do build; "" = usuário limpou.
        serverUrl = prefs[Keys.URL] ?: BuildConfig.DEFAULT_STREAM_URL,
        rtspUser = prefs[Keys.USER].orEmpty(),
        rtspPassword = prefs[Keys.PASS].orEmpty(),
        resolution = VideoResolution.fromName(prefs[Keys.RES]),
        fps = prefs[Keys.FPS]?.coerceIn(10, 60) ?: 30,
        bitrateBps = prefs[Keys.BITRATE]?.coerceIn(
            StreamSettings.MIN_BITRATE_BPS,
            StreamSettings.MAX_BITRATE_BPS,
        ) ?: StreamSettings.DEFAULT_BITRATE_BPS,
        transport = when (prefs[Keys.TRANSPORT]) {
            RtspTransport.UDP.name -> RtspTransport.UDP
            else -> RtspTransport.TCP
        },
        stopOnTaskRemoved = prefs[Keys.STOP_ON_TASK_REMOVED] ?: true,
        volumeKeysEnabled = prefs[Keys.VOLUME_KEYS] ?: true,
        volumeStepPercent = prefs[Keys.VOLUME_STEP]?.coerceIn(
            VolumeMath.MIN_STEP_PERCENT,
            VolumeMath.MAX_STEP_PERCENT,
        ) ?: VolumeMath.DEFAULT_STEP_PERCENT,
    )
}
