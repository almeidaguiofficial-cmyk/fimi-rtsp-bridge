package com.b2tech.fimirtsp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.b2tech.fimirtsp.settings.QualityPreset
import com.b2tech.fimirtsp.settings.RtspTransport
import com.b2tech.fimirtsp.settings.StreamSettings
import com.b2tech.fimirtsp.settings.VideoCodecPref
import com.b2tech.fimirtsp.settings.VideoResolution
import com.b2tech.fimirtsp.streaming.BitrateMode
import com.b2tech.fimirtsp.streaming.CodecPlan
import com.b2tech.fimirtsp.streaming.EncoderChoice
import com.b2tech.fimirtsp.streaming.StreamState
import com.b2tech.fimirtsp.validation.StreamUrlValidator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: UiState,
    onUrlChange: (String) -> Unit,
    onUserChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onResolutionChange: (VideoResolution) -> Unit,
    onFpsChange: (Int) -> Unit,
    onBitrateChange: (Int) -> Unit,
    onApplyMaxQuality: () -> Unit,
    onCodecChange: (VideoCodecPref) -> Unit,
    onTransportChange: (RtspTransport) -> Unit,
    onAuthorizeAndStart: () -> Unit,
    onStop: () -> Unit,
    onOpenFimi: () -> Unit,
    fimiFound: Boolean,
    olderAndroid: Boolean,
) {
    val engineState = state.engineState
    val streaming = engineState is StreamState.Streaming
    val reconnecting = engineState is StreamState.Reconnecting
    val busy = engineState !is StreamState.Idle && engineState !is StreamState.Error

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text("FIMI RTSP Bridge") })
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
            StatusCard(
                state = engineState,
                bitrateBps = state.bitrateSampleBps,
                reconnectAttempts = state.reconnectAttempts,
                activeCodec = state.activeCodec,
                connectivityLabel = connectivityLabel(state),
            )

            if (olderAndroid) InfoBanner("Android antigo: a captura pega a tela inteira.")
            else InfoBanner("Android 14+: você pode escolher só a janela do FIMI Navi na hora de autorizar.")

            ServerCard(
                settings = state.settings,
                urlError = state.urlError,
                onUrlChange = onUrlChange,
                onUserChange = onUserChange,
                onPasswordChange = onPasswordChange,
                onTransportChange = onTransportChange,
                enabled = !busy,
            )

            EncodingCard(
                settings = state.settings,
                encoderChoices = state.encoderChoices,
                onResolutionChange = onResolutionChange,
                onFpsChange = onFpsChange,
                onBitrateChange = onBitrateChange,
                onApplyMaxQuality = onApplyMaxQuality,
                onCodecChange = onCodecChange,
                enabled = !busy,
            )

            ActionsCard(
                canStart = state.urlIsValid && !busy,
                streaming = streaming,
                reconnecting = reconnecting,
                onAuthorizeAndStart = onAuthorizeAndStart,
                onStop = onStop,
                onOpenFimi = onOpenFimi,
                fimiFound = fimiFound,
            )

            SecurityFooter()
        }
    }
}

