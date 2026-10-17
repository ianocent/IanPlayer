# Milestones & Timeline: IanPlayer Refactor & Optimization

## Milestone 1: Persiapan Arsitektur & State Management
- **Target:** Menyiapkan mesin (engine) untuk fitur *Multi-Layout*.
- **Tugas:**
  - Tambahkan `enum class AppLayout { BASE, SPOTIFY, YTMUSIC, APPLE }`.
  - Update `data/SettingsStore.kt` dengan *DataStore* untuk menyimpan pilihan layout.
  - Refactor kerangka utama di `ui/NowPlayingScreen.kt` agar menggunakan blok kondisional (`when`) untuk memanggil *Composable* sesuai preferensi layout.

## Milestone 2: Implementasi Liquid Glass Canvas & Blur
- **Target:** Membangun fondasi efek visual refraksi (mengadopsi sistem Convx).
- **Tugas:**
  - Rombak `ui/canvas/PlayerCanvas.kt` untuk mengimplementasikan *logic* `drawBackdrop()` dan `layerBackdrop()`.
  - Hubungkan *background blur engine* ini dengan ekstraksi gambar dari `data/AlbumArtLoader.kt` & visualizer di `player/WaveProjectionService.kt`.
  - *Profiling* FPS untuk memastikan *blur engine* ini tidak membuat *memory leak*.

## Milestone 3: Bouncy & Spring-Based Animations
- **Target:** Memberikan *feel* premium dan perpindahan yang *enjoyable*.
- **Tugas:**
  - Update `ui/NavState.kt` agar animasi pindah halaman *(page transitions)* menggunakan konfigurasi `spring()` dengan *dampingRatio* menengah ke rendah (agar memantul).
  - Terapkan efek *overscroll* (bouncy) pada senarai di `ui/SongListComponents.kt` dan khususnya di `ResponsiveSnapList.kt`.

## Milestone 4: Desain Individual Layout
- **Target:** Merealisasikan ketiga UI referensi.
- **Tugas:**
  - **Spotify Layout:** Fokus pada sinkronisasi `ui/theme/Color.kt` untuk gradien.
  - **Apple Music Layout:** Integrasi maksimal dengan kanvas *Liquid Glass* (fokus di *fullscreen Now Playing*).
  - **YT Music Layout:** Optimasi `ui/tabs/StreamTab.kt` dan daftar antrean *(queue)* yang rapat ala YouTube.

## Milestone 5: Fitur Ekstra & Final Polish
- **Target:** Menajamkan fitur spesifik tambahan dan rilis stabilitas.
- **Tugas:**
  - Update rendering lirik di `ui/LyricCardGenerator.kt` menggunakan state posisi lagu (Karaoke style).
  - Penyelarasan animasi navigasi dari `MainActivity.kt`.
  - *Final testing*.