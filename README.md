<div align="center">
  <h1>🎵 Ian Player</h1>
  <p><b>A modern native Android music player built from scratch using Kotlin + Jetpack Compose.</b></p>
  <p><i>Created as a personal project to learn modern Android development while building something that is actually used in daily life.</i></p>
  <!-- Placeholder Badges (Can be replaced with shields.io) -->
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android" alt="Android" />
  <img src="https://img.shields.io/badge/Kotlin-1.9+-7F52FF?style=flat-square&logo=kotlin" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Compose-Material3-0081CB?style=flat-square" alt="Compose" />
</div>
---
## 📥 Downloads (Assets)
Get the latest version of Ian Player for your Android device:
* [📦 Download APK (Latest Release)](https://github.com/ianocent/ianplayer/releases/download/v1.2.0/app-release.apk)
* [📜 Release Notes](https://github.com/ianocent/IanPlayer/releases/tag/v1.0.0)
* [💻 Source Code (.zip)](https://github.com/ianocent/IanPlayer/archive/refs/tags/v1.0.0.zip)
---
## ✨ Main Features
### 🎧 Core Playback
* Automatic scanning of all songs on the device (supports MP3, M4A, FLAC, WAV, OGG) via MediaStore.
* Playback engine powered by Media3 ExoPlayer with MediaSessionService.
* Music continues playing in the background and appears in the notification shade / lockscreen.
* Supports Next, Previous, Seek, Shuffle, and Repeat (off / all / one).
* Realtime progress bar with manual seeking.
### 📱 Now Playing Screen
* Custom design created in Figma, ported 1:1 to Compose.
* Ambient color — UI colors (background, controls bar, slider, lyric card) are automatically extracted from the dominant color of the album art for each song.
* Uses Palette API, with colors adjusted for contrast in light/dark mode.
* Synced lyrics (LRC timestamps) from lrclib.net, with auto-scroll and active line highlighting following the song position (similar to YouTube Music).
* Falls back to plain lyrics if synced lyrics are not available.
* Swipe-down gesture to close Now Playing, with smooth animation (scale + fade + spring).
* Album art is decoded with downsampling to keep scrolling and rendering lightweight.
### 🖼️ Lyric Card Generator (Unique Feature)
* Select one or multiple lyric lines directly from the lyric view.
* Generate custom-styled lyric cards (blurred album art as background, big quote mark, accent bar with song info).
* Design created differently from the style of YouTube Music / Spotify / Genius.
* Rendered using Compose GraphicsLayer, directly saved to the gallery (Pictures/IanPlayer).
### 📚 Library & Playlist
* Song listing with album art thumbnails and a fast search bar for filtering.
* Custom playlists: create, add songs via dialog with checkboxes + search, open and play playlist contents.
* Persistent playlists — saved to SharedPreferences as JSON.
* Playlist data remains after app restart.
### 🎨 UI/UX
* Manual light & dark mode toggle.
* Mini player bar shaped as a floating card, matching the ambient color of the album art.
* Text contrast on the mini player automatically adjusts (black/white depending on background luminance).
* Open/close Now Playing transitions using AnimatedVisibility with slide + fade.
---
## 🛠️ Tech Stack
| Category | Technology |
| :--- | :--- |
| **Language** | Kotlin |
| **UI Toolkit** | Jetpack Compose (Material3) |
| **Audio Playback** | Media3 ExoPlayer + MediaSessionService |
| **Adaptive Colors** | Palette API (androidx.palette) |
| **Sync Lyrics** | lrclib.net API (free, no API key required) |
| **Persistence** | SharedPreferences (manual JSON) |
| **System UI** | Accompanist SystemUiController |
---
## 📂 Project Structure
```text
app/src/main/java/com/ianocent/musicplayer/
├── data/
│ ├── Song.kt # Song data class
│ ├── Playlist.kt # Playlist data class
│ ├── MusicRepository.kt # MediaStore query for song scanning
│ ├── AlbumArtLoader.kt # Decode album art + extract dominant color
│ └── LyricRepository.kt # Fetch & parse synced/plain lyrics from lrclib
├── player/
│ ├── PlaybackService.kt # MediaSessionService (background playback)
│ └── PlayerManager.kt # Player control wrapper (play, pause, seek, etc.)
├── viewmodel/
│ └── MusicViewModel.kt # Main state management (single source of truth)
├── ui/
│ ├── NowPlayingScreen.kt # Full player screen + lyrics + controls
│ ├── LyricCardGenerator.kt # Custom lyric card generator & save
│ └── theme/ # Colors, typography, Material3 theme
└── MainActivity.kt # Listing screen, navigation, entry point
