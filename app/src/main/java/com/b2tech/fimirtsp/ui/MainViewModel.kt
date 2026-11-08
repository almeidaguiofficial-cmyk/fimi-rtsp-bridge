package com.b2tech.fimirtsp.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.b2tech.fimirtsp.BuildConfig
import com.b2tech.fimirtsp.FimiApp
import com.b2tech.fimirtsp.network.Connectivity
import com.b2tech.fimirtsp.network.ConnectivityMonitor
import com.b2tech.fimirtsp.settings.QualityPreset
import com.b2tech.fimirtsp.settings.RtspTransport
import com.b2tech.fimirtsp.settings.SettingsRepository
import com.b2tech.fimirtsp.settings.StreamSettings
import com.b2tech.fimirtsp.settings.VideoCodecPref
import com.b2tech.fimirtsp.settings.VideoResolution
import com.b2tech.fimirtsp.streaming.EncoderChoice
import com.b2tech.fimirtsp.streaming.EncoderProbe
import com.b2tech.fimirtsp.streaming.StreamService
import com.b2tech.fimirtsp.streaming.StreamState
import com.b2tech.fimirtsp.validation.StreamUrlValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UiState(
    val settings: StreamSettings,
    val urlError: String?,
    val engineState: StreamState,
    val connectivity: Connectivity,
    val bitrateSampleBps: Long,
    val reconnectAttempts: Int,
    val awaitingProjection: Boolean,
    /**
     * Encoder e modo de bitrate (CBR/VBR) por codec; null até a sondagem terminar.
     * Valor null dentro do mapa = o aparelho não tem encoder daquele codec.
     */
    val encoderChoices: Map<VideoCodecPref, EncoderChoice?>?,
    /** Codec que o encoder aceitou na transmissão atual; null fora de transmissão. */
    val activeCodec: VideoCodecPref?,
) {
    val urlIsValid: Boolean get() = urlError == null && settings.serverUrl.isNotBlank()
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsRepo: SettingsRepository = (app as FimiApp).settings
    private val connectivity = ConnectivityMonitor(app)

    private val projectionRequestFlag = MutableStateFlow(false)

    private val encoderChoices = MutableStateFlow<Map<VideoCodecPref, EncoderChoice?>?>(null)

    init {
        // MediaCodecList é síncrona e pode levar dezenas de ms: fora da main thread.
        viewModelScope.launch(Dispatchers.Default) {
            encoderChoices.value = VideoCodecPref.entries.associateWith { EncoderProbe.probe(it) }
        }
    }

    private data class ServiceOutputs(
        val state: StreamState,
        val bps: Long,
        val attempts: Int,
        val codec: VideoCodecPref?,
    )

    // Compõe os StateFlows do serviço num só valor para caber no combine tipado (5 args).
    private val serviceOutputs = combine(
        StreamService.state,
        StreamService.statsBps,
        StreamService.reconnectAttempts,
        StreamService.activeCodec,
    ) { st, bps, attempts, codec -> ServiceOutputs(st, bps, attempts, codec) }

    val state: StateFlow<UiState> = combine(
        settingsRepo.settings,
        serviceOutputs,
        connectivity.observe(),
        projectionRequestFlag,
        encoderChoices,
    ) { settings, service, conn, awaiting, encoders ->
        UiState(
            settings = settings,
            urlError = StreamUrlValidator.validate(settings.serverUrl).error,
            engineState = service.state,
            connectivity = conn,
            bitrateSampleBps = service.bps,
            reconnectAttempts = service.attempts,
            awaitingProjection = awaiting,
            encoderChoices = encoders,
            activeCodec = service.codec,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UiState(
            settings = StreamSettings(
                serverUrl = BuildConfig.DEFAULT_STREAM_URL,
                rtspUser = "",
                rtspPassword = "",
                resolution = VideoResolution.R720P,
                fps = 30,
                bitrateBps = StreamSettings.DEFAULT_BITRATE_BPS,
                transport = RtspTransport.TCP,
                stopOnTaskRemoved = true,
            ),
            urlError = StreamUrlValidator.validate(BuildConfig.DEFAULT_STREAM_URL).error,
            engineState = StreamState.Idle,
            connectivity = Connectivity(false, Connectivity.Transport.NONE),
            bitrateSampleBps = 0L,
            reconnectAttempts = 0,
            awaitingProjection = false,
            encoderChoices = null,
            activeCodec = null,
        ),
    )

    fun updateUrl(v: String) = viewModelScope.launch {
        settingsRepo.update { it.copy(serverUrl = v.trim()) }
    }

    fun updateUser(v: String) = viewModelScope.launch {
        settingsRepo.update { it.copy(rtspUser = v) }
    }

    fun updatePassword(v: String) = viewModelScope.launch {
        settingsRepo.update { it.copy(rtspPassword = v) }
    }

    fun updateResolution(v: VideoResolution) = viewModelScope.launch {
        settingsRepo.update { it.copy(resolution = v) }
    }

    fun updateFps(v: Int) = viewModelScope.launch {
        settingsRepo.update { it.copy(fps = v.coerceIn(10, 60)) }
    }

    fun updateBitrateBps(v: Int) = viewModelScope.launch {
        settingsRepo.update {
            it.copy(
                bitrateBps = v.coerceIn(
                    StreamSettings.MIN_BITRATE_BPS,
                    StreamSettings.MAX_BITRATE_BPS,
                )
            )
        }
    }

    /** Uma só gravação: resolução, fps, bitrate e codec mudam juntos. */
    fun applyMaxQuality() = viewModelScope.launch {
        settingsRepo.update { QualityPreset.applyTo(it) }
    }

    fun updateVideoCodec(v: VideoCodecPref) = viewModelScope.launch {
        settingsRepo.update { it.copy(videoCodec = v) }
    }

    fun updateTransport(v: RtspTransport) = viewModelScope.launch {
        settingsRepo.update { it.copy(transport = v) }
    }

    fun markAwaitingProjection(v: Boolean) {
        projectionRequestFlag.value = v
    }

    fun onProjectionResult(app: Application, resultCode: Int, data: Intent?) {
        markAwaitingProjection(false)
        if (resultCode != android.app.Activity.RESULT_OK || data == null) return
        StreamService.requestStart(app, resultCode, data)
    }

    fun stopStreaming(app: Application) {
        StreamService.requestStop(app)
    }

    companion object {
        val Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: androidx.lifecycle.viewmodel.CreationExtras): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                return MainViewModel(app) as T
            }
        }
    }
}
