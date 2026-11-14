# Remote HP

Remote-control penuh untuk **HP milikmu sendiri** dari desktop/HP lain lewat jaringan apa pun
(wifi beda, kuota, beda kota) — tanpa server berbayar, tanpa pihak ketiga, data langsung
HP ↔ desktop lewat **Tailscale**.

Satu APK berisi:
- **Foreground service + WakeLock** — tetap hidup saat ditinggal/dicas.
- **Mini web server** (HTTP + WebSocket) di dalam HP (port `8787`).
- **Screen mirroring** via MediaProjection (stream JPEG ~15 fps).
- **Remote input** via Accessibility Service (tap, geser, ketik, Back/Home/Recents).
- **Dashboard** yang dibuka di browser desktop (tinggal buka alamat IP-nya).

> Ini alat untuk perangkat milikmu sendiri. Android mewajibkan notifikasi permanen
> saat service jalan dan indikator saat merekam layar — itu memang desainnya, transparan.
> Jangan dipakai untuk mengakses perangkat orang lain tanpa izin.

---

## Arsitektur

```
[Desktop Jakarta]  --Tailscale-->  [HP Sukabumi]
   browser                           APK: web server :8787
   buka http://100.x.x.x:8787        - MediaProjection -> JPEG -> WebSocket
   klik/ketik di gambar ----JSON---> - Accessibility -> dispatchGesture
```

Tidak ada VPS / relay / Cloudflare. Tailscale bikin HP & desktop seolah satu LAN privat.

---

## Build

Butuh Android Studio (atau Android SDK + Gradle).

```bash
# dari folder proyek
./gradlew assembleDebug
# APK keluar di: app/build/outputs/apk/debug/app-debug.apk
```

Belum ada Gradle wrapper (`gradlew`) di repo ini. Pilih salah satu:
- Buka folder ini di **Android Studio** (paling gampang, wrapper otomatis dibuat), atau
- punya Gradle global: `gradle wrapper` lalu `./gradlew assembleDebug`.

Install ke HP: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
(atau kirim APK ke HP dan pasang manual).

---

## Setup di HP (sekali saja)

1. **Pasang Tailscale** di HP dan di desktop, login akun sama. Catat IP Tailscale HP (`100.x.x.x`).
2. Buka aplikasi **Remote HP**:
   - Tombol **1. Aktifkan Accessibility** → aktifkan "Remote HP" di daftar.
   - Tombol **2. Mulai + Izinkan Rekam Layar** → setujui dialog rekam layar.
3. Di layar aplikasi muncul alamat, mis. `http://100.x.x.x:8787`.
4. **Pengaturan daya** biar tidak mati saat ditinggal:
   - Setelan → Aplikasi → Remote HP → Baterai → **Unrestricted**.
   - Developer options → **Stay awake while charging** = ON (biar tetap responsif saat dicas).

## Pakai dari desktop

Buka `http://100.x.x.x:8787` di browser. Akan muncul layar HP; klik = tap,
drag = geser, tombol Back/Home/Recents, dan kotak teks untuk mengetik.

---

## Catatan jujur / batasan (milestone berikutnya)

- **Layar benar-benar mati total (deep sleep):** mirror bisa jadi gelap/berhenti, karena
  sistem memadamkan panel. Solusi sekarang: "Stay awake while charging". Milestone lanjut:
  menon-aktifkan panel tapi display tetap logis-on (butuh trik tambahan, mirip
  `scrcpy --turn-screen-off`).
- **Stream = MJPEG** (sederhana & pasti jalan). Upgrade ke **H.264 (MediaCodec)** untuk
  kualitas lebih halus + bandwidth lebih kecil = milestone lanjut.
- **Ketik teks** hanya jalan kalau ada kolom input yang sedang fokus di HP.
- Ini **debug build**; untuk dipakai rutin sebaiknya di-sign jadi release.
- Belum ada autentikasi di web server — aman karena hanya lewat jaringan privat Tailscale,
  tapi milestone lanjut: tambah token/password.

## Struktur kode

| File | Isi |
|------|-----|
| `MainActivity.kt` | tombol izin + tampil alamat IP |
| `ControlService.kt` | foreground service, wakelock, start server + capture |
| `ScreenCapturer.kt` | MediaProjection → JPEG frames |
| `WebServer.kt` | NanoWSD: serve dashboard + WebSocket frame/command |
| `CommandHandler.kt` | parse JSON perintah → Accessibility |
| `InputAccessibilityService.kt` | tap/swipe/ketik/global action |
| `assets/dashboard.html` | UI kontrol di desktop |
