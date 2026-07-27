<div align="center">
  <h1>🎵 Ian Player</h1>
  <p><b>A modern native Android music player — local library, YouTube Music streaming, synced lyrics, wave record, and more.</b></p>

  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android" alt="Android" />
  <img src="https://img.shields.io/badge/Kotlin-2.1+-7F52FF?style=flat-square&logo=kotlin" alt="Kotlin" />
  <img src="https://img.shields.io/badge/Compose-Material3-0081CB?style=flat-square" alt="Compose" />
  <img src="https://img.shields.io/badge/Min%20SDK-24-333?style=flat-square" alt="Min SDK" />
  <img src="https://img.shields.io/badge/Version-5.1.5-8A2BE2?style=flat-square" alt="Version" />
</div>

---

## 📥 Download

| Asset | Link                                                                                                          |
| :--- |:--------------------------------------------------------------------------------------------------------------|
| **APK (Latest)** | [IanPlayer-v.5.1.5.apk](https://github.com/ianocent/ianplayer/releases/download/v5.1.5/IanPlayer-v.5.1.5.apk) |
| **Release Notes** | [v5.1.5](https://github.com/ianocent/IanPlayer/releases/tag/v5.1.5)                                           |

---

## ✨ Features

### 🎧 Core Playback

- Auto‑scans all local audio via `MediaStore` (MP3, M4A, FLAC, WAV, OGG).
- Powered by **Media3 ExoPlayer** with `MediaSessionService` for background playback.
- Full notification with album art, title, and artist — works on lock screen.
- **Next / Previous / Seek / Shuffle / Repeat** (Off → All → One).
- Real‑time progress slider with manual seeking.
- Queue & Up-Next management with drag‑to‑reorder.
- **Audio focus** handling (duck on incoming calls, pause on headphone unplug).
- **Crossfade** between tracks (150ms volume fade in/out).
- **Pre-fetch** next track automatically when 8 seconds remaining.
- **Player state persistence** — queue survives app restart.
- **Smart auto-fill UpNext** — when queue runs low (~5 remaining), auto-searches YouTube Music for related songs and appends them to queue (skips already-played songs).
- **Played song tracking** — tracks `playedSongIds` set; auto-fill and queue logic skip songs already played in current session.

### 🌐 YouTube Music Streaming

- Search YouTube Music via **InnerTube API** (WEB_REMIX client).
- **PoToken / BotGuard** attestation via ZemerCipher WebView.
- **Multi-client fallback** (WEB → ANDROID → IOS → Invidious) if stream fails.
- **Highest available bitrate** (up to 320k AAC / Opus).
- **Three-tier stream cache** (in-memory LRU → Room DB → file-based disk cache for album art) with URL expiry tracking.
- **Smart engine tuning** — balanced buffer (2x default) for smooth playback on all network conditions without wasting memory on low-end devices; HW-first decoder selection with fallback (supports Qualcomm, MediaTek, Exynos, Kirin).
- **Stream metadata persistence** — stream song data survives app restart via SharedPreferences cache (enables playlists with stream songs to work across sessions).
- Genre browsing (10 genres: Pop, Rock, Hip Hop, R&B, Electronic, Jazz, Classical, Country, Indie, Metal).
- Trending section.
- Infinite scroll on search results.

### ⬇️ Download & Metadata

- Download any streamed song to `Music/IanPlayer/`.
- **ID3 metadata** (title, artist, album, embedded album art) written via **mp3agic**.
- Download quality dialog with "Best" badge when multiple formats available.
- MediaStore entry updated after download.

### 📱 Now Playing Screen

- Custom Figma design ported 1:1 to Compose.
- **Ambient color** — background, controls, slider, and lyrics auto-tinted from album art dominant color (`Palette API`).
- **Animated gradient background** (rotating, 30-second cycle).
- **Hero animation** — mini-art scales/offsets to full-art on transition.
- **Synced lyrics** (LRC) from lrclib.net with auto-scroll and active-line highlight.
- Falls back to **plain lyrics**.
- **Lyric card generator** — select lines to create a quote-style card.
- **Drag-to-dismiss** (swipe down) with scale-fade-spring animation.
- **Wave Record** button — record animated lyric video with synced lyrics overlay.

### 🖼️ Card Generators

| Card | Description |
| :--- | :--- |
| **Song Card** | Now‑playing card with blurred art background, accent bar, song info. |
| **Lyric Card** | Select lyric lines → stylized quote card. |
| **Playlist Card** | Playlist summary with song list; saved to gallery + shareable. |
| **Recap Card** | Monthly music recap card (Wrapped-style) with stats. |

All cards are rendered with Compose `GraphicsLayer`, saved to `Pictures/IanPlayer/` with transparent rounded corners, and can be shared.

### 📊 Monthly Music Recap

- Spotify Wrapped-style summary every month.
- **Total plays**, **total minutes**, **top 5 artists**, **top 3 songs**.
- Auto-generated **taste commentary** based on listening patterns.
- Dismissible recap banner in Songs tab.
- Debug trigger available (tap debug button in Songs tab).

### 🌊 Wave Record (Animated Lyric Video)

- **Animated lyric line** with crossfade between lines.
- **Screen recording** via `MediaProjection` (video only, no system audio).
- **VP9 + WebM** encoding via `MediaCodec` + `MediaMuxer` with chroma green background (`#00B140`) for keyable transparency in editors.
- **Foreground service** for recording (`WaveProjectionService`).
- **Save to gallery** — video saved to `Pictures/IanPlayer/`.
- **Save static image** — PNG with transparent rounded corners.
- **Microphone fallback** if system audio capture unavailable.

### 📚 Library & Playlists

- **Tabs**: Songs | Albums | Stream | Playlists.
- **Sort modes**: A‑Z, Recently Added, Most Played (play-count tracking).
- **Favorites filter** — toggle to show only favorites.
- **Album grouping** with drill-down detail view, play-all / shuffle-all FAB.
- **Custom playlists**: create, rename, add/remove songs via checkbox dialog + search.
- **Stream songs in playlists** — stream songs persist across app restarts and display correctly in playlist details.
- **Drag‑to‑reorder** songs inside playlists.
- **Custom playlist image** (pick from gallery via Coil).

### 🎤 Voice Search

- **SpeechRecognizer** with Indonesian language support ("id-ID").
- **1-minute auto-stop** timeout.
- **RMS level visualization** (animated spectrum bars).
- **RECORD_AUDIO permission** handling with rationale dialog.
- Results populate search query and switch to Stream tab.

### 🔧 Song Management

- **Edit song info** — update title/artist directly on device.
- **Delete songs** — removes from media store with **Undo** (Snackbar).
- **Swipeable rows** — swipe left to reveal action menu.

### 🎨 UI / UX

- Manual **light / dark mode** toggle.
- **Dynamic color** (Material You, Android 12+).
- **Edge‑to‑edge** display with transparent system bars.
- **Mini‑player** with **3 layouts**: Default / Floating / Queue — cycle by swiping down.
- **Fast scroller** with alphabetical index (A–Z).
- **Draggable scrollbar** for song lists.
- **Header shine animation** — gold shimmer on "ıanplayer" text every 90s.
- **Volume control slider** in listening pill.
- **Social media popup** — tap "ıanplayer" header → Facebook, Instagram, TikTok links.
- **AnimatedVisibility** transitions (slide + fade).

### 📦 Auto‑Updater

- Checks GitHub Releases on startup for new version.
- In‑app download + install (triggers system package installer).
- Works with `FileProvider` for Android N+.

### ⚡ Performance & Stability

- **Bounded image caches** — `LruCache` for bitmap memory (4MB low-res, 8MB high-res); prevents OOM on large libraries.
- **File-based disk cache** for remote album art (`cacheDir/album_art/`) — avoids re-downloading art on every viewport entry; auto-cleaned by OS.
- **Thread-safe stream URL cache** — `Collections.synchronizedMap` wrapping `LinkedHashMap` with LRU eviction (50 entries); multiple coroutines can read/write safely.
- **`ConcurrentHashMap` for preResolvedIds** — eliminates race conditions during background search pre-resolution.
- **All JSON serialization offloaded to `Dispatchers.IO`** — playlist saves, stream cache saves, play count/history writes no longer block main thread.
- **Cancellable background jobs** — genre fetch and art load jobs properly cancelled in `onCleared()` to prevent leaks.
- **Queue race fix** — `playSong()` reads and writes queue within same coroutine block; no more overwritten concurrent mutations.

---

## 🦾 Gesture Guide

Ian Player uses swipe and drag gestures throughout. Here's a complete reference:

### Mini-Player (Bottom Bar)

| Gesture | Action                                                          |
| :--- |:----------------------------------------------------------------|
| **Swipe UP** on mini-player | Open Now Playing Screen                                         |
| **Swipe DOWN** on mini-player | Cycle mini-player layout (Default → Floating → Queue → Default) |
| **Swipe LEFT** on mini-player | Volume control slider                                             |
| **Tap** mini-player | Open Now Playing Screen                                         |

### Now Playing Screen

| Gesture | Action |
| :--- | :--- |
| **Swipe DOWN** anywhere | Dismiss Now Playing Screen (drag-to-dismiss with scale animation) |
| **Drag progress slider** | Seek through track |
| **Tap ⇤ / ⇥ buttons** | Previous / Next track |
| **Tap shuffle icon** | Toggle shuffle mode |
| **Tap repeat icon** | Cycle repeat: Off → All → One |
| **Drag Up-Next handle** | Reorder queue items |
| **Tap heart icon** | Toggle favorite |
| **Tap record button** | Open Wave Record sheet |
| **Tap lyric lines** (LRC) | Select lyrics for card generation |

### Song List (Songs Tab)

| Gesture | Action |
| :--- | :--- |
| **Swipe LEFT** on a song row | Reveal "More" action menu (Edit / Delete / etc.) |
| **Tap** a song | Play it |
| **Drag scrollbar** (right edge) | Fast-scroll through list |
| **Drag fast-scroller** (alphabetical) | Jump to letter A–Z |

### Playlist List (Playlists Tab)

| Gesture | Action |
| :--- | :--- |
| **Swipe LEFT** on a playlist card | Reveal "More" action menu (Edit / Delete / etc.) |
| **Tap** a playlist | Open playlist detail |

### Playlist Detail

| Gesture | Action |
| :--- | :--- |
| **Drag ☰ handle** on song rows | Reorder songs in playlist |
| **Tap +** button | Add songs to playlist |

### Listening Pill (Volume)

| Gesture | Action |
| :--- | :--- |
| **Drag VERTICALLY** on pill | Move pill up/down along screen edge |
| **Drag HORIZONTALLY** on pill | Show/hide volume slider |
| **Drag volume slider** | Adjust media volume |

### Stream Tab

| Gesture | Action |
| :--- | :--- |
| **Tap search bar** | Search YouTube Music |
| **Scroll down** | Infinite load more results |
| **Tap genre card** | Browse songs by genre |
| **Tap trending card** | View trending songs |

---

## 🛠️ Tech Stack

| Category | Technology |
| :--- | :--- |
| **Language** | Kotlin |
| **UI** | Jetpack Compose (Material 3) |
| **Audio** | Media3 ExoPlayer + MediaSession |
| **Image Loading** | Coil Compose |
| **Adaptive Colors** | Palette API (androidx.palette) |
| **Database** | Room (cached stream URLs) |
| **Persistence** | SharedPreferences (playlists, favorites, play counts, history, stream song cache) |
| **Disk Cache** | File-based album art cache (`cacheDir/album_art/`, WEBP 85%) |
| **YouTube Streaming** | InnerTube API + ZemerCipher (PoToken/BotGuard) |
| **Screen Recording** | MediaProjection + VP9 + WebM + MediaMuxer |
| **MP3 Tags** | mp3agic 0.9.1 |
| **Lyrics** | lrclib.net |
| **Drag & Drop** | reorderable 2.4.0 |
| **Logging** | Timber |

---

## 📂 Project Structure

```text
app/src/main/java/com/ianocent/musicplayer/
├── data/
│   ├── Song.kt                        # Song, AudioFormat, MonthlyRecap, StreamSearchResult data classes
│   ├── Playlist.kt                    # Playlist data class
│   ├── ElementRect.kt                 # ElementRect data class (for card capture)
│   ├── UpdateInfo.kt                  # Update info data class
│   ├── CachedStreamUrl.kt            # Room entity for cached stream URLs
│   ├── StreamCacheDao.kt             # Room DAO for stream cache
│   ├── AppDatabase.kt                # Room database (cached_stream_urls table)
│   ├── MusicRepository.kt            # Local song scanning via MediaStore
│   ├── AlbumArtLoader.kt             # Album art decode + Palette extraction
│   ├── LyricRepository.kt            # Synced/plain lyrics from lrclib.net
│   ├── YTMusicRepository.kt          # YouTube Music InnerTube API + stream resolution + cipher
│   ├── MetadataWriter.kt             # MP3 ID3 tag writer (mp3agic)
│   └── DownloadCompletionReceiver.kt # Post-download metadata + MediaStore update
├── player/
│   ├── PlaybackService.kt            # MediaSessionService foreground service
│   ├── PlayerManager.kt              # Media3 controller wrapper
│   └── WaveProjectionService.kt      # Foreground service for wave screen recording
├── viewmodel/
│   ├── MusicViewModel.kt             # Central state — playback, queue, search, download, recap, favorites, stream cache
│   └── PlaybackController.kt         # Playback logic — play/resume/pause, queue management, auto-fill UpNext, shuffle/repeat
├── ui/
│   ├── NowPlayingScreen.kt           # Full player overlay + lyrics + controls + up-next
│   ├── SongCardGenerator.kt          # "Now Playing" card generator
│   ├── LyricCardGenerator.kt         # Lyric quote card generator
│   ├── PlaylistCardGenerator.kt      # Playlist summary card generator
│   ├── RecapCardGenerator.kt         # Monthly recap card generator
│   ├── SongListComponents.kt         # Reusable song list composables (swipeable rows, action dialogs)
│   ├── WaveRecordCard.kt             # Audio visualizer + screen recording sheet
│   ├── ResponsiveSnapList.kt         # Full-height snap list + draggable scrollbar
│   └── theme/
│       ├── Color.kt                  # Color palette
│       ├── Theme.kt                  # Material3 theme configuration
│       └── Type.kt                   # Typography scale
├── IanPlayerApplication.kt           # Application class (ZemerCipher init)
├── MainActivity.kt                   # Single Activity — all listing UI + navigation + tabs
└── UpdateManager.kt                  # GitHub release checker + APK downloader + installer
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
