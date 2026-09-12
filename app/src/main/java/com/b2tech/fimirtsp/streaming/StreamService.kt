package com.b2tech.fimirtsp.streaming

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.b2tech.fimirtsp.FimiApp
import com.b2tech.fimirtsp.R
import com.b2tech.fimirtsp.network.Connectivity
import com.b2tech.fimirtsp.network.ConnectivityMonitor
import com.b2tech.fimirtsp.settings.RtspTransport
import com.b2tech.fimirtsp.settings.SettingsRepository
import com.b2tech.fimirtsp.settings.StreamSettings
import com.b2tech.fimirtsp.settings.VideoResolution
import com.b2tech.fimirtsp.util.LogSanitizer
import com.b2tech.fimirtsp.util.ScreenUtils
import com.b2tech.fimirtsp.validation.RtspUrlValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Serviço em primeiro plano (mediaProjection) que mantém a transmissão viva
 * enquanto o FIMI Navi está em uso. Toda a lógica de estado, reconexão e
 * captura mora aqui — a UI só observa StreamService.state.
 */
class StreamService : Service(), StreamEngine.Listener {

    companion object {
        private const val TAG = "StreamService"
        private const val CHANNEL_ID = "fimi_rtsp_bridge"
        private const val NOTIF_ID = 4210

        const val ACTION_START = "com.b2tech.fimirtsp.START"
        const val ACTION_STOP = "com.b2tech.fimirtsp.STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        private val _state: MutableStateFlow<StreamState> = MutableStateFlow(StreamState.Idle)
        val state: StateFlow<StreamState> = _state.asStateFlow()

        private val _lastError: MutableStateFlow<String?> = MutableStateFlow(null)
        val lastError: StateFlow<String?> = _lastError.asStateFlow()

        private val _statsBps: MutableStateFlow<Long> = MutableStateFlow(0L)
        val statsBps: StateFlow<Long> = _statsBps.asStateFlow()

        private val _reconnectAttempts: MutableStateFlow<Int> = MutableStateFlow(0)
        val reconnectAttempts: StateFlow<Int> = _reconnectAttempts.asStateFlow()

        fun requestStart(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, StreamService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun requestStop(context: Context) {
            val intent = Intent(context, StreamService::class.java).apply { action = ACTION_STOP }
            context.startService(intent)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var engine: StreamEngine
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var connectivity: ConnectivityMonitor
    private var reconnectJob: Job? = null
    private var manualStop = false
    private var streamingStartedAt = 0L
    private var lastAppliedFps = 30
    private var lastAppliedBitrate = 3_000_000
    private var stopOnTaskRemoved = true

    override fun onCreate() {
        super.onCreate()
        engine = RootEncoderStreamEngine(applicationContext)
        engine.setListener(this)
        settingsRepo = (application as? FimiApp)?.settings ?: SettingsRepository(applicationContext)
        connectivity = ConnectivityMonitor(applicationContext)
        createChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_STOP -> handleStopRequested()
            else -> {
                if (_state.value is StreamState.Idle) {
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
        }
        return START_STICKY
    }

    private fun handleStart(intent: Intent) {
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val data: Intent? = intent.getParcelableExtra(EXTRA_RESULT_DATA)
        if (resultCode == 0 || data == null) {
            transition(StreamEvent.FatalError("Consentimento de captura ausente"))
            stopForegroundAndSelf()
            return
        }

        startForegroundNotification(getString(R.string.status_preparing))
        transition(StreamEvent.AuthorizationGranted)

        scope.launch {
            val settings = settingsRepo.settings.first()
            stopOnTaskRemoved = settings.stopOnTaskRemoved
            val validation = RtspUrlValidator.validate(settings.serverUrl)
            if (!validation.ok) {
                _lastError.value = "URL RTSP inválida: ${validation.error}"
                transition(StreamEvent.PrepareFailure(_lastError.value!!))
                stopForegroundAndSelf()
                return@launch
            }
            val net = connectivity.snapshot()
            if (!net.hasInternet && !isLikelyLan(settings.serverUrl)) {
                _lastError.value = getString(R.string.warn_no_network)
                transition(StreamEvent.PrepareFailure(_lastError.value!!))
                stopForegroundAndSelf()
                return@launch
            }
            preparePipelineAndStart(resultCode, data, settings)
        }
    }

    private fun preparePipelineAndStart(
        resultCode: Int,
        data: Intent,
        settings: StreamSettings,
    ) {
        val candidates = ScreenUtils.encoderCandidates(applicationContext, settings.resolution)
        val target = candidates.firstOrNull() ?: (1280 to 720)
        val fallback = candidates.drop(1)
        val request = StreamEngine.PrepareRequest(
            projectionResultCode = resultCode,
            projectionData = data,
            targetWidth = target.first,
            targetHeight = target.second,
            fps = settings.fps,
            bitrateBps = settings.bitrateBps,
            transport = settings.transport,
            user = settings.rtspUser,
            password = settings.rtspPassword,
            fallbackSizes = fallback,
        )
        when (val result = engine.prepare(request)) {
            is StreamEngine.PrepareResult.Ok -> {
                lastAppliedFps = settings.fps
                lastAppliedBitrate = settings.bitrateBps
                transition(StreamEvent.PrepareSuccess)
                startForegroundNotification(getString(R.string.status_connecting))
                manualStop = false
                _reconnectAttempts.value = 0
                engine.startStream(settings.serverUrl)
            }
            is StreamEngine.PrepareResult.Failed -> {
                _lastError.value = LogSanitizer.sanitize(result.reason)
                transition(StreamEvent.PrepareFailure(_lastError.value!!))
                stopForegroundAndSelf()
            }
        }
    }

    private fun handleStopRequested() {
        manualStop = true
        reconnectJob?.cancel()
        reconnectJob = null
        transition(StreamEvent.RequestStop)
        try { engine.stopStream() } catch (t: Throwable) {
            Log.w(TAG, "stopStream falhou: ${LogSanitizer.sanitize(t.message)}")
        }
        transition(StreamEvent.StoppedCleanly)
        stopForegroundAndSelf()
    }

    private fun stopForegroundAndSelf() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val landscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE ||
            newConfig.orientation == Configuration.ORIENTATION_UNDEFINED
        engine.onOrientationChanged(landscape)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (stopOnTaskRemoved) {
            handleStopRequested()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        reconnectJob?.cancel()
        try { engine.release() } catch (_: Throwable) {}
        scope.cancel()
        if (_state.value !is StreamState.Idle) transition(StreamEvent.StoppedCleanly)
    }

    // ---- StreamEngine.Listener ----

    override fun onConnecting() {
        // Sinal do próprio motor — mantém o serviço em Connecting.
        updateNotification(getString(R.string.status_connecting))
    }

    override fun onConnected(fps: Int, bitrateBps: Int) {
        streamingStartedAt = System.currentTimeMillis()
        _reconnectAttempts.value = 0
        transition(
            StreamEvent.ConnectionSuccess(
                startedAtMillis = streamingStartedAt,
                fps = fps,
                bitrateBps = bitrateBps,
            )
        )
        updateNotification(getString(R.string.status_streaming))
    }

    override fun onDisconnected() {
        if (manualStop) return
        // A biblioteca desconectou — reconexão é feita aqui, com backoff próprio.
    }

    override fun onConnectionFailed(reason: String) {
        _lastError.value = LogSanitizer.sanitize(reason)
        if (manualStop) return
        scheduleReconnect()
    }

    override fun onAuthError() {
        _lastError.value = "Falha de autenticação RTSP. Confira usuário e senha."
        manualStop = true
        transition(StreamEvent.FatalError(_lastError.value!!))
        try { engine.stopStream() } catch (_: Throwable) {}
        stopForegroundAndSelf()
    }

    override fun onBitrateSample(bps: Long) {
        _statsBps.value = bps
    }

    // ---- reconnect ----

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val attempt = _reconnectAttempts.value + 1
            _reconnectAttempts.value = attempt
            val delayMs = Backoff.delayMs(attempt)
            transition(StreamEvent.ConnectionFailure(attempt = attempt, nextDelayMs = delayMs))
            updateNotification("${getString(R.string.status_reconnecting)} #$attempt")
            delay(delayMs)
            if (manualStop) return@launch
            val settings = settingsRepo.settings.first()
            engine.startStream(settings.serverUrl)
        }
    }

    private fun transition(event: StreamEvent) {
        val next = StreamTransitions.next(_state.value, event)
        _state.value = next
    }

    private fun isLikelyLan(url: String): Boolean {
        // Heurística simples pra permitir servir sem "internet validada":
        // se o host aparenta ser IP privado, tolera transmissão em LAN.
        val host = url.substringAfter("://").substringBefore('/').substringAfter('@').substringBeforeLast(':')
        return host.startsWith("10.") ||
            host.startsWith("192.168.") ||
            host.startsWith("172.") ||
            host == "localhost" ||
            host.startsWith("127.")
    }

    // ---- notificação ----

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        }
    }

    private fun buildNotification(text: String): android.app.Notification {
        val stopIntent = Intent(this, StreamService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.notif_stop),
                stopPending,
            )
            .build()
    }

    private fun startForegroundNotification(text: String) {
        val n = buildNotification(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }
}
