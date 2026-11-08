package com.b2tech.fimirtsp.ui

import android.app.Application
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import com.b2tech.fimirtsp.FimiApp
import com.b2tech.fimirtsp.player.PlayerErrorClassifier
import com.b2tech.fimirtsp.player.PlayerEvent
import com.b2tech.fimirtsp.player.PlayerMediaSources
import com.b2tech.fimirtsp.player.PlayerState
import com.b2tech.fimirtsp.player.PlayerTransitions
import com.b2tech.fimirtsp.settings.SettingsRepository
import com.b2tech.fimirtsp.streaming.Backoff
import com.b2tech.fimirtsp.util.LogSanitizer
import com.b2tech.fimirtsp.validation.StreamUrlValidator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlayerUiState(
    val playerUrl: String,
    val urlError: String?,
    val state: PlayerState,
    val autoReconnect: Boolean,
    val consecutiveFailures: Int,
) {
    val urlIsValid: Boolean get() = urlError == null && playerUrl.isNotBlank()
    val canPlay: Boolean get() = urlIsValid &&
        (state is PlayerState.Idle || state is PlayerState.Error || state is PlayerState.Ended)

    /** Sessão viva (conectando, tocando ou reconectando) — controla Parar e keepScreenOn. */
    val active: Boolean get() =
        state !is PlayerState.Idle && state !is PlayerState.Error && state !is PlayerState.Ended
}

/**
 * Dono do ExoPlayer da aba Assistir. O player nasce em play() e morre em
 * stopAndRelease()/onCleared() — a Activity e os composables nunca criam nem
 * liberam (rotação não recria a Activity: manifest tem configChanges).
 */
