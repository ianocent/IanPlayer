# IanPlayer PC — Milestones & Phases

Estimasi: part-time, 1 orang, sudah bisa C++ tapi baru belajar Qt/QML. Kalau full-time, bagi 2. Angka boleh meleset, urutan tidak boleh.

```
M0 Fondasi         (1-2 minggu)  — toolchain + skeleton + audio jalan
M1 Local Library   (2-3 minggu)  — scan, metadata, DB, UI library
M2 Playback Penuh  (2 minggu)    — queue, shuffle, mini player, SMTC
M3 Streaming       (3-4 minggu)  — yt-dlp + InnerTube + Tidal  <- risiko terbesar
M4 Lyrics+Download (2-3 minggu)  — 8 sumber lirik + downloader + tag
M5 Visual          (3-4 minggu)  — glass, canvas, recap, settings
M6 Desktop & Rilis (2-3 minggu)  — update, packaging portable, perf
```

Total realistis: 3-5 bulan part-time. M3 adalah milestone risiko terbesar (YouTube bisa berubah kapan saja). Itu alasan M0-M2 wajib dulu: app lokal harus sudah berguna sebelum nunggu risiko streaming.

Aturan:
1. Jangan mulai milestone baru sebelum milestone sebelumnya lolos exit criteria.
2. Tiap akhir phase: build release bersih + app harus jalan + commit.
3. Baca kode Android SEBELUM menulis code yang diport. File terpenting: `PlayerGateway.kt` (1.250+ baris, jantung semua logic player). Peta lengkap di `02-ARCHITECTURE.md`.

---

## M0 — Fondasi (1-2 minggu)

Goal: environment jalan, skeleton build, audio lokal + YouTube putar dari command line. Belum ada UI lengkap.

### Phase M0.1 — Environment (0.5-1 hari)

- [ ] Ikuti semua langkah `01-TOOLCHAIN.md` bagian 1: VS 2022, CMake, Qt 6.7 msvc64, vcpkg, Python.
- [ ] Download libmpv terbaru (SourceForge, folder files/libmpv), extract ke `third_party/mpv/`.
- [ ] Download `yt-dlp.exe` standalone terbaru (GitHub releases) ke `third_party/ytdlp/`. Yang exe standalone, bukan source tarball.
- [ ] Cek versi di Developer PowerShell: `cmake --version`, `yt-dlp.exe --version`, `vcpkg version`.

Exit: semua tool merespon di Developer PowerShell yang sama.

### Phase M0.2 — CMake skeleton (0.5-1 hari)

- [ ] Struktur repo sesuai `01-TOOLCHAIN.md` bagian 2.
- [ ] `CMakeLists.txt` root + `CMakePresets.json` + `vcpkg.json` (taglib + gtest).
- [ ] Target `ianplayer` build kosong: `main.cpp` + `AppWindow.qml` + `Theme.qml` singleton.
- [ ] Compile flags: `/W4 /permissive- /utf-8`.
- [ ] `.gitattributes` (CRLF) + `.gitignore` (`build/`, DLL third_party, file keys/secret).
- [ ] App manifest + icon.ico + window title "IanPlayer".
- [ ] String literal non-ASCII ditaruh di .qml, bukan inline di .cpp (hindari codepage masalah MSVC).

Pitfall:
- Path Qt berubah tergantung versi pas install; sesuaikan `CMAKE_PREFIX_PATH` di preset.
- Build hanya boleh dari Developer PowerShell VS 2022; dari PowerShell polos, `cl` tidak ada.

Exit: `cmake --preset debug && cmake --build --preset debug` menghasilkan `.exe`, dobel klik buka window gelap tanpa error QML.

### Phase M0.3 — libmpv audio chain (1-2 hari)

Satu class `PlaybackController`, fitur minimum dulu:

