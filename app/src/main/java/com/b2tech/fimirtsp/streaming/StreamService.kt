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
import com.b2tech.fimirtsp.network.ConnectivityMonitor
import com.b2tech.fimirtsp.settings.SettingsRepository
import com.b2tech.fimirtsp.settings.StreamSettings
import com.b2tech.fimirtsp.util.LogSanitizer
import com.b2tech.fimirtsp.util.ScreenUtils
import com.b2tech.fimirtsp.validation.StreamUrlValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

        /** Razões (RootEncoder) que não adianta reconectar — o servidor recusou de vez. */
        private val FATAL_REASONS = listOf(
            "access denied",
            "Publish.BadName",
            "Connect.Rejected",
            "Publish.Failed",
            "Unsupported protocol",
            "Endpoint malformed",
            // SRT: passphrase errada ou ausente — repetir o handshake não muda a resposta.
            "SRT_REJ_BADSECRET",
            "SRT_REJ_UNSECURE",
        )

        private val _state: MutableStateFlow<StreamState> = MutableStateFlow(StreamState.Idle)
        val state: StateFlow<StreamState> = _state.asStateFlow()

        private val _lastError: MutableStateFlow<String?> = MutableStateFlow(null)
        val lastError: StateFlow<String?> = _lastError.asStateFlow()

        private val _statsBps: MutableStateFlow<Long> = MutableStateFlow(0L)
        val statsBps: StateFlow<Long> = _statsBps.asStateFlow()

        private val _reconnectAttempts: MutableStateFlow<Int> = MutableStateFlow(0)
        val reconnectAttempts: StateFlow<Int> = _reconnectAttempts.asStateFlow()

        private val _netStats: MutableStateFlow<NetworkStats?> = MutableStateFlow(null)
        val netStats: StateFlow<NetworkStats?> = _netStats.asStateFlow()

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
    @Volatile private var manualStop = false
    @Volatile private var retryPending = false
    private var streamingStartedAt = 0L
    private var stopOnTaskRemoved = true
    @Volatile private var lastNotificationText: String? = null

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
        // Sessão nova em cima de uma antiga (ex.: usuário autorizou de novo sem parar):
        // fecha o ciclo anterior para a máquina de estados aceitar RequestAuthorize.
        if (_state.value !is StreamState.Idle && _state.value !is StreamState.Error) {
            manualStop = true
            try { engine.stopStream() } catch (_: Throwable) {}
            transition(StreamEvent.RequestStop)
            transition(StreamEvent.StoppedCleanly)
        }
        manualStop = false
        retryPending = false
        _netStats.value = null
        transition(StreamEvent.RequestAuthorize)
        transition(StreamEvent.AuthorizationGranted)

        scope.launch {
            val settings = settingsRepo.settings.first()
            stopOnTaskRemoved = settings.stopOnTaskRemoved
            val validation = StreamUrlValidator.validate(settings.serverUrl)
            if (!validation.ok) {
                _lastError.value = "Endereço inválido: ${validation.error}"
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
            adaptToNetwork = settings.adaptToNetwork,
        )
        when (val result = engine.prepare(request)) {
            is StreamEngine.PrepareResult.Ok -> {
                transition(StreamEvent.PrepareSuccess)
                startForegroundNotification(getString(R.string.status_connecting))
                manualStop = false
                _reconnectAttempts.value = 0
                if (!engine.startStream(settings.serverUrl)) {
                    // startStream já desfez a captura e reportou a razão em _lastError.
                    if (_lastError.value.isNullOrBlank()) {
                        _lastError.value = "Não foi possível iniciar a captura/encoder"
                    }
                    manualStop = true
                    transition(StreamEvent.FatalError(_lastError.value!!))
                    stopForegroundAndSelf()
                }
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
        manualStop = true
        engine.setListener(null)
        try { engine.release() } catch (_: Throwable) {}
        // Callback atrasado da lib não pode deixar notificação "Parar" zumbi.
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIF_ID)
        scope.cancel()
        if (_state.value !is StreamState.Idle && _state.value !is StreamState.Error) {
            transition(StreamEvent.StoppedCleanly)
        }
    }

    // ---- StreamEngine.Listener (chegam na thread da lib; StateFlow e
    // NotificationManager são thread-safe, e manualStop cerca callbacks tardios) ----

    override fun onConnecting() {
        if (manualStop) return
        retryPending = false
        updateNotification(getString(R.string.status_connecting))
    }

    override fun onConnected(fps: Int, bitrateBps: Int) {
        if (manualStop) return
        retryPending = false
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
        // A lib só recusa reTry para "Endpoint malformed"; recusas permanentes do servidor
        // (403, BadName, Rejected...) voltariam true e virariam loop infinito de reconexão.
        if (FATAL_REASONS.any { reason.contains(it, ignoreCase = true) }) {
            failFatally("Servidor recusou a publicação: ${_lastError.value}")
            return
        }
        scheduleReconnect(reason)
    }

    override fun onAuthError() {
        // No RTMP a lib também cai aqui para qualquer _error do connect que não reconhece.
        failFatally("Servidor recusou a conexão (autenticação, key na URL ou rota). Confira a URL e as credenciais.")
    }

    override fun onCaptureStopped() {
        failFatally("Captura de tela encerrada pelo sistema. Autorize de novo para continuar.")
    }

    override fun onBitrateSample(bps: Long) {
        if (manualStop) return
        _statsBps.value = bps
    }

    override fun onNetworkStats(stats: NetworkStats) {
        if (manualStop) return
        _netStats.value = stats
        // Durante o voo o FIMI Navi fica na frente: a notificação é o único lugar onde o piloto
        // vê que a rede piorou. Só reescreve quando o texto muda, não a cada segundo.
        if (_state.value !is StreamState.Streaming) return
        val text = getString(
            when (stats.quality) {
                LinkQuality.UNSTABLE -> R.string.status_streaming_unstable
                LinkQuality.BAD -> R.string.status_streaming_bad
                LinkQuality.GOOD, LinkQuality.UNKNOWN -> R.string.status_streaming
            }
        )
        if (text != lastNotificationText) updateNotification(text)
    }

    private fun failFatally(message: String) {
        if (manualStop) return
        manualStop = true
        _lastError.value = message
        transition(StreamEvent.FatalError(message))
        try { engine.stopStream() } catch (_: Throwable) {}
        stopForegroundAndSelf()
    }

    // ---- reconnect ----

    /**
     * Chamado na thread da biblioteca. O reTry da RootEncoder mantém encoder e captura
     * vivos, espera [delayMs] e reconecta o socket; startStream de novo lançaria
     * IllegalStateException ("Stream already started").
     */
    private fun scheduleReconnect(reason: String) {
        // A lib pode entregar dois onConnectionFailed para a mesma queda (leitor + sender);
        // dois reTry concorrentes agendariam dois reconnects no mesmo socket.
        if (retryPending) return
        retryPending = true
        val attempt = _reconnectAttempts.value + 1
        _reconnectAttempts.value = attempt
        val delayMs = Backoff.delayMs(attempt)
        transition(StreamEvent.ConnectionFailure(attempt = attempt, nextDelayMs = delayMs))
        updateNotification("${getString(R.string.status_reconnecting)} #$attempt")
        val willRetry = try {
            engine.retry(delayMs, reason)
        } catch (t: Throwable) {
            Log.w(TAG, "retry falhou: ${LogSanitizer.sanitize(t.message)}")
            false
        }
        if (!willRetry) {
            retryPending = false
            failFatally("Servidor recusou a reconexão: ${LogSanitizer.sanitize(reason)}")
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
        lastNotificationText = text
        val n = buildNotification(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun updateNotification(text: String) {
        lastNotificationText = text
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }
}
