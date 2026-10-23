# IanPlayer PC — Overview

Port IanPlayer Android (versi 6.3.4, package `com.ianocent.musicplayer`) ke aplikasi desktop Windows 10.

## ApaIanPlayer itu (fakta dari code, jangan asumsi lagi)

IanPlayer Android **bukan video player**. Ini YouTube Music streaming + local music player dengan UI ala Spotify:

- Stream lagu dari YouTube Music (InnerTube) + metadata dari Tidal
- Local music library dari MediaStore
- Full player: synced lyrics karaoke (8 sumber API), queue, shuffle/repeat, crossfade
- Downloader lagu + auto tag (iTunes/Deezer) + ID3/MP4 tag writer
- "Liquid glass" UI (Haze blur), canvas backdrop (aurora/video/Ken Burns/Spotify Canvas), Monthly Recap ala Spotify Wrapped
- Voice assistant "Halo Yan" (wake word, bahasa Indonesia)
- Update otomatis via GitHub Releases
- App name user-visible: "Ian Player", dev ianocent, Indonesian

Detail lengkap per fitur → `05-FEATURE-PARITY.md`. Ini yang jadi acuan, bukan imajinasi "video player".

## Kenapa C++20 + Qt 6

Opsi yang dievaluasi:

| Opsi | Playback native | Perlu belajar | Scraping/stream | UI bagus di Win10? |
|---|---|---|---|---|
| C++20 + Qt 6 | Penuh (libmpv/WASAPI) | Low (skill sudah ada) | HTTP C++ + bundel yt-dlp | Ya, QML modern + glass effects |
| C# + WinUI 3 | Bagus | Middle (C# baru) | Sama mudah, ecosystem .NET luas | Bagus, tooling sering berubah |
| C# + Avalonia | Bagus | Middle | Sama | Bagus, bonus bisa Android/Mac |
| Rust + Tauri | Bagus | High (borrow checker) | yt-dlp via process | Tergantung webview |
| Electron | Worst: Chromium buat app lagu | Low | yt-dlp via process | Bagus tapi 300MB+ RAM idle |

### Keputusan stack

- **Language:** C++20 (MSVC v143)
- **UI:** Qt 6.7 LTS, QML Quick. Glass/blur pakai `MultiEffect` + layer blur; Haze Android diganti implementasi QML
- **Audio engine:** libmpv (ia bukan cuma video: audio decode semua format, gapless, WASAPI output). Alternatif QMediaPlayer ditolak: codec support cacat (flac/opus ok tapi gapless dan format eksotik lemah)
- **Stream URL resolution:** bundel `yt-dlp.exe` sebagai child process. Alasan di bawah
- **Build:** CMake + vcpkg, MSVC
- **Distribusi:** **portable saja** — folder berisi .exe + DLL, dobel klik langsung jalan, tanpa installer (keputusan user)
- **DB:** SQLite (schema dipetakan dari Room Android)
- **Settings:** QSettings ini memetakan `SettingsStore` Android 1:1

## Kenapa yt-dlp, bukan port penuh logic YouTube ke C++

Android: scraping InnerTube itu pure Kotlin HTTP+JSON (`YTMusicRepository.kt`) — gampang diport ke C++ (QNetworkAccessManager + QJsonDocument). **Tapi** stream URL resolution butuh **PoToken/BotGuard** via `zemer-cipher` yang jalan di **WebView**. Di desktop tidak ada WebView Android; option C++-nya:

1. **Bundel yt-dlp.exe** (recommended) — resolusi jalan di yt-dlp (ia sendiri selalu update detak konten YouTube), URL-nya di-feed ke libmpv. Search/home/feed tetap diport sendiri pakai InnerTube C++ biar kontrol UX ada di tangan
2. Port PoToken pakai Qt WebEngine (runtime 100MB+, fragile)

Pilihan yang diambil: **1**. Mekanisme detail di `02-ARCHITECTURE.md`.

## File index

| File | Isi |
|---|---|
| `00-OVERVIEW.md` | Dokumen ini — keputusan stack + peta file |
| `01-TOOLCHAIN.md` | Setup build environment Windows 10 sampai hello world jalan |
| `02-ARCHITECTURE.md` | Struktur code + porting map Android → PC |
| `03-DESIGN-SYSTEM.md` | Token warna, tipografi, komponen, semua rule tampilan |
| `04-MILESTONES.md` | Milestone & phase utama, checklist per phase (dokumen kerja utama) |
| `05-FEATURE-PARITY.md` | Matriks semua fitur Android → PC (tautan ke milestone) |
| `06-RISK-LOG.md` | Jebakan khusus project ini saat running di PC |

## Prinsip kerja

1. **Repo Android tidak disentuh.** Hanya jadi reference baca.
2. **Tiap milestone harus build + jalan.** No big-bang.
3. **Urutan risiko dulu:** audio jalan → UI → streaming. Streaming adalah risiko paling besar dan paling gampang mati pihak YouTube berubah; jangan pernah bikin seluruh app bergantung padanya sebelum lokal penuh jalan.
4. **Warning keamanan** (hanya catat, jangan eksekusi): code Android punya **credential Spotify `sp_dc` default yang ternyata live, committed di source** (`SettingsStore.DEFAULT_SPOTIFY_SP_DC`). Saat port, credential kredential user harusnya input, bukan tertanam. Kalau mau lanjut fitur Spotify Canvas, minta user masuk `cookie` sendiri di settings.
