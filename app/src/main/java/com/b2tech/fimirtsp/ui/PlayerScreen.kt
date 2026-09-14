package com.b2tech.fimirtsp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.b2tech.fimirtsp.player.PlayerState
import kotlinx.coroutines.delay

/**
 * Aba Assistir: reproduz o feed publicado (rtsp:// ou rtmp://) em tempo real.
 * O player pertence ao PlayerViewModel; esta tela só exibe e comanda.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    state: PlayerUiState,
    player: ExoPlayer?,
    onUrlChange: (String) -> Unit,
    onAutoReconnectChange: (Boolean) -> Unit,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onRetryNow: () -> Unit,
) {
    // Sair da aba para e libera o player (vídeo de monitoramento, nada em background).
    DisposableEffect(Unit) {
        onDispose { onStop() }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text("Assistir ao vivo") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PlayerStatusCard(state)
            VideoSurface(state, player)
            PlayerSourceCard(
                state = state,
                onUrlChange = onUrlChange,
                onAutoReconnectChange = onAutoReconnectChange,
            )
            PlayerActionsCard(
                state = state,
                onPlay = onPlay,
                onStop = onStop,
                onRetryNow = onRetryNow,
            )
            LatencyFooter()
        }
    }
}

@Composable
private fun PlayerStatusCard(state: PlayerUiState) {
    val st = state.state
    // Relógio de 1s pro tempo de sessão enquanto toca.
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val playing = st is PlayerState.Playing
    LaunchedEffect(playing) {
        while (playing) {
            nowMs = System.currentTimeMillis()
            delay(1_000)
        }
    }

    val label = when (st) {
        is PlayerState.Idle -> "Parado"
        is PlayerState.Connecting -> "Conectando"
        is PlayerState.Buffering -> "Carregando"
        is PlayerState.Playing -> "● Ao vivo"
        is PlayerState.Reconnecting -> "Reconectando (tentativa ${st.attempt})"
        is PlayerState.Ended -> "Transmissão encerrada"
        is PlayerState.Error -> "Erro"
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = when (st) {
                is PlayerState.Playing -> MaterialTheme.colorScheme.tertiaryContainer
                is PlayerState.Error -> MaterialTheme.colorScheme.errorContainer
                is PlayerState.Reconnecting -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            when (st) {
                is PlayerState.Playing -> {
                    val secs = ((nowMs - st.startedAtMillis) / 1000).coerceAtLeast(0)
                    Text("Tempo assistindo: ${formatPlayerDuration(secs)}")
                }
                is PlayerState.Reconnecting -> {
                    Text("Próxima tentativa em ${st.nextDelayMs / 1000}s")
                    if (state.consecutiveFailures >= 5) {
                        Text(
                            "Várias falhas seguidas — confira endereço, porta e credenciais, e se a transmissão já começou.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                is PlayerState.Ended -> Text(
                    "O servidor encerrou a transmissão. Toque em Assistir para tentar de novo.",
                    style = MaterialTheme.typography.bodySmall,
                )
                is PlayerState.Error -> {
                    Text(st.message, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Corrija o endereço se for o caso e toque em Assistir.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                else -> Unit
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun VideoSurface(state: PlayerUiState, player: ExoPlayer?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { view ->
                view.player = player
                // Mantém a tela acesa só enquanto há sessão ativa; limpa sozinho
                // quando a aba sai de composição.
                view.keepScreenOn = state.active
            },
            modifier = Modifier.fillMaxSize(),
        )
        when (state.state) {
            is PlayerState.Connecting, is PlayerState.Buffering, is PlayerState.Reconnecting ->
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            is PlayerState.Playing -> Text(
                "AO VIVO",
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .background(Color(0xCCB00020), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
            is PlayerState.Idle -> Text(
                "Sem vídeo",
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.Center),
            )
            else -> Unit
        }
    }
}

@Composable
private fun PlayerSourceCard(
    state: PlayerUiState,
    onUrlChange: (String) -> Unit,
    onAutoReconnectChange: (Boolean) -> Unit,
) {
    val editable = !state.active
    PlayerSectionCard(title = "Fonte do vídeo (RTSP ou RTMP)") {
        OutlinedTextField(
            value = state.playerUrl,
            onValueChange = onUrlChange,
            enabled = editable,
            label = { Text("Endereço da transmissão") },
            placeholder = { Text("rtsp://reader:senha@192.168.1.100:8554/fimi") },
            isError = state.urlError != null && state.playerUrl.isNotEmpty(),
            supportingText = {
                Text(
                    state.urlError
                        ?: "Ex.: rtsp://reader:senha@IP:8554/fimi  ou  rtmp://IP:1935/fimi?user=reader&pass=senha"
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.weight(1f)) {
                Text("Reconectar automaticamente", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Continua tentando enquanto o feed do drone não sobe (e religa se cair).",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(checked = state.autoReconnect, onCheckedChange = onAutoReconnectChange)
        }
    }
}

@Composable
private fun PlayerActionsCard(
    state: PlayerUiState,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onRetryNow: () -> Unit,
) {
    PlayerSectionCard(title = "Controle") {
        Button(
            onClick = onPlay,
            enabled = state.canPlay,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Assistir")
        }
        Button(
            onClick = onStop,
            enabled = state.active,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Parar reprodução")
        }
        if (state.state is PlayerState.Reconnecting) {
            OutlinedButton(
                onClick = onRetryNow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Tentar agora")
            }
        }
    }
}

@Composable
private fun PlayerSectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun LatencyFooter() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Latência", style = MaterialTheme.typography.labelLarge)
            Text(
                "Na rede local, rtsp:// fica em ~0,5–1 s de atraso e rtmp:// em 1–3 s. " +
                    "Pra menos atraso ainda, abra http://SERVIDOR:8889/fimi (WebRTC) num navegador.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "O reprodutor não suporta rtsps:// nem rtmps:// — fora da LAN, use VPN.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun formatPlayerDuration(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