- [ ] `mpv_create` + set option: `volume=100`, `gapless-audio=weak`, `ao` default (WASAPI).
- [ ] `loadTrack(url)` async via `mpv_command_async`.
- [ ] Observe properties: `time-pos`, `duration`, `paused`, `eof-reached`, `metadata`. Implement event loop di thread sendiri (`mpv_wait_event`), emit signal Qt ke main thread. Wajib, karena mpv API tidak thread-safe.
- [ ] CLI test 1: `IanPlayer.exe "C:\path\test.flac"` — bunyi, pause/resume, volume, seek.
- [ ] CLI test 2: `IanPlayer.exe "https://youtube.com/watch?v=VIDEOID"` — spawn `yt-dlp.exe -J --no-download` via QProcess, parse JSON, ambil format audio terbaik, feed ke mpv.
- [ ] Cache resolusi di SQLite tabel `cached_stream_urls` (schema sama Android: videoId, url, resolvedAtMs, expiresAtMs). Parse `expire=` epoch dari URL googlevideo; kalau belum expired, pakai cache.
- [ ] Unit test gtest untuk parser expire.

Pitfall:
- Teardown order: hentikan playback, destroy mpv instance, baru quit app. Kalau kebalik, crash saat exit.
- Path dengan spasi: pakai `QProcess::setProgram` + `setArguments`, jangan gabung string.
- Error 403 saat play stream → update yt-dlp dulu, cek `06-RISK-LOG.md`.

Exit: smoke test "Hello Audio" (`01-TOOLCHAIN.md` bagian 5) pass.

### Phase M0.4 — App shell QML (0.5-1 hari)

- [ ] Layout dasar: tab bar 4 tab (Stream/Songs/Playlists/Albums) + area konten kosong.
- [ ] `Theme.qml` + font Inter ter-registrasi. Cek h1/h2 dan warna token merender.
- [ ] Tab reorderable via drag, persist `tab_order` di QSettings.
- [ ] Custom titlebar: `FramelessWindowHint` + drag region + `startSystemMove`/`startSystemResize` (sudah ada di Qt 6.7). Kerjakan sekarang, jangan diundur — menyusul jadi pekerjaan besar.

Pitfall:
- Tanpa drag region eksplisit, window tidak bisa digeser.
- Double-click titlebar untuk maximize: test manual, ini bug klasik frameless window.

Exit: window jalan dengan tab bar custom dan token dark mode.

**M0 exit criteria:** app start, audio lokal putar, YouTube via CLI putar, tab bar jalan, custom titlebar jalan.

---

## M1 — Local Library (2-3 minggu)

Goal: app jadi local music player berguna. Paritas Songs/Albums/Playlists Android, tapi data dari scan filesystem, bukan MediaStore.

### Phase M1.1 — Folder scan + metadata (3-4 hari)

