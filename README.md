# FIMI RTSP Bridge

App Android que espelha a tela do celular durante o uso do **FIMI Navi 3.0** e
publica o vídeo por **RTSP ou RTMP** para um servidor (MediaMTX, Skywatch,
OBS Studio como cliente, NVR, etc.). Serve como ponte pra sacar do FIMI Navi um
sinal ao vivo que outras ferramentas conseguem ler — VLC, OBS, câmeras IP
virtuais, análise de vídeo.

Testado com o padrão de captura já em produção no Skywatch Cast (RootEncoder 2.8.1).

## O que ele faz

- Pega a tela do celular (ou só a janela do FIMI Navi no Android 14+).
- Codifica em **H.264 por hardware**, GOP de 1s, sem B-frames, sem áudio.
- Publica em `rtsp://SERVIDOR:8554/fimi` **ou** `rtmp://SERVIDOR:1935/live/ID?key=...`
  (o app escolhe RTSP/RTMP pelo esquema da URL; `rtsps://` e `rtmps://` também valem).
- Roda em **Foreground Service tipo mediaProjection**, notificação persistente
  com botões **Vol −**, **Vol +** e “Parar transmissão”.
- **Comando de volume**: sobe/desce o volume de mídia do aparelho por passo
  configurável — pela tela, pelas teclas físicas e pela notificação (ver seção
  própria abaixo).
- Reconecta automaticamente com backoff 1s → 2s → 4s → 8s → 15s (saturado),
  mantendo encoder e captura vivos (`reTry` da RootEncoder) — sem pedir nova
  autorização de captura a cada queda.
- RTSP: transporte **TCP** (padrão) ou **UDP**. RTMP: sempre TCP.
- Aceita usuário/senha (Basic/Digest no RTSP; auth RTMP, o que a lib negociar).
- Sem áudio de verdade: o cliente é configurado como *only video*, então o servidor
  não recebe uma faixa de áudio silenciosa (evita travar HLS no MediaMTX).
- Valida a URL antes de iniciar; nunca escreve senha em log.

## O que ele NÃO faz

- Não controla o drone.
- Não modifica o APK do FIMI Navi nem o firmware do drone.
- Não usa root, Accessibility Service, mods do sistema.
- Não faz automação de toques nem simula toques.
- Não bind‐eia o socket em nenhuma rede específica (mais adiante em
  “diagnóstico de rede”).
- Não expõe portas na internet automaticamente.
- O comando de volume **não** mexe no volume da transmissão: não existe faixa de
  áudio no stream. Ele controla o alto-falante do celular.

## Requisitos

- Android 9 (API 28) ou superior — o app tira melhor proveito do Android 14+,
  que deixa você escolher apenas a janela do FIMI Navi na tela de consentimento
  do MediaProjection.
- FIMI Navi 3.0 instalado e funcional com o drone/controle.
- Servidor RTSP alcançável — recomendamos rodar o MediaMTX do `server/`.

## Como compilar

### Opção A — pelo GitHub Actions (recomendado)

O workflow `.github/workflows/build.yml` já:

1. Configura JDK 17, Android SDK 35 e Gradle 8.10.2 (chama `gradle` direto,
   sem depender do `gradle-wrapper.jar`, que não é versionado).
2. Roda `testDebugUnitTest` e `lintDebug`.
3. Roda `assembleDebug`.
4. Publica o APK em **Actions → Artifacts** (`fimi-rtsp-bridge-debug`) e
   também na release **`latest`** do repositório — dá pra baixar direto, sem login:
   `https://github.com/<usuario>/<repo>/releases/download/latest/app-debug.apk`
5. Se o build falhar, o log completo do Gradle sobe na release `buildlog`
   (`releases/download/buildlog/build.log`) — útil quando não se tem `gh` na máquina.

Após dar push na branch `main`, aguarde o job e baixe o APK.

> Kotlin fixo em **2.3.21**: a RootEncoder 2.8.1 publicada no JitPack arrasta
> `kotlin-stdlib` 2.3.x, e Kotlin 2.0.x falha em `compileDebugKotlin` com
> "incompatible metadata".

### Opção B — localmente

Você precisa de:

- **JDK 17** (Temurin/Corretto/Zulu).
- **Android SDK** com plataformas 35 e build-tools mais recentes (baixe pelo
  Android Studio ou `cmdline-tools`).
- **Gradle 8.10.2+** (o wrapper só funciona depois de `gradle wrapper`).

Passos:

