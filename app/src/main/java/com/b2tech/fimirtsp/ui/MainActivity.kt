package com.b2tech.fimirtsp.ui

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import com.b2tech.fimirtsp.streaming.ProjectionController
import com.b2tech.fimirtsp.ui.theme.FimiTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels(factoryProducer = { MainViewModel.Factory })
    private val projection by lazy { ProjectionController(applicationContext) }

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onProjectionResult(application, result.resultCode, result.data)
        if (result.resultCode != RESULT_OK) {
            toast("Captura de tela negada")
        }
    }

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        maybeRequestNotifPermission()

        setContent {
            FimiTheme {
                val uiState by viewModel.state.collectAsState()
                val candidates = remember { FimiLauncher.findCandidates(this@MainActivity) }
                MainScreen(
                    state = uiState,
                    onUrlChange = viewModel::updateUrl,
                    onUserChange = viewModel::updateUser,
                    onPasswordChange = viewModel::updatePassword,
                    onResolutionChange = viewModel::updateResolution,
                    onFpsChange = viewModel::updateFps,
                    onBitrateChange = viewModel::updateBitrateBps,
                    onTransportChange = viewModel::updateTransport,
                    onVolumeUp = viewModel::raiseVolume,
                    onVolumeDown = viewModel::lowerVolume,
                    onVolumePercent = viewModel::setVolumePercent,
                    onVolumeStepChange = viewModel::updateVolumeStepPercent,
                    onVolumeKeysToggle = viewModel::updateVolumeKeysEnabled,
                    onAuthorizeAndStart = { authorizeAndStart(uiState.urlIsValid) },
                    onStop = { viewModel.stopStreaming(application) },
                    onOpenFimi = { openFimi(candidates) },
                    fimiFound = candidates.isNotEmpty(),
                    olderAndroid = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                )
            }
        }
    }

    /**
     * Teclas físicas viram o comando de volume do app (passo configurável) enquanto esta
     * tela está na frente. Com o FIMI Navi em primeiro plano quem manda é o sistema — aí
     * o caminho são os botões Vol +/− da notificação da transmissão.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!interceptsVolumeKey(keyCode)) return super.onKeyDown(keyCode, event)
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) viewModel.raiseVolume() else viewModel.lowerVolume()
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        // Consome o "soltar": sem isso o sistema ainda aplicaria o passo padrão dele por cima.
        if (interceptsVolumeKey(keyCode)) return true
        return super.onKeyUp(keyCode, event)
    }

    private fun interceptsVolumeKey(keyCode: Int): Boolean {
        val isVolumeKey = keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        return isVolumeKey && viewModel.volumeKeysEnabled()
    }

    private fun authorizeAndStart(urlIsValid: Boolean) {
        if (!urlIsValid) {
            toast("Endereço inválido. Use rtsp:// ou rtmp:// e corrija antes de iniciar.")
            return
        }
        viewModel.markAwaitingProjection(true)
        try {
            projectionLauncher.launch(projection.createRequestIntent())
        } catch (t: Throwable) {
            viewModel.markAwaitingProjection(false)
            toast("Não foi possível pedir captura: ${t.message}")
        }
    }

    private fun openFimi(candidates: List<FimiLauncher.Candidate>) {
        when {
            candidates.isEmpty() -> toast(getString(com.b2tech.fimirtsp.R.string.fimi_not_found))
            candidates.size == 1 -> launchCandidate(candidates.first())
            else -> {
                val labels = candidates.map { it.label }.toTypedArray()
                AlertDialog.Builder(this)
                    .setTitle("Qual FIMI Navi abrir?")
                    .setItems(labels) { _, which -> launchCandidate(candidates[which]) }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        }
    }

    private fun launchCandidate(candidate: FimiLauncher.Candidate) {
        val intent = FimiLauncher.launchIntentFor(this, candidate)
        if (intent == null) {
            toast("Não consegui abrir ${candidate.label}")
            return
        }
        try {
            startActivity(intent)
        } catch (t: Throwable) {
            toast("Erro ao abrir ${candidate.label}: ${t.message}")
        }
    }

    private fun maybeRequestNotifPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
