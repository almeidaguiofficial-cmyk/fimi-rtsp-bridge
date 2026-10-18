# Servidor MediaMTX para o FIMI RTSP Bridge

Este servidor recebe o vídeo publicado pelo celular via RTSP ou SRT e redistribui em RTSP, SRT, HLS e WebRTC.

## Requisitos

- Docker Desktop (Windows/macOS) ou Docker Engine + Compose (Linux).
- Uma porta 8554 livre no host que rodar o servidor.

## Subir

```bash
cd server
docker compose up -d
```

O MediaMTX passa a escutar em:

- `rtsp://SEU_HOST:8554/fimi`  ← endpoint que o app publica
- `rtsp://SEU_HOST:8554/fimi`  ← endpoint que VLC/OBS/NVR consomem
- `srt://SEU_HOST:8890?streamid=publish:fimi:publisher:SENHA` ← publicação SRT (4G instável)
- `http://SEU_HOST:8888/fimi/index.m3u8` ← HLS
- `http://SEU_HOST:8889/fimi` ← WebRTC

## Segurança

O `mediamtx.yml` já vem com autenticação interna ativada. Antes de expor:

1. Troque os campos `pass:` dos usuários `publisher` e `reader` por senhas fortes.
2. Não abra a porta 8554 diretamente na internet — use VPN, tailscale ou tunel SSH.
3. Se precisar RTSPS, gere um certificado (Let's Encrypt/mkcert) e habilite `rtspsAddress` no MediaMTX.

## Testar publicação sem o celular

```bash
ffmpeg -re -f lavfi -i "testsrc2=size=1280x720:rate=30" -c:v libx264 -tune zerolatency \
  -f rtsp rtsp://publisher:EscolhaUmaSenhaForte@localhost:8554/fimi
```

## Publicar por SRT (4G instável)

No app, use como endereço:

```
srt://SEU_HOST:8890?streamid=publish:fimi:publisher:EscolhaUmaSenhaForte
```

As credenciais vão dentro do `streamid` (`acao:caminho:usuario:senha`); os campos
usuário/senha do app só valem para RTSP/RTMP. O app acrescenta `&latency=800` (ms)
se a URL não trouxer `latency=` — aumente para 1200–2000 em 4G muito ruim.

Teste sem o celular:

```bash
ffmpeg -re -f lavfi -i "testsrc2=size=1280x720:rate=30" -c:v libx264 -tune zerolatency \
  -f mpegts "srt://localhost:8890?streamid=publish:fimi:publisher:EscolhaUmaSenhaForte&pkt_size=1316"
```

## Consumir no VLC

```bash
vlc --rtsp-tcp rtsp://reader:EscolhaOutraSenhaForte@SEU_HOST:8554/fimi
```

Se der latência alta, `--rtsp-tcp` força TCP (mais estável em NAT/rede pública). Para
reduzir a latência interna do VLC ainda mais, adicione `--network-caching=200`.

## Consumir em navegador (WebRTC)

Abra `http://SEU_HOST:8889/fimi` em qualquer navegador moderno. Latência típica < 500 ms.

## Portas explicadas

| Porta       | Uso                     | Expor?                        |
|-------------|-------------------------|-------------------------------|
| 8554/tcp    | RTSP publicação/leitura | Somente para clientes         |
| 8322/tcp    | RTSPS (opcional, TLS)   | Somente com certificado válido|
| 8890/udp    | SRT publicação/leitura  | Somente para clientes         |
| 8888/tcp    | HLS                     | Se quiser player web/OBS      |
| 8889/tcp    | WebRTC (WHIP/WHEP)      | Se quiser player web          |
| 8189/udp    | ICE/WebRTC              | Junto com 8889                |
| 9997/tcp    | API interna             | Nunca — restrita a 127.0.0.1  |

## Windows sem Docker

Baixe o binário do MediaMTX em https://github.com/bluenviron/mediamtx/releases e
rode `mediamtx.exe mediamtx.yml` na pasta `server/`. As portas são as mesmas.
