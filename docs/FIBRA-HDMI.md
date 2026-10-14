# Levar a imagem do drone por fibra e HDMI

Objetivo: o que o piloto vê no FIMI Navi aparecer num monitor/TV/projetor
longe do ponto de voo (central de operação, ônibus de apoio, outro prédio),
usando **fibra óptica** como transporte e **HDMI** na ponta.

## Primeiro, a verdade que economiza dinheiro

- **A fibra não é o gargalo de latência.** A luz leva ~5 µs por km. Quem pesa é
  a captura + codificação H.264 no celular (~100–300 ms) e o buffer do player.
  Fibra de 2 km ou de 20 m dá no mesmo.
- **O celular não tem saída HDMI utilizável durante o voo.** O FIMI Navi usa o
  conector USB-C do celular para falar com o controle. Adaptador USB-C→HDMI
  ocuparia a mesma porta. Por isso o caminho prático é *software*: o RTSP Bridge
  tira o vídeo da tela e manda pela rede.
- Se o seu controle tiver saída HDMI/AV própria, **use-a** (Caminho C) — é a
  rota de menor latência e não depende do celular.

## Caminhos

```
A) Rede sobre fibra  (recomendado — 1 cabo, multiponto, grava e escala)

 Celular + RTSP Bridge --Wi-Fi/4G--> [Mini-PC / Raspberry Pi: MediaMTX]
                                              │ Ethernet
                                      Conversor de mídia SFP
                                              │  FIBRA (SM/MM)
                                      Conversor de mídia SFP
                                              │ Ethernet
                                [Raspberry Pi / mini-PC: play-hdmi.sh] --HDMI--> Monitor/TV
                                              └── (ou VLC/OBS/NVR direto na rede)

B) Saída HDMI local + extensor HDMI-sobre-fibra  (zero rede na ponta distante)

 Celular --Wi-Fi--> [Pi/mini-PC: MediaMTX + play-hdmi.sh] --HDMI--> Extensor HDMI TX
                                                                        │ FIBRA
                                                                  Extensor HDMI RX --HDMI--> Monitor/TV

C) Direto do controle (se o modelo tiver HDMI/AV out)

 Controle --HDMI--> Extensor HDMI TX --FIBRA--> Extensor HDMI RX --HDMI--> Monitor/TV
```

| | A) IP sobre fibra | B) HDMI sobre fibra | C) Controle → HDMI |
|---|---|---|---|
| Latência extra da fibra | desprezível | desprezível | desprezível |
| Latência total típica | 0,4–1 s | 0,4–1 s | < 50 ms |
| Vários monitores | sim (switch) | precisa splitter | precisa splitter |
| Gravar / reutilizar | sim (MediaMTX) | não | não |
| Custo | 2 conversores SFP (~US$ 30–60) + Pi | 1 par de extensores (~US$ 80–400) + Pi | 1 par de extensores |
| Passa pelo celular | sim | sim | **não** |

## Passo a passo — Caminho A (o que o repositório já suporta)

1. **Servidor** (mini-PC ou Raspberry Pi 4/5 perto de onde o celular conecta):
   ```bash
   cd server && docker compose up -d     # MediaMTX em rtsp://HOST:8554/fimi
   ```
   Troque as senhas em `server/mediamtx.yml` antes de qualquer coisa.
2. **Celular**: abra o FIMI RTSP Bridge, URL `rtsp://publisher:SENHA@IP_DO_SERVIDOR:8554/fimi`,
   720p/30 fps e 4–6 Mbps é o ponto doce para latência baixa. Veja o README raiz.
3. **Fibra**: ligue o servidor num conversor de mídia (ou direto numa porta SFP do
   switch) e leve a fibra até o ponto remoto. Requisitos práticos:
   - Distância ≤ 550 m → multimodo (OM3) com SFP SR 850 nm; acima disso → monomodo (OS2)
     com SFP LR 1310 nm (até 10 km). Os **dois SFPs precisam ser do mesmo tipo**
     (ou par BiDi TX/RX complementar: 1310/1550 nm).
   - Conectores LC/UPC (azul) ou SC/APC (verde) **não se misturam**: APC com UPC danifica o polimento.
   - 1 Gbps sobra: o stream usa < 10 Mbps.
4. **Ponta HDMI** (Raspberry Pi/mini-PC conectado à fibra e ao monitor):
   ```bash
   sudo apt install mpv
   STREAM_URL='rtsp://reader:SENHA@IP_DO_SERVIDOR:8554/fimi' server/hdmi/play-hdmi.sh
   ```
   Para subir sozinho no boot e reconectar quando o drone cair, use
   `server/hdmi/fimi-hdmi.service` (instruções no cabeçalho do arquivo).
5. **Sem Pi na ponta**: qualquer PC/NVR/VLC da rede lê
   `vlc --rtsp-tcp --network-caching=200 rtsp://reader:SENHA@IP:8554/fimi`,
   ou abra `http://IP:8889/fimi` (WebRTC) num navegador de smart TV.

## Passo a passo — Caminho B

Igual ao A, mas o `play-hdmi.sh` roda no **mesmo** equipamento do MediaMTX
(`STREAM_URL=rtsp://reader:SENHA@127.0.0.1:8554/fimi`) e a fibra sai do **HDMI**
dele para um par de extensores HDMI-sobre-fibra. Na compra, confira:

- resolução/refresh que casa com a saída do Pi (1080p60 é o seguro);
- "HDMI 1.4 / HDCP" — o stream do drone não tem HDCP, mas alguns extensores baratos exigem EDID válido;
- tipo da fibra (1 fibra simplex monomodo é o mais barato de passar) e conector.

## Ajuste de latência (o que realmente move o ponteiro)

| Onde | Ajuste | Efeito |
|---|---|---|
| App | FPS 30, 720p, bitrate 4–6 Mbps, GOP 1 s (já é o padrão) | menos fila no encoder |
| Rede | Wi-Fi 5 GHz ou 4G/5G estável; evite RTSP/UDP em Wi-Fi ruim | menos retransmissão |
| Player | `--profile=low-latency --no-cache --untimed` (já no script) | corta 200–500 ms de buffer |
| Player | WebRTC/WHEP em vez de HLS | ~0,3–0,5 s vs. 3–10 s do HLS |
| Display | modo "Game"/"PC" da TV | tira 30–100 ms de pós-processamento |

## Problemas comuns

- **Tela preta, log "401"**: usuário/senha do `reader` não confere com `mediamtx.yml`.
- **Imagem trava a cada ~1 s**: troque `--rtsp-transport=tcp` por UDP só se a rede for limpa; senão reduza bitrate.
- **mpv sem hardware decode no Pi 5**: o Pi 5 não tem decoder H.264 em hardware; 1080p30 por software dá conta, mas use 720p se esquentar.
- **Sem link de fibra**: LED do SFP apagado = TX/RX trocados (inverta o par) ou fibra monomodo em SFP multimodo.
- **Rede aberta**: RTSP simples vai em texto puro. Fibra privada entre dois pontos é segura; Wi-Fi/4G não — use VPN/Tailscale no trecho do celular até o servidor.
