# IanPlayer PC — Architecture & Porting Map

Referensi file Android ada di eksplorasi codebase `com.ianocent.musicplayer` (v6.3.4, Kotlin/Compose, MVVM-lite, 1 Activity ~4700 baris + `PlayerGateway.kt` 1250+ baris).

## 1. Prinsip port

- Android: 1 Activity besar + 1 ViewModel besar + `PlayerGateway` sebagai sumber kebenaran playback. Di PC: pecah per area (di bawah), tapi **jaga logic 1:1** (queue machine, prefetch, auto-fill, cache TTL). Port adalah transplantasi, bukan improvisasi.
- UI logic (Compose) tidak diport kode; diport struktur + token dari `03-DESIGN-SYSTEM.md`.
- Semua scraping (InnerTube/Tidal/iTunes/Deezer/lyrics/Spotify) itu HTTP+JSON murni — port langsung ke `QNetworkAccessManager` + `QJsonDocument`.

## 2. Class mapping Android → PC

| Android (Kotlin) | PC (C++/Qt) | Catatan |
|---|---|---|
| `MusicViewModel.kt` | `LibraryController` + `StreamController` | pecah 2; Android 1 file raksasa, jangan tiru — pecah by area |
| `PlayerGateway.kt` | `PlaybackController` + `QueueController` | queue truth tetap 1 tempat |
| `PlaybackService.kt` (Media3) | libmpv instance + SMTC bridge | tidak ada service Windows; app langsung |
| `MediaController` (Media3 session) | signal/slot Qt langsung |andi tidak perlu IPC session |
| `MusicRepository.kt` (MediaStore) | `LibraryScanner` + MetadataReader (TagLib) | MediaStore tidak ada di Windows; scan filesystem |
| `SongStore.kt` (Prefs JSON: playlists, favorites, history, queue) | SQLite tables | pindah ke DB biar konsisten |
| `YTMusicRepository.kt` | `InnertubeClient` + `StreamResolver` | scraping port; streaming URL via yt-dlp |
| `TidalRepository.kt` | `TidalClient` | pure HTTP, port langsung |
| `LyricRepository.kt` | `LyricsClient` | 8 providers, port langsung |
| `SongDownloader.kt` + `DownloadCompletionReceiver` | `DownloadTaskQueue` (module sendiri) | QNetworkAccessManager + antrian |
| `MusicMetadataService.kt` | `TagEnricher` | iTunes + Deezer |
| `MetadataWriter.kt` (mp3agic/jaudiotagger) | TagLib | satu lib untuk baca & tulis, semua format |
| `RecapBuilder.kt` | `RecapBuilder` | logic sama, query SQL |
| `UpdateManager.kt` | `Updater` | repo PC terpisah |
| `IanDeepLinks.kt` | protocol handler + CLI args | registry |
| `HeadsetReceiver.kt` | MMDevice API notifications | audio device switch |
| `IanVoiceAssistantService.kt` | (v2) whisper.cpp/Windows Speech | stretch |
| `WaveProjectionService.kt` | (v2) WASAPI loopback + Media Foundation | stretch |
| `SettingsStore.kt` | `SettingsStore` (QSettings) | key & default 1:1 |
| Haze glass (`IanGlass.kt`) | QML `MultiEffect` stack | design tokens `03-DESIGN-SYSTEM.md` |
| `AmbientPaletteController` | `AmbientPalette` (C++ + QML) | quantize artwork 2 warna |
| `PlayerCanvas.kt` + `CanvasVideoSource.kt` | `CanvasRenderer` QML ShaderEffect + libmpv 2nd instance | 4 mode |
| Coil | `ThumbnailProvider` (QNAM + LRU disk cache) | |
| Room `AppDatabase.kt` | QtSql SQLite | schema di bawah |
| `zemer-cipher` (PoToken via WebView) | bundel `yt-dlp.exe` | keputusan besar di `00-OVERVIEW.md` |

## 3. Threading model

```
Main thread        : QApplication, semua QML, PlaybackController slots
Worker (QtConcurrent): scan metadata, thumbnail extract, tag enrich
Event thread (1x)  : mpv_wait_event loop  -> emit Qt signal -> main thread
QProcess threads   : yt-dlp resolve, download SOS queue
Network (QNAM)     : async by design, debounce timer di main thread
```