```powershell
# 1. Gerar o gradle-wrapper.jar (não versionamos o binário)
gradle wrapper --gradle-version 8.10.2 --distribution-type bin

# 2. Compilar debug
./gradlew :app:assembleDebug

# 3. Testes
./gradlew testDebugUnitTest
```

O APK sai em `app/build/outputs/apk/debug/app-debug.apk`.

## Como instalar

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Ou envie o APK pro celular e abra o arquivo — o Android pedirá pra permitir
instalação de fonte desconhecida.

## Servidor de teste (MediaMTX)

Veja [`server/README.md`](server/README.md). Resumo:

```bash
cd server
docker compose up -d
```

## Como usar

1. Abra o **FIMI RTSP Bridge** e informe o endereço:  
   `rtsp://IP_DO_SERVIDOR:8554/fimi`
2. (Opcional) usuário e senha do servidor RTSP.
3. Escolha resolução (1280×720 ou 1920×1080), FPS (15/24/30) e bitrate.
4. Toque em **Autorizar captura e iniciar** — o Android mostrará o consentimento
   do MediaProjection. **No Android 14+** você pode escolher só a janela do FIMI
   Navi; nas versões anteriores, ele captura a tela inteira.
5. Aceite a captura. O app entra em **Preparando → Conectando → Transmitindo**.
6. Toque em **Abrir FIMI Navi** — ele fica em primeiro plano e o serviço
   continua transmitindo em background.
7. Abra o vídeo em outro dispositivo:  
   `vlc --rtsp-tcp rtsp://IP_DO_SERVIDOR:8554/fimi`
8. Pra parar: use o botão do app **ou** a ação **Parar transmissão** da
   notificação persistente.

## Comando de volume

O comando mexe no **canal de mídia (`STREAM_MUSIC`) do celular** — o mesmo dos
alertas sonoros do FIMI Navi. Não exige permissão nenhuma no manifesto e funciona
com a transmissão parada ou rolando.

Três formas de acionar, porque cada uma cobre um cenário:

| Acionador | Onde funciona | Observação |
|---|---|---|
| Botões **+ / −** e slider do card “Comando de volume” | Tela do app | Slider só aplica ao soltar o dedo |
| **Teclas físicas** de volume | Enquanto a tela do app está na frente | Liga/desliga no switch do card |
| Ações **Vol +** / **Vol −** da notificação | Qualquer lugar, com o FIMI Navi na frente | É o caminho durante o voo |

Detalhes que importam:

- O **passo** é percentual (5%, 10% ou 20% do máximo do aparelho) e nunca move
  menos de 1 índice — em celular com escala curta (máximo 7) um passo de 10%
  arredondaria pra zero e o botão não faria nada.
- Com o FIMI Navi em primeiro plano, as teclas físicas voltam a ser do sistema:
  quem recebe evento de tecla é o app em foco. Por isso a notificação tem os
  botões — é o único acionador que funciona durante o voo.
- A notificação ecoa o volume aplicado (`Transmitindo • Vol 60%`) e, quando o
  sistema recusa (Não Perturbe, volume travado por dock/HDMI), mostra o motivo
  no lugar.
- O app **observa** o volume: mudanças feitas fora dele (painel do sistema, fone
  Bluetooth) aparecem no card sem precisar reabrir a tela.

## Usar com um servidor RTMP (ex.: Skywatch)

1. No painel do servidor, copie a **URL completa de publicação** do drone —
   normalmente no formato `rtmp://SEU-SERVIDOR:1935/live/<id-do-drone>?key=sk_...`.
   Se o servidor valida a chave por webhook, o `?key=` **precisa** ir junto; sem ele
   o servidor derruba a publicação logo depois do handshake (o app entra em
   "Reconectando" em loop).
2. Cole essa URL no campo **Endereço do servidor**. Os chips TCP/UDP somem: RTMP é
   sempre TCP.
3. Deixe usuário/senha vazios (a autenticação está na URL). Se o seu servidor usa
   RTMP com usuário/senha (Basic/Digest), aí sim preencha os dois campos.
4. **Autorizar captura e iniciar** → o vídeo aparece no player do servidor.
5. Em rede corporativa com filtro de tráfego, prefira `rtmps://…:443/…` se o
   servidor oferecer — TLS na porta 443 costuma passar onde 1935 é bloqueada.

## Diagnóstico de rede (USB + 4G/5G)

Durante o uso normal:

- O **FIMI Navi** conversa com o controle FIMI pelo **USB** — não interferimos
  nessa interface.
- O app **RTSP Bridge** usa a rede **padrão** do sistema (Wi-Fi ou celular).

