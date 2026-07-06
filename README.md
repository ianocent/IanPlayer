Ian Player 🎵

Music player Android native yang dibangun dari nol pakai Kotlin + Jetpack Compose. Dibuat sebagai personal project untuk belajar Android development modern sambil bikin sesuatu yang beneran dipakai sehari-hari.

✨ Fitur

Playback Inti

Scan otomatis semua lagu di device (support MP3, M4A, FLAC, WAV, OGG) via MediaStore

Playback engine pakai Media3 ExoPlayer dengan MediaSessionService — musik tetap jalan di background dan muncul di notification shade / lockscreen

Next, previous, seek, shuffle, dan repeat (off / all / one)

Progress bar realtime dengan seek manual

Now Playing Screen

Didesain custom di Figma, di-porting 1:1 ke Compose

Ambient color — warna UI (background, controls bar, slider, lyric card) otomatis diekstrak dari dominant color album art tiap lagu, pakai Palette API, lalu disesuaikan kontrasnya untuk light/dark mode

Synced lyrics (LRC timestamp) dari lrclib.net, auto-scroll & highlight baris aktif mengikuti posisi lagu — mirip YouTube Music

Fallback ke plain lyrics kalau synced lyric gak tersedia

Swipe-down gesture buat nutup Now Playing, dengan animasi smooth (scale + fade + spring)

Album art di-decode dengan downsampling biar scroll & render tetap ringan

Lyric Card Generator

Fitur unik: pilih satu atau beberapa baris lirik langsung dari lyric view

Generate kartu lirik bergaya custom (blur album art sebagai background, big quote mark, accent bar info lagu) — dibuat berbeda dari gaya YouTube Music / Spotify / Genius

Render pakai GraphicsLayer Compose, langsung disimpan ke galeri (Pictures/IanPlayer)

Library & Playlist

Listing lagu dengan thumbnail album art, search bar untuk filter cepat

Playlist custom: buat, isi lagu lewat dialog dengan checkbox + search, buka & putar isi playlist

Playlist persisten — disimpan ke SharedPreferences sebagai JSON, tetap ada setelah app di-restart

UI/UX

Light & dark mode toggle manual

Mini player bar berbentuk floating card, ikut warna ambient album art, dengan kontras teks otomatis (hitam/putih tergantung luminance background)

Transisi buka/tutup Now Playing pakai AnimatedVisibility dengan slide + fade

🛠️ Tech Stack

KategoriTeknologiBahasaKotlinUI ToolkitJetpack Compose (Material3)Audio PlaybackMedia3 ExoPlayer + MediaSessionServiceWarna AdaptifPalette API (androidx.palette)Sync Lyricslrclib.net API (gratis, tanpa API key)PersistensiSharedPreferences (JSON manual)System UIAccompanist SystemUiController

📂 Struktur Proyek

app/src/main/java/com/ianocent/musicplayer/ ├── data/ │ ├── Song.kt # Data class lagu │ ├── Playlist.kt # Data class playlist │ ├── MusicRepository.kt # Query MediaStore untuk scan lagu │ ├── AlbumArtLoader.kt # Decode album art + ekstrak dominant color │ └── LyricRepository.kt # Fetch & parse synced/plain lyrics dari lrclib ├── player/ │ ├── PlaybackService.kt # MediaSessionService (background playback) │ └── PlayerManager.kt # Wrapper kontrol player (play, pause, seek, dst) ├── viewmodel/ │ └── MusicViewModel.kt # State management utama (single source of truth) ├── ui/ │ ├── NowPlayingScreen.kt # Layar full player + lyric + controls │ ├── LyricCardGenerator.kt # Generator & save kartu lirik custom │ └── theme/ # Warna, tipografi, tema Material3 └── MainActivity.kt # Listing screen, navigasi, entry point

🚀 Cara Menjalankan

Clone repo ini

Buka di Android Studio (versi terbaru direkomendasikan untuk kompatibilitas Compose API terbaru)

Sync Gradle

Colok device fisik (atau pakai emulator dengan file audio manual)

Run — app akan minta izin akses media (READ_MEDIA_AUDIO / READ_EXTERNAL_STORAGE)

📋 Requirement

Android Studio dengan Compose BOM yang support rememberGraphicsLayer (untuk fitur Lyric Card)

Min SDK 24

Koneksi internet untuk fitur lyric sync (opsional, ada fallback offline behavior)

🗺️ Roadmap

[ ] Refactor & relayout listing screen (search bar, header, playlist card sesuai desain Figma terbaru)

[ ] Detail lyric card: styling tambahan / template pilihan

[ ] Loading state yang lebih baik untuk permission handling

[ ] Playlist reorder (drag & drop)

📄 Lisensi

Personal project — bebas dipakai sebagai referensi belajar.