Rules:
- Dilarang call libmpv dari worker thread; semua via controller di main thread, command async.
- SQLite connection per thread (open `QSqlDatabase` di thread yang pake, bukan share).
- Debounce search 250-300ms, jangan spam InnerTube.
- Streaming watch out semuanya lewat signal; no busy-wait epoch polling (Android pakai polling 100/500ms — di Qt pakai `QTimer` 500ms untuk time-pos mirror, cukup).

## 4. Flow streaming (bagian paling penting)

```
User pilih track stream (videoId)
        |
QueueController: queue.push(track)
        |
PlaybackController.playNext():
    cek cache (var cached_stream_urls, expiresAtMs masih valid?)
        |-- hit -> URL googlevideo langsung
        |            |
        |-- miss  -> panggil StreamResolver (QProcess yt-dlp -J)
                      decode JSON, pick audio format bitrate tertinggi,
                      isi cache (resolveAtMs, expiresAtMs = parse 'expire=' URL)
        |
libmpv loadfile(URL, mode=replace) + metadata passthrough
        |
Saat time-pos >= duration - 8s -> trigger prefetch next queue item
```

Paritas Android yang gak boleh hilang:
- Expiry-aware re-resolve (URL expired -> resolve ulang otomatis).
- Retry-on-error: kalau libmpv gagal load URL yang masih "valid" di cache, resolve ulang paksa tanpa cek TTL.
- `autoFillUpNext` saat queue temp: fetch 5 related, filter noise.
- Mixed queue local + stream works (format unified `Track` struct: `source: local|youtube|tidal`, `localId` OC `videoId`).

Anti-pattern yang Android hindari (tetap dihindari):
- Gak pernah hardcode AK selain file keys yang di-ignored git.
- Jangan percaya `expire` secara naive; re-resolve kalau mpv bilang stream failure.

## 5. Database schema (SQLite, QtSql)

Pemetaan dari store set Android (Room + SharedPreferences) — semua dipindah ke DB:

```sql
-- dari MusicRepository scan M1
CREATE TABLE songs (
  id INTEGER PRIMARY KEY,
  path TEXT UNIQUE, title TEXT, artist TEXT, artist_id INTEGER,
  album TEXT, album_id INTEGER, track_no INTEGER, year INTEGER,
  duration_ms INTEGER, bitrate INTEGER, codec TEXT,
  art_256 TEXT, art_64 TEXT,           -- path thumbnail cache
  added_ts INTEGER, modified_ts INTEGER, avail INTEGER DEFAULT 1
);
CREATE TABLE albums   (id INTEGER PRIMARY KEY, name TEXT, artist TEXT, art_256 TEXT);
CREATE TABLE artists  (id INTEGER PRIMARY KEY, name TEXT, art_256 TEXT);
CREATE INDEX idx_songs_artist ON songs(artist_id);

-- dari SongStore.kt
CREATE TABLE playlists      (id INTEGER PRIMARY KEY, name TEXT, image TEXT, created_ts INTEGER);
CREATE TABLE playlist_songs (playlist_id INTEGER, song_id INTEGER, position INTEGER,
                             PRIMARY KEY(playlist_id, song_id));
CREATE TABLE favorites      (song_key TEXT PRIMARY KEY);          -- song_key = "local:<id>" | "yt:<videoId>"
CREATE TABLE play_counts    (song_key TEXT PRIMARY KEY, count INTEGER, last_ts INTEGER);
CREATE TABLE play_history   (song_key TEXT, played_ts INTEGER, duration_ms INTEGER);  -- window 90 hari

-- dari Room v3 Android (schema identik)
CREATE TABLE cached_stream_urls (video_id TEXT PRIMARY KEY, url TEXT,
                                 resolved_at_ms INTEGER, expires_at_ms INTEGER);
CREATE TABLE lyric_cache (key TEXT PRIMARY KEY, synced_json TEXT, plain_text TEXT, cached_at_ms INTEGER);
CREATE TABLE canvas_cache (song_key TEXT PRIMARY KEY, matched INTEGER, confidence REAL,
                           spotify_id TEXT, canvas_url TEXT, canvas_type TEXT, artist_name TEXT,
                           url_expires_at INTEGER, source TEXT, updated_at INTEGER);
```

Track key universal: `local:<rowid>` / `yt:<videoId>` — ini yang mengganti konsep key campuran Android ("yt:videoId" vs "local:title|artist|dur").

