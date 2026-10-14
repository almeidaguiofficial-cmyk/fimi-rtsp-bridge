#!/usr/bin/env bash
# Reproduz o stream do drone (MediaMTX) em tela cheia na saída HDMI local,
# com latência mínima. Pensado para Raspberry Pi / mini-PC Linux sem desktop (DRM/KMS),
# mas funciona também dentro de uma sessão X11/Wayland (VO=gpu sem DRM).
#
# Uso:
#   STREAM_URL='rtsp://reader:SENHA@127.0.0.1:8554/fimi' ./play-hdmi.sh
#
# Variáveis opcionais:
#   STREAM_URL  URL RTSP de leitura            (padrão: rtsp://reader:EscolhaOutraSenhaForte@127.0.0.1:8554/fimi)
#   GPU_CTX     drm | x11egl | wayland | auto   (padrão: drm; use auto dentro de desktop)
#   HWDEC       decodificação por hardware      (padrão: auto-safe)
#   RETRY_S     espera entre tentativas, em s   (padrão: 2)
set -u

STREAM_URL="${STREAM_URL:-rtsp://reader:EscolhaOutraSenhaForte@127.0.0.1:8554/fimi}"
GPU_CTX="${GPU_CTX:-drm}"
HWDEC="${HWDEC:-auto-safe}"
RETRY_S="${RETRY_S:-2}"

command -v mpv >/dev/null 2>&1 || { echo "mpv não encontrado (apt install mpv)" >&2; exit 1; }

# Loop infinito: se o drone/app cair, o MediaMTX fecha o path e o mpv sai; reabrimos sozinhos.
while true; do
  mpv \
    --profile=low-latency \
    --no-audio \
    --no-cache \
    --untimed \
    --rtsp-transport=tcp \
    --hwdec="$HWDEC" \
    --vo=gpu --gpu-context="$GPU_CTX" \
    --fullscreen \
    --no-osc --no-input-default-bindings --really-quiet \
    --keep-open=no \
    "$STREAM_URL"
  echo "[$(date +%T)] sem sinal, tentando de novo em ${RETRY_S}s..." >&2
  sleep "$RETRY_S"
done
