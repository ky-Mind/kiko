#!/usr/bin/env bash
# Remote HP — konek + kontrol HP lewat Tailscale pakai scrcpy.
# Tanpa root, tanpa reset, tanpa ikon share-screen. Di HP cukup 1 app: Tailscale.
#
# Pakai:
#   ./remote-hp.sh           # konek via Tailscale lalu buka scrcpy
#   ./remote-hp.sh --off     # sama, tapi layar HP digelapin (tetap full kontrol)
#   ./remote-hp.sh --lan     # pakai IP wifi lokal (kalau HP se-wifi, lebih enteng)
#   ./remote-hp.sh --off --lan
#
# Override lewat env kalau IP berubah:
#   TS_IP=100.x.x.x LAN_IP=192.168.x.x ./remote-hp.sh

ADB="${ADB:-/home/kymind/Android/Sdk/platform-tools/adb}"
TS_IP="${TS_IP:-100.123.67.107}"     # IP Tailscale HP
LAN_IP="${LAN_IP:-192.168.88.93}"    # IP wifi lokal HP
PORT_FIXED="${PORT_FIXED:-5555}"     # port adb tetap (hasil 'adb tcpip 5555')
PORT_WLDBG="${PORT_WLDBG:-36565}"    # port wireless-debugging (cadangan)

SCREEN_OFF=0
HOST="$TS_IP"
WHICH="Tailscale"

while [ $# -gt 0 ]; do
  case "$1" in
    --off)  SCREEN_OFF=1 ;;
    --lan)  HOST="$LAN_IP"; WHICH="LAN" ;;
    -h|--help)
      grep '^#' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "argumen tak dikenal: $1"; exit 1 ;;
  esac
  shift
done

echo "==> konek ke HP ($WHICH: $HOST) ..."
DEV=""
for p in "$PORT_FIXED" "$PORT_WLDBG"; do
  "$ADB" connect "$HOST:$p" >/dev/null 2>&1
  if "$ADB" -s "$HOST:$p" shell true >/dev/null 2>&1; then
    DEV="$HOST:$p"; break
  fi
done

if [ -z "$DEV" ]; then
  echo "!! Gagal konek ke HP."
  echo "   Kemungkinan besar HP habis reboot -> Wireless Debugging mati."
  echo "   Perbaiki di HP:"
  echo "     Setelan > Opsi pengembang > Wireless debugging = ON"
  echo "   Lalu (sekali, saat HP se-wifi / colok USB) di PC:"
  echo "     $ADB connect $LAN_IP:$PORT_WLDBG   # atau pairing ulang"
  echo "     $ADB -s <dev> tcpip $PORT_FIXED     # set port tetap lagi"
  exit 1
fi

echo "==> terkoneksi: $DEV"
FLAGS=( --video-bit-rate 2M --max-size 1024 --max-fps 30 --window-title "Remote HP ($WHICH)" )
if [ "$SCREEN_OFF" = "1" ]; then
  FLAGS+=( --turn-screen-off --stay-awake )
  echo "==> layar HP digelapin (tetap full kontrol dari sini)"
fi

echo "==> buka scrcpy ..."
exec scrcpy -s "$DEV" "${FLAGS[@]}"
