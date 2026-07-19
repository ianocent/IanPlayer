<div align="center">
  <h1>🎵 Ian Player</h1>
  <p><b>A modern native Android music player — local library, multi-source streaming, synced lyrics, and more.</b></p>

  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android" alt="Android" />
  <img src="https://img.shields.io/badge/Kotlin-2.1+-7F52FF?style=flat-square&logo=kotlin" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Compose-Material3-0081CB?style=flat-square" alt="Compose" />
  <img src="https://img.shields.io/badge/Min%20SDK-24-333?style=flat-square" alt="Min SDK" />
  <img src="https://img.shields.io/badge/Version-2.0.0-8A2BE2?style=flat-square" alt="Version" />
</div>

---

## 📥 Download

| Asset | Link                                                                                                          |
| :--- |:--------------------------------------------------------------------------------------------------------------|
| **APK (Latest)** | [IanPlayer-v.3.2.5.apk](https://github.com/ianocent/ianplayer/releases/download/v3.2.5/IanPlayer-v.3.2.5.apk) |
| **Release Notes** | [v3.2.5](https://github.com/ianocent/IanPlayer/releases/tag/v3.2.5)                                           |

---

## ✨ Features

### 🎧 Core Playback

- Auto‑scans all local audio via `MediaStore` (MP3, M4A, FLAC, WAV, OGG).
- Powered by **Media3 ExoPlayer** with `MediaSessionService` for background playback.
- Full notification with album art, title, and artist — works on lock screen.
- **Next / Previous / Seek / Shuffle / Repeat** (Off → All → One).
- Real‑time progress slider with manual seeking.
- Queue & Upnext management with drag‑to‑reorder in playlists.

### 🌐 Multi‑Source Streaming

- Search songs across **three sources** (run in parallel for speed):
  - **JioSaavn** — free high‑quality streams, album art, and metadata.
  - **YouTube Music** — InnerTube API with PoToken / BotGuard (ZemerCipher).
  - **SoundCloud** — public tracks via API v2 with auto client‑id extraction.
- Results are **deduplicated** and listed in a paginated feed.
- Tap any stream result to play instantly — no download needed.

### ⬇️ Download & Metadata

- Download any streamed song straight to `Music/IanPlayer/`.
- Auto‑fetches **highest available bitrate** (YouTube: up to 256k AAC / Opus).
- **ID3 metadata** (title, artist, album, embedded album‑art) written via **mp3agic**.
- MediaStore entry is also updated so Android system picks up the tags correctly.
- Download quality dialog with "Best" badge when multiple formats are available.

### 📱 Now Playing Screen

- Custom Figma design ported 1:1 to Compose.
- **Ambient color** — background, controls, slider, and lyrics automatically tinted from the dominant color of the album art (`Palette API`).
- **Synced lyrics** (LRC) from lrclib.net with auto‑scroll and active‑line highlight.
- Falls back to **plain lyrics** (lrclib / lyrics.ovh / some‑random‑api).
- Swipe‑down to dismiss with scale‑fade‑spring animation.
- Album art downsampled for smooth rendering.

### 🖼️ Card Generators

| Card | Description |
| :--- | :--- |
| **Song Card** | Now‑playing card with blurred album‑art background, accent bar, song info. |
| **Lyric Card** | Select one or multiple lyric lines → generate a stylized quote card. |
| **Playlist Card** | Playlist summary card with song list; saved to gallery + shareable via FileProvider. |

All cards are rendered with Compose `GraphicsLayer` and saved to `Pictures/IanPlayer/`.

### 📊 Monthly Music Recap

- Spotify Wrapped‑style summary card every month.
- Shows: **total plays**, **total minutes**, **top 5 artists**, **top 3 songs**.
- Auto‑generated **taste commentary** based on your listening patterns.
- Dismissible; appears when you have ≥5 plays in the past 30 days.

### 📚 Library & Playlists

- **Tabs**: Songs | Albums | Stream | Playlists.
- **Sort modes**: A‑Z, Recently Added, Most Played (with play‑count tracking).
- **Album grouping** with drill‑down detail view and shuffle‑all.
- **Custom playlists**: create, rename, add / remove songs via checkbox dialog + search.
- **Drag‑to‑reorder** songs inside playlists.
- All playlist data persisted in SharedPreferences as JSON.

### 🔧 Song Management

- **Edit song info** — update title / artist directly on the device.
- **Delete songs** — removes from media store.
- **Swipeable rows** — swipe left to reveal a "More" button with context actions.

### 📦 Auto‑Updater

- Checks GitHub Releases on startup for new versions.
- In‑app download + install (triggers system package installer).
- Works with `FileProvider` for Android N+.

### 🎨 UI / UX

- Manual **light / dark mode** toggle.
- **Mini‑player bar** floating at the bottom — ambient‑colored, automatically adjusting text contrast.
- **AnimatedVisibility** transitions (slide + fade) between listing and Now Playing.
- **Fast scroller** with alphabetical index (letters A–Z).
- **Edge‑to‑edge** display with transparent system bars.

---

## 🛠️ Tech Stack

| Category | Technology |
| :--- | :--- |
| **Language** | Kotlin |
| **UI** | Jetpack Compose (Material 3) |
| **Audio** | Media3 ExoPlayer + MediaSession |
| **Image Loading** | Coil Compose |
| **Adaptive Colors** | Palette API (androidx.palette) |
| **MP3 Tags** | mp3agic 0.9.1 |
| **YT BotGuard** | ZemerCipher (PoToken generation) |
| **Logging** | Timber |
| **Drag & Drop** | reorderable 2.4.0 |
| **System UI** | Accompanist SystemUIController |
| **Navigation** | State‑based (manual, no NavHost) |
| **Persistence** | SharedPreferences (JSON) |
| **Lyrics** | lrclib.net, lyrics.ovh, some‑random‑api, lrcmux |

---

## 📂 Project Structure

```text
app/src/main/java/com/ianocent/musicplayer/
├── data/
│   ├── Song.kt                        # Song & AudioFormat data classes
│   ├── MonthlyRecap.kt                # (in Song.kt) Monthly recap data class
│   ├── Playlist.kt                    # Playlist data class
│   ├── MusicRepository.kt             # Local song scanning via MediaStore
│   ├── AlbumArtLoader.kt              # Album art decode + Palette extraction
│   ├── LyricRepository.kt             # Synced / plain lyrics from multiple APIs
│   ├── StreamRepository.kt            # JioSaavn search + stream URL
│   ├── YTMusicRepository.kt           # YouTube Music search + adaptive formats
│   ├── SoundCloudRepository.kt        # SoundCloud search + client‑id extraction
│   ├── MetadataWriter.kt              # MP3 ID3 tag writer (mp3agic)
│   └── DownloadCompletionReceiver.kt  # Post‑download metadata + MediaStore update
├── player/
│   ├── PlaybackService.kt             # MediaSessionService foreground service
│   └── PlayerManager.kt               # Media3 controller wrapper
├── viewmodel/
│   └── MusicViewModel.kt              # Central state — playback, queue, search, download, recap
├── ui/
│   ├── NowPlayingScreen.kt            # Full player overlay + lyrics + controls
│   ├── SongCardGenerator.kt           # "Now Playing" card generator
│   ├── LyricCardGenerator.kt          # Lyric quote card generator
│   ├── PlaylistCardGenerator.kt       # Playlist summary card generator
│   └── theme/
│       ├── Color.kt                   # Color palette
│       ├── Theme.kt                   # Material3 theme configuration
│       └── Type.kt                    # Typography scale
├── IanPlayerApplication.kt            # Application class (ZemerCipher init)
├── MainActivity.kt                    # Single Activity — all listing UI + navigation
└── UpdateManager.kt                   # GitHub release checker + APK downloader
```

---

## 🔧 Build & Run

1. Clone the repository:
   ```bash
   git clone https://github.com/ianocent/IanPlayer.git
   ```
2. Open in **Android Studio Hedgehog (2023.1.1)+**.
3. Sync Gradle and run on a device / emulator running **Android 7.0 (API 24)+**.

> **Note:** The `zemer-cipher` dependency fetches a BotGuard binary (`.so`) at runtime for YouTube PoToken generation. An internet connection is required on first launch.

---

## 📄 License

This project is open source. Feel free to fork, modify, and learn from it.