@Composable
private fun StatusCard(
    state: StreamState,
    bitrateBps: Long,
    reconnectAttempts: Int,
    activeCodec: VideoCodecPref?,
    connectivityLabel: String,
) {
    val label = when (state) {
        is StreamState.Idle -> "Parado"
        is StreamState.AwaitingPermission -> "Aguardando autorização"
        is StreamState.Preparing -> "Preparando"
        is StreamState.Connecting -> "Conectando"
        is StreamState.Streaming -> "● Transmitindo"
        is StreamState.Reconnecting -> "Reconectando (tentativa ${state.attempt})"
        is StreamState.Stopping -> "Encerrando"
        is StreamState.Error -> "Erro"
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = when (state) {
                is StreamState.Streaming -> MaterialTheme.colorScheme.tertiaryContainer
                is StreamState.Error -> MaterialTheme.colorScheme.errorContainer
                is StreamState.Reconnecting -> MaterialTheme.colorScheme.secondaryContainer
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
            when (state) {
                is StreamState.Streaming -> {
                    val secs = ((System.currentTimeMillis() - state.startedAtMillis) / 1000).coerceAtLeast(0)
                    Text("Tempo: ${formatDuration(secs)}  •  FPS alvo: ${state.fps}  •  Alvo: ${state.bitrateBps / 1_000_000.0} Mbps")
                    Text("Bitrate agora: ${bitrateBps / 1000} kbps")
                    if (activeCodec != null) Text("Codec: ${activeCodec.label}")
                }
                is StreamState.Reconnecting -> Text("Próxima tentativa em ${state.nextDelayMs / 1000}s")
                is StreamState.Error -> {
                    Text(state.message, style = MaterialTheme.typography.bodyMedium)
                    Text("Toque em Autorizar captura e iniciar para tentar de novo.", style = MaterialTheme.typography.bodySmall)
                }
                else -> Unit
            }
            Text("Rede: $connectivityLabel", style = MaterialTheme.typography.bodySmall)
            if (reconnectAttempts > 0 && state is StreamState.Streaming) {
                Text("Reconexões nesta sessão: $reconnectAttempts", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ServerCard(
    settings: StreamSettings,
    urlError: String?,
    onUrlChange: (String) -> Unit,
    onUserChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTransportChange: (RtspTransport) -> Unit,
    enabled: Boolean,
) {
    var showPassword by remember { mutableStateOf(false) }
    val family = StreamUrlValidator.validate(settings.serverUrl).family
    SectionCard(title = "Servidor de destino (RTSP ou RTMP)") {
        OutlinedTextField(
            value = settings.serverUrl,
            onValueChange = onUrlChange,
            enabled = enabled,
            label = { Text("Endereço do servidor") },
            placeholder = { Text("rtsp://192.168.1.100:8554/fimi") },
            isError = urlError != null && settings.serverUrl.isNotEmpty(),
            supportingText = {
                Text(
                    urlError
                        ?: "Ex.: rtsp://IP:8554/fimi  ou  rtmp://servidor:1935/live/ID?key=..."
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = settings.rtspUser,
            onValueChange = onUserChange,
            enabled = enabled,
            label = { Text("Usuário (opcional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = settings.rtspPassword,
            onValueChange = onPasswordChange,
            enabled = enabled,
            label = { Text("Senha (opcional)") },
            singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    val icon = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                    Icon(icon, contentDescription = "Alternar visualização da senha")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        when (family) {
            StreamUrlValidator.Family.RTMP -> Text(
                "RTMP: transporte sempre TCP. Se o servidor exigir chave, ela vai na própria URL (?key=...).",
                style = MaterialTheme.typography.bodySmall,
            )
            else -> {
                Text("Transporte RTSP", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = settings.transport == RtspTransport.TCP,
                        onClick = { onTransportChange(RtspTransport.TCP) },
                        enabled = enabled,
                        label = { Text("TCP (padrão)") },
                    )
                    FilterChip(
                        selected = settings.transport == RtspTransport.UDP,
                        onClick = { onTransportChange(RtspTransport.UDP) },
                        enabled = enabled,
                        label = { Text("UDP") },
                    )
                }
            }
        }
    }
}

@Composable
private fun EncodingCard(
    settings: StreamSettings,
    encoderChoices: Map<VideoCodecPref, EncoderChoice?>?,
    onResolutionChange: (VideoResolution) -> Unit,
    onFpsChange: (Int) -> Unit,
    onBitrateChange: (Int) -> Unit,
    onApplyMaxQuality: () -> Unit,
    onCodecChange: (VideoCodecPref) -> Unit,
    enabled: Boolean,
) {
    SectionCard(title = "Codificação") {
        if (QualityPreset.isActive(settings)) {
            Text(
                "✓ Qualidade máxima: 1080p • 30 fps • 8 Mbps CBR • H.265",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Button(
                onClick = onApplyMaxQuality,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Usar qualidade máxima (1080p • 30 fps • 8 Mbps CBR • H.265)")
            }
        }
        Text(
            "8 Mbps constantes pedem upload estável acima de 10 Mbps. Se travar no 4G, baixe o bitrate.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text("Resolução", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VideoResolution.entries.forEach { res ->
                FilterChip(
                    selected = settings.resolution == res,
                    onClick = { onResolutionChange(res) },
                    enabled = enabled,
                    label = { Text(res.label) },
                )
            }
        }
        Text("FPS: ${settings.fps}", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15, 24, 30).forEach { fps ->
                FilterChip(
                    selected = settings.fps == fps,
                    onClick = { onFpsChange(fps) },
                    enabled = enabled,
                    label = { Text("$fps") },
                )
            }
        }
        Text(
            "Bitrate: %.1f Mbps".format(settings.bitrateBps / 1_000_000.0),
            style = MaterialTheme.typography.labelLarge,
        )
        Slider(
            value = settings.bitrateBps / 1_000_000f,
            onValueChange = { v -> onBitrateChange((v * 1_000_000).toInt()) },
            valueRange = 0.5f..12f,
            steps = 22,
            enabled = enabled,
        )
        Text("Codec", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VideoCodecPref.entries.forEach { codec ->
                FilterChip(
                    selected = settings.videoCodec == codec,
                    onClick = { onCodecChange(codec) },
                    enabled = enabled,
                    label = { Text(codec.label) },
                )
            }
        }
        if (settings.videoCodec == VideoCodecPref.H265) {
            Text(
                "H.265 entrega imagem melhor no mesmo bitrate. Por RTMP o servidor precisa " +
                    "aceitar Enhanced RTMP (YouTube e MediaMTX recentes aceitam; Facebook e " +
                    "servidores antigos recusam). Se não conectar, volte para H.264. " +
                    "A aba Assistir só abre H.265 por RTSP.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (encoderChoices != null) {
            val expected = CodecPlan.expected(
                settings.videoCodec,
                hevcAvailable = encoderChoices[VideoCodecPref.H265] != null,
            )
            if (expected != settings.videoCodec) {
                Text(
                    "⚠ Este aparelho não tem encoder ${settings.videoCodec.label}: " +
                        "a transmissão vai sair em ${expected.label}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            encoderChoices[expected]?.let { BitrateModeNote(it, expected) }
        }
    }
}

@Composable
private fun BitrateModeNote(choice: EncoderChoice, codec: VideoCodecPref) {
    when {
        choice.mode == BitrateMode.VBR -> Text(
            "⚠ Este aparelho não tem encoder ${codec.label} com bitrate constante (CBR): " +
                "a taxa vai variar com a cena. Encoder: ${choice.name}" +
                if (codec == VideoCodecPref.H265) ". Para manter CBR, use H.264." else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        choice.hardware -> Text(
            "Bitrate constante (CBR) • encoder de hardware ${choice.name}",
            style = MaterialTheme.typography.bodySmall,
        )
        else -> Text(
            "Bitrate constante (CBR) via encoder de software (${choice.name}). " +
                "É mais pesado: se a imagem engasgar, use 1280×720.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ActionsCard(
    canStart: Boolean,
    streaming: Boolean,
    reconnecting: Boolean,
    onAuthorizeAndStart: () -> Unit,
    onStop: () -> Unit,
    onOpenFimi: () -> Unit,
    fimiFound: Boolean,
) {
    SectionCard(title = "Controle") {
        Button(
            onClick = onAuthorizeAndStart,
            enabled = canStart,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (streaming || reconnecting) "Autorizar nova sessão de captura" else "Autorizar captura e iniciar")
        }
        Button(
            onClick = onStop,
            enabled = streaming || reconnecting,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Parar transmissão")
        }
        OutlinedButton(
            onClick = onOpenFimi,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (fimiFound) "Abrir FIMI Navi" else "Abrir aplicativo do drone")
        }
        if (!fimiFound) {
            Text(
                "Não encontrei o app da FIMI. A transmissão funciona mesmo assim, com qualquer modelo: " +
                    "inicie aqui e abra o app do seu drone pela tela inicial.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
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
private fun InfoBanner(text: String) {
    AssistChip(
        onClick = { },
        label = { Text(text) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    )
}

@Composable
private fun SecurityFooter() {
    Spacer(Modifier.height(4.dp))
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Segurança", style = MaterialTheme.typography.labelLarge)
            Text(
                "RTSP e RTMP simples não são criptografados. Use VPN, rede privada, rtsps:// ou rtmps:// quando o servidor oferecer.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Este app não controla o drone e não modifica o FIMI Navi. Ele só espelha a tela e publica no servidor.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Por isso funciona com qualquer modelo FIMI (X8 Pro, X8 SE, X8 Mini, Tele Max) e com app de drone de outra marca.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun connectivityLabel(state: UiState): String {
    val c = state.connectivity
    val transport = when (c.transport) {
        com.b2tech.fimirtsp.network.Connectivity.Transport.WIFI -> "Wi-Fi"
        com.b2tech.fimirtsp.network.Connectivity.Transport.CELLULAR -> "4G/5G"
        com.b2tech.fimirtsp.network.Connectivity.Transport.ETHERNET -> "Ethernet"
        com.b2tech.fimirtsp.network.Connectivity.Transport.USB_OR_OTHER -> "outra"
        com.b2tech.fimirtsp.network.Connectivity.Transport.NONE -> "nenhuma"
    }
    val internet = if (c.hasInternet) "com internet" else "sem internet validada"
    return "$transport — $internet"
}

private fun formatDuration(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
