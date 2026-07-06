<div align="center">
  <h1>🎵 Ian Player</h1>
  <p><b>Music player Android native modern yang dibangun dari nol pakai Kotlin + Jetpack Compose.</b></p>
  <p><i>Dibuat sebagai personal project untuk belajar Android development modern sambil bikin sesuatu yang beneran dipakai sehari-hari.</i></p>

  <!-- Placeholder Badges (Bisa diganti pakai shields.io) -->
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android" alt="Android" />
  <img src="https://img.shields.io/badge/Kotlin-1.9+-7F52FF?style=flat-square&logo=kotlin" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Compose-Material3-0081CB?style=flat-square" alt="Compose" />
</div>

---

## 📥 Downloads (Assets)
Dapatkan versi terbaru dari Ian Player untuk perangkat Android kamu:
* [📦 Download APK (Latest Release)](https://github.com/ianocent/ianplayer/releases/download/v1.0.0/IanPlayer-v1.0.0.apk)
*   [📜 Release Notes](#)
*   [💻 Source Code (.zip)](#)

---

## ✨ Fitur Utama

### 🎧 Core Playback
*   Scan otomatis semua lagu di device (support MP3, M4A, FLAC, WAV, OGG) via MediaStore.
*   Playback engine pakai Media3 ExoPlayer dengan MediaSessionService.
*   Musik tetap jalan di background dan muncul di notification shade / lockscreen.
*   Mendukung Next, previous, seek, shuffle, dan repeat (off / all / one).
*   Progress bar realtime dengan seek manual.

### 📱 Now Playing Screen
*   Didesain custom di Figma, di-porting 1:1 ke Compose.
*   Ambient color — warna UI (background, controls bar, slider, lyric card) otomatis diekstrak dari dominant color album art tiap lagu.
*   Menggunakan Palette API, warna disesuaikan kontrasnya untuk light/dark mode.
*   Synced lyrics (LRC timestamp) dari lrclib.net, auto-scroll & highlight baris aktif mengikuti posisi lagu (mirip YouTube Music).
*   Fallback ke plain lyrics kalau synced lyric gak tersedia.
*   Swipe-down gesture buat nutup Now Playing, dengan animasi smooth (scale + fade + spring).
*   Album art di-decode dengan downsampling biar scroll & render tetap ringan.

### 🖼️ Lyric Card Generator (Fitur Unik)
*   Pilih satu atau beberapa baris lirik langsung dari lyric view.
*   Generate kartu lirik bergaya custom (blur album art sebagai background, big quote mark, accent bar info lagu).
*   Desain dibuat berbeda dari gaya YouTube Music / Spotify / Genius.
*   Render pakai GraphicsLayer Compose, langsung disimpan ke galeri (Pictures/IanPlayer).

### 📚 Library & Playlist
*   Listing lagu dengan thumbnail album art, search bar untuk filter cepat.
*   Playlist custom: buat, isi lagu lewat dialog dengan checkbox + search, buka & putar isi playlist.
*   Playlist persisten — disimpan ke SharedPreferences sebagai JSON.
*   Data playlist tetap ada setelah app di-restart.

### 🎨 UI/UX
*   Light & dark mode toggle manual.
*   Mini player bar berbentuk floating card, ikut warna ambient album art.
*   Kontras teks pada mini player otomatis menyesuaikan (hitam/putih tergantung luminance background).
*   Transisi buka/tutup Now Playing pakai AnimatedVisibility dengan slide + fade.

---

## 🛠️ Tech Stack

| Kategori | Teknologi |
| :--- | :--- |
| **Bahasa** | Kotlin |
| **UI Toolkit** | Jetpack Compose (Material3) |
| **Audio Playback** | Media3 ExoPlayer + MediaSessionService |
| **Warna Adaptif** | Palette API (androidx.palette) |
| **Sync Lyrics** | lrclib.net API (gratis, tanpa API key) |
| **Persistensi** | SharedPreferences (JSON manual) |
| **System UI** | Accompanist SystemUiController |

---

## 📂 Struktur Proyek
```text
app/src/main/java/com/ianocent/musicplayer/
├── data/
│   ├── Song.kt                 # Data class lagu
│   ├── Playlist.kt             # Data class playlist
│   ├── MusicRepository.kt      # Query MediaStore untuk scan lagu
│   ├── AlbumArtLoader.kt       # Decode album art + ekstrak dominant color
│   └── LyricRepository.kt      # Fetch & parse synced/plain lyrics dari lrclib
├── player/
│   ├── PlaybackService.kt      # MediaSessionService (background playback)
│   └── PlayerManager.kt        # Wrapper kontrol player (play, pause, seek, dst)
├── viewmodel/
│   └── MusicViewModel.kt       # State management utama (single source of truth)
├── ui/
│   ├── NowPlayingScreen.kt     # Layar full player + lyric + controls
│   ├── LyricCardGenerator.kt   # Generator & save kartu lirik custom
│   └── theme/                  # Warna, tipografi, tema Material3
└── MainActivity.kt             # Listing screen, navigasi, entry point