## 6. SMTC bridge (System Media Transport Controls)

Windows: metadata playback muncul di volume OSD, lock screen, dan jalan via media keys. API-nya WinRT (`Windows.Media.SystemMediaTransportControls`), bisa dipakai dari C++/WinRT (MSVC, tanpa C++/CX).

```
SMTCTaskbar (module):
  setTitle / setArtist / setArtwork(thumbnail 200x200 png)
  playbackStatus: paused|playing
  button events: play, pause, next, prev -> PlaybackController
```

Kalau integrasi WinRT ribet di CMake build ini, alternatif: pakai header C++/WinRT standalone (satu props include). Catatan risiko di `06-RISK-LOG.md`.

## 7. IanGlass QML stack (paritas Haze)

Haze Android = blur + vibrancy + tint di layer di belakang. QML ekuivalen:

```qml
// GlassPanel.qml — dipake di mini player bar, pill, dialog scrim
Rectangle {
    color: Theme.surface1
    opacity: 1
    layer.enabled: glassEnabled
    layer.effect: MultiEffect {
        blurEnabled: true
        blur: glassBlur / 40        // settings glass_blur 4-40
        blurMax: 32
        saturation: 0.1 + glassVibrancy * 0.4
        // lens/highlight: shader sendiri tipis di atas (bukan MultiEffect)
    }
}
```

- `VisualPerformanceTier`: cek `QQuickWindow::rendererInterface()->graphicsApi()` dan GPU name via `glGetString(GL_RENDERER)`. GPU lemah / software renderer (llvmpipe / ANGLE swiftshader) -> glass off + panel flat (sama logic `VisualPerformanceTier` Android).
- Konsisten: internal GlassConfig bounds sama Android (blur 4-40, vibrancy 0.0-0.6, motion_scale 0.5-2.0).

## 8. Canvas rendering & matching

- `generative`: QML `ShaderEffect` fragment shader aurora (shader ditulis nanti; freeze saat `playing=false` — paritas Android, aurora berhenti saat pause).
- `video`: libmpv instance kedua `ao=null` (video tanpa audio), load file matched, loop. Matching logic `CanvasVideoSource`: cari `<title>.<ext>` di folder audio yang sama / folder `IanCanvas`; match title+artist, fallback duration ±5s.
- `kenburns`: `Image` + `SequentialAnimation` scale 1.0->1.15 + pan, duration `motion_scale` * 30s.
- Spotify: port repos dari `data/canvas/` Android — butuh `sp_dc` cookie dari user (catatan security di `00-OVERVIEW.md`, jangan ship default credential).
- Canvas fade before upnext: setting `canvas_fade_before_upnext`.

## 9. Settings key mapping (QSettings)

File: `%APPDATA%\IanPlayer\ianplayer.ini`. Semua keyAndroid dipetakan sama (list lengkap di `05-FEATURE-PARITY.md`):
- `is_dark_mode` (true), `is_pill_at_bottom`, `mini_layout_index`, `sort_mode`, `tab_order`, `dont_show_update`, `user_name`, `avatar_uri`, `banner_uri`
- `canvas_mode`, `canvas_intensity`, `canvas_blur`, `canvas_dim`, `canvas_fade_before_upnext`
- `glass_enabled`, `glass_blur`, `glass_vibrancy`, `glass_lens`, `glass_highlight`
- `motion_scale`, `reduce_motion`
- `seek_bar_style`, `lyrics_style`, `crossfade_duration_ms`
- `spotify_sp_dc` (input user; jangan pernah shipped default)
- baru PC: `library_folders[]`, `download_dir`, `cache_size_mb`, `normalize_volume_loudness` (opsional v2)

## 10. Modul yang tidak ada di desktop (dan penggantinya)

| Android | Ganti dengan |
|---|---|
| MediaStore ContentResolver | filesystem scan + DB |
| Foreground service + notification player | no service; app window + SMTC |
| SpeechRecognizer/TTS fused | v2: whisper.cpp / Windows Speech |
| AudioPlaybackCapture (projection) | v2: WASAPI loopback |
| DownloadManager system | QNAM module |
| WebView BotGuard (zemer-cipher) | bundle yt-dlp process |
| PendingIntent notification buttons | SMTC buttons + mini player bar |
| HeadsetReceiver BT ACL | MMDevice API notifications |