@OptIn(UnstableApi::class)
class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsRepo: SettingsRepository = (app as FimiApp).settings

    private val engineState = MutableStateFlow<PlayerState>(PlayerState.Idle)
    private val failures = MutableStateFlow(0)

    private val _player = MutableStateFlow<ExoPlayer?>(null)
    val player: StateFlow<ExoPlayer?> = _player

    private var reconnectJob: Job? = null
    private var reconnectAttempt = 0
    private var currentUrl = ""

    val state: StateFlow<PlayerUiState> = combine(
        settingsRepo.settings,
        engineState,
        failures,
    ) { settings, st, fails ->
        PlayerUiState(
            playerUrl = settings.playerUrl,
            urlError = StreamUrlValidator.validateForPlayback(settings.playerUrl).error,
            state = st,
            autoReconnect = settings.playerAutoReconnect,
            consecutiveFailures = fails,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PlayerUiState(
            playerUrl = "",
            urlError = StreamUrlValidator.validateForPlayback("").error,
            state = PlayerState.Idle,
            autoReconnect = true,
            consecutiveFailures = 0,
        ),
    )

    fun updateUrl(v: String) = viewModelScope.launch {
        settingsRepo.update { it.copy(playerUrl = v.trim()) }
    }

    fun updateAutoReconnect(v: Boolean) = viewModelScope.launch {
        settingsRepo.update { it.copy(playerAutoReconnect = v) }
    }

    fun play() {
        if (state.value.active) return
        val url = state.value.playerUrl.trim()
        val validation = StreamUrlValidator.validateForPlayback(url)
        if (!validation.ok) return
        if (validation.scheme == "rtmp" && !PlayerMediaSources.rtmpNativeLibAvailable()) {
            transition(
                PlayerEvent.FatalError(
                    "A biblioteca RTMP nativa não carrega neste aparelho. " +
                        "Use rtsp:// (ex.: rtsp://reader:senha@IP:8554/fimi)."
                )
            )
            return
        }
        currentUrl = url
        cancelReconnect()
        reconnectAttempt = 0
        failures.value = 0
        transition(PlayerEvent.Play)
        val exo = _player.value ?: createPlayer().also { _player.value = it }
        exo.setMediaSource(PlayerMediaSources.create(url))
        exo.prepare()
        exo.playWhenReady = true
    }

    fun retryNow() {
        if (engineState.value !is PlayerState.Reconnecting) return
        cancelReconnect()
        retryConnect()
    }

    fun stopAndRelease() {
        cancelReconnect()
        reconnectAttempt = 0
        failures.value = 0
        _player.value?.let {
            it.removeListener(listener)
            it.release()
        }
        _player.value = null
        transition(PlayerEvent.Stop)
    }

    override fun onCleared() {
        stopAndRelease()
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    reconnectAttempt = 0
                    failures.value = 0
                    transition(PlayerEvent.Ready(System.currentTimeMillis()))
                }
                // A máquina só aceita BufferingStarted vindo de Playing; o STATE_BUFFERING
                // inicial do prepare() vira no-op e a UI continua em Conectando.
                Player.STATE_BUFFERING -> transition(PlayerEvent.BufferingStarted)
                // MediaMTX encerra a sessão quando o publicador para.
                Player.STATE_ENDED -> onStreamEnded()
                else -> Unit
            }
        }

        override fun onRenderedFirstFrame() {
            // READY pode chegar antes do primeiro frame em stream video-only.
            reconnectAttempt = 0
            failures.value = 0
        }

        override fun onPlayerError(error: PlaybackException) = handleError(error)
    }

    private fun createPlayer(): ExoPlayer {
        // Buffers curtos: a prioridade é latência, não suavidade. Começa a tocar
        // com 300 ms e volta de rebuffer com 1 s.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 1_000,
                /* maxBufferMs = */ 3_000,
                /* bufferForPlaybackMs = */ 300,
                /* bufferForPlaybackAfterRebufferMs = */ 1_000,
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(/* backBufferDurationMs = */ 0, /* retainBackBufferFromKeyframe = */ false)
            .build()
        val renderers = DefaultRenderersFactory(getApplication<Application>())
            .setEnableDecoderFallback(true)
        // Sem setAudioAttributes(handleFocus): o stream é video-only e não deve
        // roubar o foco de áudio de música/navegação.
        return ExoPlayer.Builder(getApplication<Application>(), renderers)
            .setLoadControl(loadControl)
            .build()
            .also { it.addListener(listener) }
    }

    private fun handleError(error: PlaybackException) {
        failures.value += 1
        when (PlayerErrorClassifier.classify(error.errorCode, failures.value)) {
            PlayerErrorClassifier.Kind.RETRY_IMMEDIATE -> _player.value?.let {
                it.seekToDefaultPosition()
                it.prepare()
                it.playWhenReady = true
            }
            PlayerErrorClassifier.Kind.RETRY ->
                if (state.value.autoReconnect) scheduleReconnect()
                else transition(PlayerEvent.FatalError(friendlyMessage(error)))
            PlayerErrorClassifier.Kind.FATAL ->
                transition(PlayerEvent.FatalError(friendlyMessage(error)))
        }
    }

    private fun onStreamEnded() {
        if (state.value.autoReconnect) scheduleReconnect()
        else transition(PlayerEvent.StreamEnded)
    }

    private fun scheduleReconnect() {
        reconnectAttempt += 1
        val delayMs = Backoff.delayMs(reconnectAttempt)
        transition(PlayerEvent.RecoverableError(reconnectAttempt, delayMs))
        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch {
            delay(delayMs)
            retryConnect()
        }
    }

    private fun retryConnect() {
        val exo = _player.value ?: return
        transition(PlayerEvent.RetryTimerFired)
        exo.setMediaSource(PlayerMediaSources.create(currentUrl))
        exo.prepare()
        exo.playWhenReady = true
    }

    private fun cancelReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
    }

    private fun transition(event: PlayerEvent) {
        engineState.value = PlayerTransitions.next(engineState.value, event)
    }

    private fun friendlyMessage(error: PlaybackException): String =
        LogSanitizer.sanitize("${error.errorCodeName}: ${error.message ?: "falha na reprodução"}")

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: androidx.lifecycle.viewmodel.CreationExtras): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                return PlayerViewModel(app) as T
            }
        }
    }
}
