# FIMI RTSP Bridge

App Android que espelha a tela do celular durante o uso do **FIMI Navi 3.0** e
publica o vídeo por **RTSP, RTMP ou SRT** para um servidor (MediaMTX, Skywatch,
OBS Studio como cliente, NVR, etc.). Serve como ponte pra sacar do FIMI Navi um
sinal ao vivo que outras ferramentas conseguem ler — VLC, OBS, câmeras IP
virtuais, análise de vídeo.

Testado com o padrão de captura já em produção no Skywatch Cast (RootEncoder 2.8.1).

## O que ele faz

- Pega a tela do celular (ou só a janela do FIMI Navi no Android 14+).
- Codifica em **H.264 por hardware**, GOP de 1s, sem B-frames, sem áudio.
- Publica em `rtsp://SERVIDOR:8554/fimi`, `rtmp://SERVIDOR:1935/live/ID?key=...` **ou**
  `srt://SERVIDOR:8890?streamid=publish:fimi` (o app escolhe o protocolo pelo esquema da URL;
  `rtsps://` e `rtmps://` também valem).
- **Adapta à internet** (ligado por padrão): baixa o bitrate quando a subida não dá conta e
  descarta atraso acumulado para a imagem não travar — ver
  [Internet instável](#internet-instável-imagem-travando).
- Roda em **Foreground Service tipo mediaProjection**, notificação persistente
  com botão “Parar transmissão”.
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

## Internet instável (imagem travando)

Por que trava: quando o 4G não aguenta o bitrate, o vídeo vai se acumulando na fila de envio
da RootEncoder (até 400 frames ≈ 13 s a 30 fps). O espectador vê a imagem cada vez mais
atrasada e, quando a fila lota, a lib descarta frames soltos no meio do GOP — a imagem
congela ou borra até o próximo keyframe.

O que o app faz com **Adaptar à internet** ligado (padrão):

| Mecanismo | O que faz |
|---|---|
| Bitrate adaptativo | `QueueAwareBitrateAdapter` da RootEncoder lê a fila a cada segundo: quando frames começam a empilhar, baixa o bitrate para abaixo do que a rede entregou (piso de 300 kbps); quando a rede melhora, sobe de volta até o máximo escolhido. |
| Salto para o ao vivo | Se mesmo assim a fila passar de **2 s**, ela é descartada de uma vez e o encoder solta um keyframe: perde-se um trecho, mas a imagem volta ao ao vivo em vez de virar replay atrasado. |
| Painel de rede | O card de status mostra qualidade da internet, atraso na fila, bitrate adaptado e saltos. A notificação avisa "internet instável/ruim" — é o que dá pra ver com o FIMI Navi na frente. |

Simulação com os componentes reais (queda do 4G de 5 para 1,2 Mbps por 60 s, teto de 3 Mbps):
sem adaptação o atraso passa de **36 s** e não para de crescer; com adaptação fica em
**1,7 s** e o bitrate volta sozinho a 3 Mbps quando a rede se recupera.

Dicas de campo, em ordem de impacto:

1. **SRT, se o servidor aceitar** (o MediaMTX do `server/` aceita): UDP com retransmissão de
   pacote perdido, feito para link ruim. Endereço:
   `srt://SERVIDOR:8890?streamid=publish:fimi:usuario:senha`. O app acrescenta `latency=800`
   (ms) se a URL não trouxer; em 4G muito ruim use `&latency=1500`.
2. **720p a 24 fps** com bitrate máximo de 2–3 Mbps. 1080p a 300 kbps vira borrão; 720p
   aguenta bem melhor a queda.
3. **RTSP sempre em TCP** fora da rede local. UDP no RTSP não reenvia pacote perdido.
4. Celular com sinal: 4G com uma barra em área rural sobe 0,5–1 Mbps — nenhum software
   faz milagre abaixo disso.

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
│   ├── RootEncoderStreamEngine.kt    # RootEncoder GenericStream (rtsp/rtsps/rtmp/rtmps/srt)
│   ├── NetworkAdaptation.kt          # LatencyGuard, qualidade do link, NetworkStats
│   ├── SrtUrl.kt                     # latência padrão do SRT (800 ms)
│   ├── StreamState.kt                # Máquina de estados
│   └── Backoff.kt                    # 1,2,4,8,15s
├── network/ConnectivityMonitor.kt
├── settings/SettingsRepository.kt    # DataStore
├── validation/StreamUrlValidator.kt  # rtsp/rtsps/rtmp/rtmps/srt
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

- `StreamUrlValidatorTest` — RTSP/RTSPS/RTMP/RTMPS/SRT, `?key=`, `streamid=`, IPv6, userinfo, portas, caminhos.
- `BackoffTest` — 1, 2, 4, 8, 15s (saturado).
- `StreamStateMachineTest` — transições esperadas e proibidas.
- `LogSanitizerTest` — senha nunca vaza em log ou toast.
- `BitrateBoundsTest` — clamp de bitrate 300 kbps..20 Mbps.
- `LatencyGuardTest` — salto para o ao vivo com fila ≥ 2 s e cooldown de 5 s.
- `LinkQualityClassifierTest` — internet boa/instável/ruim a partir da fila e do bitrate.
- `SrtUrlTest` — latência padrão do SRT sem atropelar a escolhida pelo usuário.

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