O app não faz bind global do processo em uma rede específica pra não quebrar o
FIMI Navi. Se a lib subjacente escolher uma rota diferente da esperada, você vê
na tela status: **transporte** ativo, **internet validada** e o **endereço**
configurado. A mensagem de erro é sempre sanitizada (senha nunca aparece).

Se estiver com o **hotspot USB** ligado no celular, o Android pode preferir a
Wi-Fi como padrão. Desligue o hotspot se quiser garantir 4G/5G, ou vice-versa.

## Limitações

- **Android < 14**: o consentimento de MediaProjection captura a tela inteira
  — se você abrir notificações, elas aparecem no vídeo. Habitue-se ao “não
  perturbe” durante voo.
- **Interface e telemetria do FIMI**: o app transmite exatamente o que o FIMI
  Navi mostra. HUD, botões, mensagens, tudo entra no vídeo. Se você quer só a
  imagem do drone “limpa”, o FIMI Navi teria que expor esse feed nativo — o que
  ele não faz.
- **RTSPS**: aceito no validador; funciona se o servidor implementar. O MediaMTX
  oferece via `rtspsAddress` (comentado no exemplo).
- **Bateria e temperatura**: encoder de vídeo por 30–60 min esquenta o celular.
  Recomendamos ventilação, sem case e brilho reduzido.
- **RTSP simples é texto puro na rede** — use VPN/tunel/RTSPS ao sair da LAN.

## Arquitetura

```
app/
├── FimiApp.kt                        # Application; expõe SettingsRepository
├── ui/
│   ├── MainActivity.kt               # Activity + ActivityResult (MediaProjection)
│   ├── MainScreen.kt                 # Tela Compose Material 3
│   ├── MainViewModel.kt              # StateFlow<UiState>, StateFlow do serviço
│   ├── FimiLauncher.kt               # Busca FIMI Navi entre pacotes
│   └── theme/Theme.kt                # Material 3 dinâmico + light/dark
├── streaming/
│   ├── StreamService.kt              # Foreground Service mediaProjection
│   ├── ProjectionController.kt       # Sessão do MediaProjection
│   ├── StreamEngine.kt               # Contrato do motor
│   ├── RootEncoderStreamEngine.kt    # RootEncoder GenericStream (rtsp/rtsps/rtmp/rtmps)
│   ├── StreamState.kt                # Máquina de estados
│   └── Backoff.kt                    # 1,2,4,8,15s
├── audio/
│   ├── VolumeController.kt           # AudioManager (STREAM_MUSIC) + observa mudanças
│   └── VolumeMath.kt                 # Passo/porcentagem ↔ índice (puro, testável)
├── network/ConnectivityMonitor.kt
├── settings/SettingsRepository.kt    # DataStore
├── validation/StreamUrlValidator.kt  # rtsp/rtsps/rtmp/rtmps
└── util/{LogSanitizer,ScreenUtils}.kt
```

A máquina de estados fica em `StreamState.kt`:

```
Idle → AwaitingPermission → Preparing → Connecting → Streaming
                                                   ↘ Reconnecting → Connecting
                                                   ↘ Error
Qualquer estado → Stopping → Idle
```

## Testes

```bash
./gradlew testDebugUnitTest
```

Cobrem:

- `StreamUrlValidatorTest` — RTSP/RTSPS/RTMP/RTMPS, `?key=`, IPv6, userinfo, portas, caminhos.
- `BackoffTest` — 1, 2, 4, 8, 15s (saturado).
- `StreamStateMachineTest` — transições esperadas e proibidas.
- `LogSanitizerTest` — senha nunca vaza em log ou toast.
- `BitrateBoundsTest` — clamp de bitrate 300 kbps..20 Mbps.
- `VolumeMathTest` — passo nunca zera, teto/piso, round-trip índice ↔ porcentagem.

## Segurança

- Nenhuma senha vai pra logs — `LogSanitizer` intercepta URL/headers/pares.
- Foreground Service com tipo `mediaProjection` conforme exigido pelo Android
  14+ (`FOREGROUND_SERVICE_MEDIA_PROJECTION`).
- Cada sessão pede novo consentimento do MediaProjection.
- A notificação persistente é obrigatória e sempre visível.
- O serviço libera `VirtualDisplay`, encoder e sessão do MediaProjection em
  `stop`, `onDestroy`, revogação de permissão ou remoção da task (se o toggle
  “parar ao remover a tarefa” estiver ligado — padrão).

## Créditos

- [RootEncoder](https://github.com/pedroSG94/RootEncoder) — pipeline de
  encoder/streaming.
- [MediaMTX](https://github.com/bluenviron/mediamtx) — servidor RTSP/RTSPS/HLS/WebRTC.
