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
    // Aba Assistir. Default vazio de propósito: DEFAULT_STREAM_URL é credencial
    // de publicação e não deve pré-preencher o reprodutor.
    val playerUrl: String = "",
    val playerAutoReconnect: Boolean = true,
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
        val PLAYER_URL = stringPreferencesKey("player_url")
        val PLAYER_AUTO_RECONNECT = booleanPreferencesKey("player_auto_reconnect")
    }

    val settings: Flow<StreamSettings> = context.dataStore.data.map { prefs ->
        StreamSettings(
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
            playerUrl = prefs[Keys.PLAYER_URL].orEmpty(),
            playerAutoReconnect = prefs[Keys.PLAYER_AUTO_RECONNECT] ?: true,
        )
    }

    suspend fun update(transform: (StreamSettings) -> StreamSettings) {
        context.dataStore.edit { prefs ->
            val current = StreamSettings(
                serverUrl = prefs[Keys.URL] ?: BuildConfig.DEFAULT_STREAM_URL,
                rtspUser = prefs[Keys.USER].orEmpty(),
                rtspPassword = prefs[Keys.PASS].orEmpty(),
                resolution = VideoResolution.fromName(prefs[Keys.RES]),
                fps = prefs[Keys.FPS] ?: 30,
                bitrateBps = prefs[Keys.BITRATE] ?: StreamSettings.DEFAULT_BITRATE_BPS,
                transport = when (prefs[Keys.TRANSPORT]) {
                    RtspTransport.UDP.name -> RtspTransport.UDP
                    else -> RtspTransport.TCP
                },
                stopOnTaskRemoved = prefs[Keys.STOP_ON_TASK_REMOVED] ?: true,
                playerUrl = prefs[Keys.PLAYER_URL].orEmpty(),
                playerAutoReconnect = prefs[Keys.PLAYER_AUTO_RECONNECT] ?: true,
            )
            val next = transform(current)
            prefs[Keys.URL] = next.serverUrl
            prefs[Keys.USER] = next.rtspUser
            prefs[Keys.PASS] = next.rtspPassword
            prefs[Keys.RES] = next.resolution.name
            prefs[Keys.FPS] = next.fps.coerceIn(10, 60)
            prefs[Keys.BITRATE] = next.bitrateBps
                .coerceIn(StreamSettings.MIN_BITRATE_BPS, StreamSettings.MAX_BITRATE_BPS)
            prefs[Keys.TRANSPORT] = next.transport.name
            prefs[Keys.STOP_ON_TASK_REMOVED] = next.stopOnTaskRemoved
            prefs[Keys.PLAYER_URL] = next.playerUrl
            prefs[Keys.PLAYER_AUTO_RECONNECT] = next.playerAutoReconnect
        }
    }
}