- [ ] `LibraryScanner`: scan rekursif multi-folder yang dipilih user. Format: mp3, flac, m4a/aac, wav, ogg, opus. Skip `System Volume Information` dan folder aplikasi/sistem.
- [ ] TagLib: baca title, artist, album, track#, year, duration, bitrate, embedded album art.
- [ ] Fallback parsing `"Artist - Title"` dari nama file (port logic `MusicRepository.kt` Android 1:1).
- [ ] Thumbnail cache: ekstrak album art ke `%LOCALAPPDATA%\IanPlayer\thumbnails\` (256 & 64 px).
- [ ] SQLite via QtSql (driver bawaan). Schema: `songs, albums, artists, playlists, playlist_songs, favorites, play_history, play_counts`. Pemetaan penuh di `02-ARCHITECTURE.md`.
- [ ] Incremental scan: file yang sudah di DB (path + lastmodified) tidak di-scan ulang; file hilang ditandai unavail.
- [ ] `QFileSystemWatcher` untuk perubahan folder realtime (murah, wajib).
- [ ] UI: tombol "Scan Folder" + progress bar. Scan di `QtConcurrent`, UI tidak boleh beku.

Pitfall:
- Library besar (10k+ file): full scan itu I/O berat. Harus async, UI thread nol I/O.
- Long path Windows >260 char: enable manifest `longPathAware` (di `06-RISK-LOG.md`).
- QPSQL tidak; pakai QSQLITE saja dengan `PRAGMA journal_mode=WAL` supaya scan dan baca UI tidak saling blokir.

Exit: scan 1000+ file sukses, metadata & artwork masuk DB, app smooth.

### Phase M1.2 — Models + Library UI (3-4 hari)

- [ ] Model `QAbstractListModel` per entitas: SongsModel, AlbumsModel, ArtistsModel, PlaylistModel.
- [ ] UI 3 tab: Songs (row list), Albums (grid square), Playlists.
- [ ] Sort modes: title / artist / album / recently added / play count (paritas `sort_mode` Android).
- [ ] Search filter debounced 250ms via `QSortFilterProxyModel`.
- [ ] Fast scroller A-Z (paritas Android).
- [ ] Album detail & Artist detail screens (layout referensi `AlbumDetailView`).
- [ ] Favorites: tombol heart, persist di DB (Android simpan di Preferences JSON; di desktop pindahin ke DB).
- [ ] Context menu klik kanan: play next, add to playlist, favorite, show in Explorer. Fitur baru desktop, Android tidak punya.

Pitfall:
- Jangan reset model saat user sedang drag scrollbar; index invalid bikin jumpy.
- Semua database access dari thread sama (atau pakai connection per thread). SQLite default connection tidak boleh dilintas-thread.

Exit: search, sort, favorite, detail screens jalan dari data lokal.

### Phase M1.3 — Playlists (2 hari)

- [ ] CRUD: create, rename, delete, reorder songs via drag.
- [ ] Playlist image custom via file picker.
- [ ] Drag & drop dari list Songs ke sidebar Playlists.
- [ ] Context menu: add to queue, play next, remove dari playlist.

**M1 exit criteria:** pilih folder → library lengkap, semua tab berguna, playlist tersimpan, restart app mempertahankan semuanya.

---

## M2 — Playback Penuh (2 minggu)

Goal: player engine setara `PlayerGateway.kt` Android: satu sumber kebenaran queue, UI hanya konsumen.

### Phase M2.1 — PlaybackController lengkap (3-4 hari)

- [ ] Queue ops: playNow, playNext, add, remove, reorder, clear.
- [ ] Shuffle & repeat (off / all / one). Ikut state machine `PlayerGateway.kt`, jangan labu sendiri.
- [ ] Auto-next saat track selesai; seek presisi; volume + mute.
- [ ] Persist queue: simpan current queue + track + index, restore saat cold start (paritas `KEY_LAST_QUEUE/SONG/INDEX` Android).
- [ ] Skeleton prefetch: next track di-resolve saat 8 detik tersisa (logic Android). Runtime streaming baru pas M3, tapi hook-nya dibuat sekarang.

### Phase M2.2 — Now Playing + Mini player (3-4 hari)

- [ ] Now Playing panel yang bisa minimize (layout `03-DESIGN-SYSTEM.md` bagian 5.1).
- [ ] Mini player dock bawah, muncul saat queue ada isi (seperti Spotify).
- [ ] Keyboard shortcuts lengkap + overlay `?` (daftar di design doc).
- [ ] Klik artwork di mini player membuka Now Playing; minimize via tombol atau Esc.

### Phase M2.3 — Media keys & SMTC (1-2 hari)

- [ ] Integrasi SMTC (System Media Transport Controls): play/pause/next/prev dari keyboard media jalan walau fokus di app lain.
- [ ] Info "now playing" muncul di volume OSD Windows — gratis dari SMTC.
- [ ] Bonus: artwork di lock screen/hardware volume OSD saat playing.

Pitfall:
- Jangan bikin global keyboard hook sendiri; SMTC adalah jalur resmi Windows. Global hook itu rumit dan antivirus suka flag.
- Custom titlebar + fullscreen video (kalau nanti video mode): test maximize/minimize dulu dari taskbar.

Exit: tombol media keyboard jalan seperti Spotify.

### Phase M2.4 — Crossfade (1-2 hari)

- [ ] Dual-instance crossfade (paritas `PlayerGateway`: player kedua overlap, volume swap). Setting `crossfade_duration_ms` (0/100/300/700/1200).
- [ ] Android belum sempat expose UI-nya; di desktop expose penuh di settings.
- [ ] Baca implementasi Android (`PlayerGateway.kt` bagian 1012-1116) sebelum ngoding.

**M2 exit criteria:** queue, shuffle, repeat, restore, mini player, keyboard, SMTC jalan dengan library lokal. App sekarang layak dipakai harian. Glitch/library bug tinggal fix-fix minor saja.

---

## M3 — Streaming YouTube Music (3-4 minggu)

Goal: Stream tab setara Android: feeds, search 2 provider, related, queue campuran lokal+stream, deep link. Risiko terbesar project.

### Phase M3.1 — yt-dlp resolver robust (3-4 hari)

- [ ] `StreamResolver`: QProcess `yt-dlp -J --no-download`, timeout 10 detik, retry 1x, pilih format audio-only bitrate tertinggi.
- [ ] Cache di SQLite `cached_stream_urls`, parse `expire=` epoch (unit test — sama seperti Android), TTL-aware reuse.
- [ ] Prefetch dari M2.1: resolve next track di background saat 8 detik tersisa; cache hit = nol latency.
- [ ] Error UI mapping: "gagal resolve", "stream expired" (auto resolve ulang), "network error".
- [ ] Version check yt-dlp saat startup (`--version`); kalau bukan build terbaru, tampilkan hint update di settings.

### Phase M3.2 — InnerTube client (4-5 hari)

- [ ] `InnertubeClient` C++: POST JSON ke `youtubei/v1/*`, client WEB_REMIX (music). Endpoint yang Android pakai: `search` (filter songs), `browse` (home feed / trending), `next` (related/radio). Endpoint `player` di Android diport opsional — resolusi URL tetap lewat yt-dlp.
- [ ] API keys dari property file, seperti konsep `keys.properties` Android. Jangan hardcode di source.
- [ ] Genre list (~12) + moods (12) + time-of-day queries: port 1:1 dari `MusicViewModel.kt:117-177`.
- [ ] Noise filter: regex compilation/karaoke + filter konten India (port `isIndianContent` Android).
- [ ] Thumbnail loader pengganti Coil: `QNetworkAccessManager` + disk cache LRU di `%LOCALAPPDATA%\IanPlayer\imgcache` (max 200MB).

### Phase M3.3 — Tidal search (1-2 hari)

- [ ] Port `TidalRepository`: `api.tidal.com/v1/search/tracks`, public token sama Android.
- [ ] Tidal dipakai buat metadata + cover; playback tetap resolve lewat YouTube fallback (sama logic `SongSource` Android).
- [ ] Dual-provider search jalan bareng (paritas `StreamSearchController`): debounce + fan-out YTM & Tidal + eager pre-resolution + paging.

### Phase M3.4 — Stream UI (3-4 hari)

- [ ] Stream tab: home feed / trending, chips genre & mood, klik kandidat → play via resolver.
- [ ] Search screen: debounced 300ms, section 2 provider, paging infinite scroll.
- [ ] Related auto-fill Up Next: fetch 5 track relevan saat queue kosong (paritas `autoFillUpNext`, filtered).
- [ ] Now Playing menampilkan metadata stream (title, artist, artwork Tidal/YTM).

### Phase M3.5 — Integration endpoints (1-2 hari)

- [ ] Protocol handler `ianplayer://song?v=<videoId>` (untuk portable: app tulis registry `HKCU\Software\Classes` sendiri via toggle "Enable" di settings, per-user tanpa elevasi; detail `06-RISK-LOG.md` R18).
- [ ] Open-with: URL `youtube.com/watch?v=`, `youtu.be`, `vnd.youtube:` — buka app lalu play track.
- [ ] Single instance via named mutex `Global\IanPlayerPC`; instance kedua ganti argumen ke instance pertama terus exit.
- [ ] Video mode stream (backlog M3.6, hanya kalau M5 sukses): merged video+audio paritas `ianvideo://`, render via libmpv. Saat fail, fallback track audio (paritas `tryRevertVideoToAudio`).

**M3 exit criteria:** search dari 2 provider, feed tampil, queue campuran lokal+stream putar, restart restore queue campuran, tidak ada credential di source.

---

## M4 — Lyrics & Downloader (2-3 minggu)

### Phase M4.1 — Lyrics engine (3-4 hari)

- [ ] Port `LyricRepository` 8 sumber: LRCLIB get/search, LrcMux, Genius (via jina.ai), Musixmatch (via jina.ai), LRCLIB plain, SomeRandomAPI, lyrics.ovh. Semuanya HTTP JSON/LRC, port ke `QNetworkAccessManager`.
- [ ] Parses LRC timestamps untuk synced; plain text sebagai fallback.
- [ ] Cache DB `lyric_cache` (schema sama: `key="artist|title"`, syncedJson atau NONE, TTL 7 hari).
- [ ] Source cycling UI: indicator nomor 1..8, konversi persis Android.
- [ ] Kalau satu source mati, tampilkan fallback plain text, app tidak hang.

### Phase M4.2 — Lyrics UI karaoke (2 hari)

- [ ] Lyrics panel dari Now Playing: highlight per baris karaoke, sinkron ke position playback.
- [ ] Auto-scroll baris aktif; user scroll manual = pause auto-scroll beberapa detik.
- [ ] Selected source perlu persist (paritas Android mempersist source number yang dipilih).
- [ ] `lyrics_style` setting: minimal / karaoke (2 variants Android).

### Phase M4.3 — Downloader + tags (3-4 hari)

- [ ] UI pilih kualitas: list bitrate dari yt-dlp format info (strategy Android: sorted by bitrate).
- [ ] Download via QNetworkAccessManager + progress + pause/resume. Antrian maju; satu-file satu-waktu — jangan parallel download banyak (YouTube mudaha throttle).
- [ ] Enrichment tags: iTunes Search API utama + Deezer fallback (album, genre, year, track#, cover) — port `MusicMetadataService`.
- [ ] Write tag via TagLib (ID3 mp3 / MP4 m4a; mengganti mp3agic + jaudiotagger Android). Regex title cleaning dari `SongTags.kt:85-122` port 1:1.
- [ ] Folder target default `Music\IanPlayer\` (Documents-user).` Setelah download, panggil scanner M1 auto-refresh.
- [ ] Tag editor UI + delete file ke Recycle Bin (`IFileOperation`, JANGAN direct delete; paritas `RecoverableSecurityException` flow Android).

**M4 exit criteria:** lagu stream bisa didownload jadi file lokal lengkap tag + cover, lyrics karaoke jalan untuk lagu lokal & stream, hasil download langsung tampil di library M1.

---

## M5 — Visual, Glass, Canvas, Recap (3-4 minggu)

Goal: identitas visual IanPlayer pindah: liquid glass, canvas, ambient palette, recap, mini player variants.

### Phase M5.1 — Settings penuh (2 hari)

- [ ] Semua key `SettingsStore` Android (list penuh di `05-FEATURE-PARITY.md`): motion_scale, canvas_mode, glass_blur/vibrancy/lens/highlight, seek_bar_style, mini_layout_index, is_pill_at_bottom, spotify_sp_dc (user input), dsb.
- [ ] Profile: user_name, avatar, banner via file picker.
- [ ] Support Developer + social links (Facebook/Instagram/TikTok statis Android).

### Phase M5.2 — Liquid glass QML (3-5 hari)

- [ ] `IanGlass` QML: `MultiEffect` blur + saturation/vibrancy + lens/highlight overlay; default & range dari `IanGlassConfig` Android (blur 4-40, vibrancy 0.3).
- [ ] Performance tier (paritas `VisualPerformanceTier`): detek GPU lemah → turunkan blur radius; fallback panel rata.
- [ ] Ambient palette finalisasi: quantize artwork ke 2 warna dominan (octree/kmeans), pakai sebagai background gradient gelap >80%, crossfade 500ms saat lagu berganti.
- [ ] Semua glass params ikut settings + `motion_scale`.

Pitfall besar:
- Blur `MultiEffect` pada layer besar itu mahal di GPU. Apply hanya di elemen kecil (panel, pill, mini player), bukan background window utama. Kalau mau full glass, test di 1080p dan 4K (framebuffer 4K + blur layer bisa bikin GPU lemah crash).

Exit: glass on/off + slider blur/vibrancy bekerja live tanpa drop frame kasus normal.

### Phase M5.3 — Canvas (4-5 hari)

- [ ] 4 mode paritas Android:
  - `off`
  - `generative` — aurora via `ShaderEffect` QML (port `PlayerCanvas.kt`; freeze saat pause).
  - `video` — backdrop video mp4/webm/mkv disandingkan lokal (paritas `CanvasVideoSource`: match by title/artist/duration), libmpv instance kedua tanpa audio, loop silent.
  - `kenburns` — slow pan/zoom artwork, QML transforms saja (murah).
- [ ] Canvas settings: intensity, blur, dim, fade before upnext.
- [ ] Spotify Canvas (opsional, kerjakan terakhir): port `canvas/*` (GraphQL partner + TOTP token mint + proxy fetch). Hanya aktif kalau user isi `sp_dc` di settings. **Jangan embed default sp_dc dari source Android** — issue security.

### Phase M5.4 — Mini player variants + seek bar styles (2 hari)

- [ ] 4 layout mini player (`mini_layout_index`) + pill top/bottom.
- [ ] Seek bar: wave & squiggle via `ShaderEffect` QML (classic & minimal sudah selesai di M2).

### Phase M5.5 — Monthly Recap (2-3 hari)

- [ ] Port `RecapBuilder`: hitung dari play_history (window 90 hari) — top songs, top artists, total plays, menit listen, taste comment.
- [ ] Recap UI kartu penuh (reuse token design system).
- [ ] Trigger bulanan (check interval + last_recap_check_ts, sama Android).
- [ ] Bonus desktop: export recap ke PNG (`QQuickItem::grabImage`) — fitur baru, Android tidak ada.

**M5 exit criteria:** app terlihat seperti IanPlayer Android di desktop: glass, canvas, ambient color, recap, mini player variants, semua mengikuti settings.

---

## M6 — Desktop-native & Rilis (2-3 minggu)

### Phase M6.1 — Auto-update (2 hari)

- [ ] Port `UpdateManager`: cek GitHub Releases API + semver compare. PC pakai repo PC sendiri (`ianplayer-pc`) — jangan referensi repo release Android, karena version & changelog beda.
- [ ] "Don't show again" pref + release notes + download.
- [ ] "Update yt-dlp": jalankan `yt-dlp.exe -U` (build standalone mendukung self-update).
- [ ] **Update flow khusus portable** (tanpa installer):
  1. Download `IanPlayer-Portable-<versi>.zip` (artefak release) ke `%TEMP%`.
  2. Show progress + "update siap, akan restart".
  3. App exit → helper batch: tunggu process mati (`taskkill /wait` fallback) → backup `user-data` penting (tidak perlu, data di `%LOCALAPPDATA%` aman terpisah) → extract zip ke folder baru → swap folder (rename lama ke `_old`, rename baru jadi nama asli) → start app → hapus `_old`.
  4. Kalau app di folder tanpa write permission (misal di Program Files, user salah taruh), tampilkan pesan jelas "pindahan app ke folder yang bisa ditulis (Desktop/Downloads/D:\)". Cegah sejak awal: cek write-access startup pertama, banner warning.
- [ ] Update check + notification in-app (soft prompt), bukan auto-push silently.

### Phase M6.2 — Desktop integration (2-3 hari)

- [ ] Single instance (QLockFile), parameter instance kedua diteruskan via named pipe.
- [ ] Register protocol + file association (.mp3/.flac/.m4a/.ogg/.opus/.wav) ditulis langsung oleh app ke `HKCU\Software\Classes` (per-user, portable; ada toggle on/off di settings + tombol "Unregister").
- [ ] Taskbar thumbnail toolbar (prev/play/next) via `ITaskbarList3`.
- [ ] Audio device switch mid-play (dicabut headphone, bluetooth): libmpv `audio-device-list` + auto-recover. Analogi `HeadsetReceiver` Android; desktop pakai MMDevice notifications / re-init libmpv.

### Phase M6.3 — Portable packaging (1-2 hari)

- [ ] Struktur folder distribusi final (autogenerate via build script):

```text
IanPlayer-Portable/
├── IanPlayer.exe
├── mpv-2.dll
├── yt-dlp.exe
├── Qt DLL (windeployqt)
└── settings-first-run.cmd   (optional: bikin shortcut desktop sekali klik)
```

- [ ] Build script satu perintah: configure release → build release → `windeployqt --release --no-translations --qmldir src/ui` → copy `yt-dlp.exe` + `mpv-2.dll` → zip `IanPlayer-Portable-<versi>.zip`.
- [ ] First-run portable essentials:
  - `%LOCALAPPDATA%\IanPlayer\` untuk semua data (DB, cache, thumbs, settings) — app harus hidup walau exe dipindah-pindah folder.
  - Perlu protocol `ianplayer://` + file association (.mp3/.flac/dsb.) tanpa installer? Kalau ya, app menuliskannya sendiri ke `HKCU\Software\Classes` saat user klik "Enable" di settings (khusus per-user, no-elevasi; letak detail di `06-RISK-LOG.md` R18).
- [ ] Wajib: test-race di Windows 10 clean VM — copy folder dari D: drive, jalankan, scan, play lokal, putar stream, pindah folder, jalankan lagi. **Ini wajib untuk portable**, karena "missing DLL after move" pasti ketemu di sini (R01/R02).
- [ ] Caveat dokumentasi user: satu folder = satu app; jangan taruh di Program Files (permission denied saat update); taruh di Desktop/Downloads/D:\.

### Phase M6.4 — Performance & QA (2-3 hari)

- [ ] Cold start < 2 detik sampai interaktif (profile pakai Tracy atau Qt Profiler).
- [ ] Library 10k lagu: scroll tanpa stutter (ListView `reuseItems`, thumbnail async, nol sync I/O di delegate).
- [ ] Streaming seek latensi rendah (cache menang).
- [ ] Memory target < 300MB steady state dengan library besar.
- [ ] QA akhir: brisk `05-FEATURE-PARITY.md` — tiap baris dihitung implemented/deferred/n.a. plus alasan.

### Phase M6.5 — Stretch (backlog v2, jangan dilaku pre-step M6.4)

- [ ] Voice assistant "Halo Yan": di desktop butuh wake word + STT sendiri (`whisper.cpp` offline / Windows Speech). Ini project tersendiri. Kandidat v2.
- [ ] Wave Recording (rekam visualizer sebagai video): Media Foundation H.264 + audio WASAPI loopback (analog AudioPlaybackCapture Android). Proyek besar. Kandidat v2.
- [ ] Light mode (token set kedua).

**M6 exit criteria:** folder portable jalan di Windows 10 clean dari lokasi biasa (Desktop/D:/Downloads), auto-update swap folder sukses, feature parity matrix lolos full, app stable.

---

## Dependency summary

```
M0 > M1 > M2 > M3 > M4 > M5 > M6  (urutan wajib)
```

Beberapa fleksibilitas:
- M2.4 crossfade bisa dibuat kapan saja.
- M6.5 stretch terpisah ke v2.
- M3.5 video mode gak wajib; M5 masa bisa mulai meskipun video mode belum ada.

Yang tidak fleksibel: M0-M1-M2 harus tuntas sebelum M3. Kalau streaming rusak (YouTube berubah), app tetap punya nilai karena local player sudah berfungsi penuh.
