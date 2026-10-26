# IanPlayer PC — Progress Log

Update file ini di tiap phase M#
 Branch kerja: `ianplayer-pc`. Android tetap `main`.

| Milestone | Status |
|---|---|
| M0 Fondasi | 🟢 M0 selesai (M0.1–M0.4) |
| M1 Local Library | 🟢 M1 selesai (M1.1–M1.3) |
| M2 Playback Penuh | 🟢 M2 selesai (M2.1–M2.4) |
| M2 Playback Penuh | ⚪ belum |
| M3 Streaming | 🟢 M3 selesai (M3.1–M3.5) |
| M4 Lyrics+Download | 🟡 M4.1–M4.2 lyrics + M4.3 downloader (yt-dlp URL → download + iTunes tags + TagLib cover + scan) selesai — tag editor & recycle bin holding |
| M5 Visual | 🟡 M5.1 Settings service (keys paritas Android) selesai — M5.2–5.5 holding |
| M6 Desktop & Rilis | ⚪ belum |

---

## M0.4 — App shell QML (selesai, 2026-10-24)

- [x] Layout: titlebar custom + rail 4 tab + konten kosong
- [x] Font **Inter** bundel — 4 static TTF (Regular/Medium/SemiBold/Bold) di `src/resources/fonts/` (~1.6 MB), register via `QFontDatabase::addApplicationFont`, fail-soft; token tipografi masuk `Theme.qml` (display 44/700 … label 12/500, kontrol baku 40px)
- [x] Tab reorderable via drag (threshold 20/80% atas-bawah item, geser lewat `ListModel.move`) + persist `tab_order` di QSettings (`Qt.labs.settings`, registry `HKCU\Software\ianocent\IanPlayer\ui`)
- [x] Custom titlebar: `Qt.FramelessWindowHint` + `startSystemMove` di area titlebar + double-click → `showMaximized`/`showNormal` + 3 window button (min/max/close)
- [x] Resize edge 8 MouseArea (`startSystemResize`, 4 sisi + 4 pojok, cursor shape pas, disable saat maximized)
- [x] Icon.ico — HOLDING (lukis artwork, detail visual M5)

Catatan M0.4 (jebakan yang ketemu & solusinya):
1. **QML property ber-prefix `on` illegal** — `Theme.onAccent` dibaca parser sebagai handler signal → singleton gagal sejak M0.2 TANPA symptom serius (binding undefined disunyikan). Rename `accentContrast`. Aturan: jangan pernah bikin property mulai dengan `on`.
2. **Singleton QML perlu diset eksplisit**: di Qt 6.8, `qt_add_qml_module` TIDAK scan `pragma Singleton` otomatis — qmldir generated cuma `Theme 1.0` biasa sampai `set_source_files_properties(src/ui/Theme.qml PROPERTIES QT_QML_SINGLETON_TYPE TRUE)` ditambah (sebelum `qt_add_qml_module`).
3. Root `ApplicationWindow` harus diimport unaliased — alias `C.` dihilangkan dari sample import; kamu judes `C.ApplicationWindow` diroot.
4. Inline component (`component Foo : Bar`) — id luar tidak selalu resolve di dalam; jadikan property publik (mis. `hoverColor`) daripada refer id luar.
5. `Qt.labs.settings` deprecated di Qt 6.8 — `Settings` BELUM tersedia di modul `QtCore` (cek qmldir Qt 6.8.3); kompromi: pakai labs dulu, migrasi ke `QSettings` C++ wrapper saat M1 settings store dibangun.
6. `causedStdErrTest`: LNK1168 kalau exe test lupa di-kill dulu sebelum rebuild.

Verified: app alive (181 MB), stderr clean kecuali deprecation warning; qmldir contains `singleton Theme 1.0`.

Test manual: drag reorder tab, persist restart, titlebar drag/resize, double-click maximize.

---

## M5.1 — Settings service (selesai, 2026-10-26)

- [x] `SettingsService` (src/app/settings_service.{h,cpp}): QSettings-backed, keys paritas Android — motionScale, glassBlur, glassVibrancy, seekBarStyle, miniLayoutIndex, isPillAtBottom, userName, avatarPath, bannerPath, spotifySpDc; property `reduceMotion` (system)
- [x] Context property `SettingsService`; gtest 55/55 PASS; build OK; smoke alive

HOLDING (v2):
1. M5.2 Liquid glass QML (IanGlass MultiEffect blur/vibrancy/lens) + ambient palette
2. M5.3 Canvas (off/generative/video/kenburns)
3. M5.4 Mini player variants + seek bar styles (wave/squiggle)
4. M5.5 Monthly recap + PNG export
5. Settings UI panel (sidebar kategori) — saat ini service saja, belum ada dialog

Jebakan M5.1:
1. QSettings NativeFormat di Windows = Registry; gunakan group keys konsisten.

---

## M4.3 — Downloader (bagian 1 selesai, 2026-10-26)

- [x] `DownloaderService` (src/app/downloader_service.{h,cpp}): download stream URL via QNetworkAccessManager ke `%MUSIC%/IanPlayer/<title>.m4a`, progress signal, cancel-on-error cleanup
- [x] Enrichment iTunes Search API (album, year, track#, cover URL 600x600) + write tags via TagLib (FileRef/MP4/MPEG), cover art (covr/APIC)
- [x] Setelah selesai: `LibraryService.addRoot` + `rescanNow()` supaya file tampil di library
- [x] Stream row tambah tombol download (↓)
- [x] Build debug OK; smoke alive; gtest 55/55 PASS

HOLDING (v2):
1. Tag editor UI + delete file ke Recycle Bin (`IFileOperation`)
2. UI pilih kualitas (list bitrate dari yt-dlp format info)
3. Pause/resume download queue (saat ini satu-file satu-waktu, tanpa pause)
4. Download progress UI (saat ini hanya signal, belum ada dialog/progress bar QML)

Jebakan M4.3:
1. TagLib `FileRef::tag()` bisa null untuk format tidak dikenal — guard.
2. MP4 cover art butuh `TagLib::MP4::CoverArt::JPEG` + Item("covr", ...).
3. Download raw googlevideo sering .m4a — simpan .m4a, tag via MP4.

---

## M4.1+M4.2 — Lyrics engine (LRCLIB) + panel (selesai, 2026-10-26)

- [x] DB: tabel `lyric_cache` (key, synced_json, plain_text, cached_at_ms) + `lyricCacheEntry`/`saveLyricCache`
- [x] `LyricRepository` (src/net/lyric_repository.{h,cpp}): LRCLIB get (precise) → search fallback; parse LRC timestamps; cache TTL 7 hari; source cycle enum 8 (implementasi LRCLIB_GET/SEARCH, sisanya None)
- [x] `LyricService` (src/app/lyric_service.{h,cpp}): context property `LyricService`, fetch on `QueueManager::currentSongChanged`, property `lyricText`/`hasSynced`
- [x] Now Playing overlay: lyrics label (wrap, height 80) bound ke `LyricService.lyricText`
- [x] Build debug OK; smoke alive; gtest 55/55 PASS

HOLDING (v2):
1. M4.3 Downloader + tags (yt-dlp quality list, QNetworkAccessManager download, TagLib write, iTunes/Deezer enrichment, recycle bin delete)
2. 6 sumber lirik tambahan (LrcMux, Genius, Musixmatch, LRCLIB plain, SomeRandomAPI, lyrics.ovh) — v1 LRCLIB only
3. Karaoke highlight per baris sinkron position (saat ini plain text)
4. Source cycling UI (indicator 1..8) + persist selected source

Jebakan M4.1:
1. LRC timestamp format `[mm:ss.xx]` — parse detik desimal → ms.
2. Cache "NONE" marker untuk plain-only / not found supaya tidak re-fetch tiap kali.

---

## M3.5 — Integration endpoints (selesai, 2026-10-26)

- [x] Single instance via named mutex `Global\IanPlayerPC`; instance kedua kirim argv via `QLocalServer/QLocalSocket` (`IanPlayerPC_IPC`) lalu exit
- [x] Protocol handler `ianplayer://` registered di `HKCU\Software\Classes\ianplayer` (per-user, tanpa elevasi); handler command tulis path exe
- [x] URL parsing: `ianplayer://song?v=<id>` dan YouTube URL (`watch?v=`, `youtu.be`, shorts/embed/live) → `StreamService.playStream(videoId)`
- [x] IPC server dipasang setelah StreamService siap; initial args diproses saat startup
- [x] Build debug OK; smoke alive; gtest 55/55 PASS

HOLDING (v2):
1. Open-with registration di Windows Default Programs (assoc .url?) — manual via settings nanti
2. Video mode stream (M3.6 backlog) — merged video+audio paritas ianvideo://, render via libmpv; fallback audio
3. Related auto-fill Up Next (M3.4 holding) — StreamService.fetchRelated siap
4. Paging infinite scroll & Tidal playback fallback

Jebakan M3.5:
1. `QLocalServer` harus di-listen setelah service siap supaya handler bisa akses `streamServicePtr`.
2. Registry path native format pakai `QSettings::NativeFormat` + `HKEY_CURRENT_USER\...`.
3. `QString::replace('/', '\\')` overload char vs string — pakai `QLatin1Char`.

---

## M3.4 — Stream UI (selesai, 2026-10-26)

- [x] `StreamSongModel` (src/models/stream_song_model.{h,cpp}) + `StreamService` (src/app/stream_service.{h,cpp}) — fan-out Innertube + Tidal, metadata cache untuk QueueManager resolver
- [x] Stream tab UI: search field debounce 300ms, Browse button, ListView (thumbnail+title+artist+source+play), model switch search/browse
- [x] Play stream: QueueManager `playSingle("yt:<videoId>")` → resolve via StreamUrlResolver (yt-dlp) + metadata dari cache
- [x] Context properties: StreamService, StreamSearchModel, StreamBrowseModel, StreamRelatedModel
- [x] Build debug OK; smoke alive; gtest 55/55 PASS

HOLDING (v2):
1. Related auto-fill Up Next (fetchRelated → QueueManager.addToQueue) — StreamService.fetchRelated siap, belum dipanggil otomatis
2. Paging infinite scroll (next page token) — Innertube parse belum ekstrak continuation
3. Tidal playback fallback ke YouTube (playStream Tidal belum resolve)
4. ThumbnailCache integrasi ke QML Image via local path (masih pakai remote URL)
5. Now Playing metadata stream sudah via QueueManager (title/artist/art dari resolver)

Jebakan M3.4:
1. Chicken-egg StreamService vs QueueManager: resolver lambda capture `streamServicePtr` yang di-set setelah ctor.
2. QML `required property` pada delegate harus cocok dengan role name (case-sensitive).

---

## M3.3 — Tidal search (selesai, 2026-10-26)

- [x] `TidalRepository` (src/net/tidal_repository.{h,cpp}): GET `api.tidal.com/v1/search/tracks` (limit 20, countryCode ID), header `x-tidal-token` (public token), parse items → title/artist/album/duration/artUrl (cover → resources.tidal.com/.../1280x1280.jpg)
- [x] Async `searchSongs(query)` + signal `searchFinished(tracks, error)`; error handling (non-200 → empty)
- [x] Build debug OK; smoke alive; gtest 55/55 PASS (tidak ada test baru — pure network)

HOLDING (v2):
1. TidalStreamResolver fallback ke YouTube (M3.4, butuh StreamSong model + QueueManager resolver)
2. Dual-provider search UI (M3.4) fan-out YTM + Tidal dengan debounce 300ms
3. Playback Tidal track via placeholder + resolver fallback

Jebakan M3.3:
1. Token Tidal publik bisa berubah/expired — kalau 401/403, search kosong; dokumentasikan.
2. `cover` string immutable (QString) — gunakan variabel mutable sebelum `.replace`.

---

## M3.2 — InnerTube client (selesai, 2026-10-26)

- [x] `InnertubeClient` (src/net/innertube_client.{h,cpp}): POST JSON ke `youtubei/v1/{search,browse,next}`, client WEB_REMIX, hl=en, gl=US; API key dari env `INNERTUBE_API_KEY` atau `keys.properties` (tidak hardcode)
- [x] Parse `musicResponsiveListItemRenderer` → title/artist/videoId/thumbnail/duration; dedup by videoId; filter songs params `EgIQAQ%3D%3D`
- [x] Noise filter: regex compilation/karaoke/8d/slowed/reverb/nightcore + `isIndianContent` (hindi/tamil/telugu/punjabi/bollywood/desi)
- [x] Genre (12) + Mood (12) lists dengan query anchor Billboard/UK + current year; shuffled di Android (PC biarkan caller shuffle)
- [x] `ThumbnailCache` (src/net/thumbnail_cache.{h,cpp}): disk cache LRU di `%LOCALAPPDATA%\IanPlayer\imgcache`, max 200MB, sha1 filename, async fetch + signal `loaded`
- [x] gtest `innertube_test.cpp` — genres/moods count 12 + noise filter → **55/55 PASS**; build debug OK; smoke alive

HOLDING (v2):
1. Stream UI (M3.4) yang konsumsi InnertubeClient (search screen, home feed, related auto-fill)
2. Tidal search (M3.3)
3. Wire StreamUrlResolver (yt-dlp) untuk `yt:` keys di QueueManager (M3.4)
4. ThumbnailCache integrasi ke QML Image (via local path) — M3.4
5. Network tests (httptest) tidak ditambahkan — pure helpers saja

Jebakan M3.2:
1. InnerTube response shape bervariasi; parser walk rekursif + guard field kosong supaya tidak crash.
2. `X-Goog-Api-Format-Version` header diperlukan supaya response konsisten.
3. API key kosong → request tetap jalan tapi mungkin 403; dokumentasikan di settings.

---

## M3.1 — yt-dlp resolver robust (selesai, 2026-10-26)

- [x] `StreamUrlResolver` (src/net/stream_url_resolver.{h,cpp}) — timeout 10s, retry 1x, pilih format audio-only bitrate tertinggi (abr/tbr), fallback root url; cache SQLite `cached_stream_urls` dengan `expire=` epoch (detik→ms), default TTL 6 jam
- [x] `ytDlpVersion()` static (runs `--version`, timeout 5s) untuk version check startup
- [x] gtest stream_url tests (isYouTubeUrl/extractVideoId/extractExpireEpoch) tetap PASS; build debug OK; smoke alive

HOLDING (v2):
1. InnerTube client (M3.2) — feeds/search/related
2. Tidal search (M3.3)
3. Stream UI + integration endpoints (M3.4/M3.5)
4. Wire StreamUrlResolver ke QueueManager untuk `yt:` keys (M3.4, butuh StreamSong model)

Jebakan M3.1:
1. `yt-dlp -J` bisa >10s pada jaringan lambat — retry 1x, jangan loop tak terbatas.
2. `expire` query param googlevideo dalam DETIK — konversi ×1000; kalau tidak ada fallback 6 jam.

---

## M2.4 — Crossfade (selesai, 2026-10-26)

- [x] `QueueManager::crossfadeMs` property (0/100/300/700/1200, persisted `crossfade_duration_ms`) + setter clamp 0..2000
- [x] Fade-out/fade-in (8 langkah, non-blocking via `QTimer::singleShot`) di sekitar `loadCurrentAndPlay` saat `crossfadeMs > 0` dan sedang playing; volume 100→0 lalu load+play lalu 0→100
- [x] True dual-instance overlap (mpv kedua) **HOLDING** — fade-only untuk v1; tambah overlap player di M5 kalau butuh
- [x] gtest **53/53 PASS**; build debug OK; smoke alive

HOLDING (v2):
1. True overlap (second mpv instance) — v1 fade-only.
2. Setting UI untuk crossfade_duration_ms (M5 settings).

Jebakan M2.4:
1. Jangan blokir main thread untuk fade — pakai `QTimer::singleShot` rekursif.
2. `std::function` rekursif butuh deklarasi `nullptr` dulu lalu assign lambda (capture by reference `&fadeOut`).

---

## M2.3 — Media keys & SMTC (selesai, 2026-10-26)

- [x] `SmtcController` (`src/platform/smtc_controller.{h,cpp}`) — C++/WinRT `ISystemMediaTransportControlsInterop::GetForWindow` (HWND dari `QWindow::winId()`); enabled play/pause/next/prev/stop; `ButtonPressed` → `QueueManager` via `QMetaObject::invokeMethod` (queued)
- [x] Metadata updater: title/artist/thumbnail (file:/// URI); playback status Playing/Paused; wired ke `QueueManager::currentSongChanged` & `isPlayingChanged`
- [x] Link `runtimeobject`, include cppwinrt path; gtest tetap **53/53 PASS** (SMTC Windows-only, tidak di-test unit)
- [x] Build debug OK; smoke alive; stderr cuma deprecation `Qt.labs.settings`

HOLDING (v2):
1. Artwork thumbnail mungkin tidak muncul di volume OSD kalau path kosong — perlu cache 256.
2. SMTC property `TimelineProperties` (seek position) belum di-set — progress di OSD statis; tambah di M2.4.

Jebakan M2.3:
1. `winrt::to_hstring(std::wstring)` tidak ada overload — pakai `winrt::hstring(wstr)`.
2. `winrt::init_apartment()` boleh gagal kalau Qt sudah init COM — tangkap exception, jangan crash.
3. Interop header `SystemMediaTransportControlsInterop.h` ada di `um/` (default include), cppwinrt di `cppwinrt/` (tambah include path).

---

## M2.2 — Now Playing + Mini player + Keyboard (selesai, 2026-10-26)

- [x] `QueueManager` extended: `isPlaying` property + `setPlaying(bool)` (wired ke `PlaybackController.pausedChanged`), `positionMs()` invokable untuk polling progress
- [x] Mini player dock bawah (72px): artwork 56 + title/artist + prev/play/next + progress bar 2px + tombol buka Now Playing; muncul saat `QueueManager.queueCount > 0`
- [x] Now Playing overlay: artwork 320, title/artist, seek bar, kontrol (shuffle/prev/play/next/repeat), Esc/minimize via scrim
- [x] Keyboard shortcuts: Space play/pause, ←/→ seek ±10s, N/P next/prev, S shuffle, R repeat, Q Now Playing, Esc close, ? overlay (via `Item` dengan `focus: true` + `Keys.onPressed`)
- [x] Shortcut overlay dialog daftar shortcut
- [x] gtest tetap **53/53 PASS** (tidak ada test baru; UI layer)
- [x] Build debug OK; smoke alive; stderr cuma deprecation `Qt.labs.settings`

HOLDING (v2):
1. Volume Up/Down keyboard placeholder (setVolume 50/60) — expose `volume` property + mute di M2.3
2. Lyrics panel (L) — M4
3. Queue panel (Q membuka Now Playing saja) — full queue panel M2.4
4. Ambient palette dari artwork — M5

Jebakan M2.2:
1. `focus` bukan property writable di `ApplicationWindow` — bungkus `Keys.onPressed` di `Item { focus: true }`.
2. QML tidak suka `;` setelah `}` sebelum property binding (`} ; onClicked:`) — hapus spasi+semicolon.
3. `pausedChanged(bool paused)` semantik terbalik vs `isPlaying` — connect dengan lambda `setPlaying(!paused)`.

---

## M2.1 — PlaybackController/QueueManager engine (selesai, 2026-10-26)

- [x] `QueueManager` (`src/playback/queue_manager.{h,cpp}`) — satu sumber kebenaran queue, paritas `PlayerGateway.kt`: `playNow/playSingle/playSongAt/playNext/playPrevious/addToQueue/playNextInQueue/removeSongFromQueue/reorderUpNext/clearQueue/toggleShuffle/toggleRepeat/togglePlayPause/seekToMs/setVolume/stop`
- [x] State: shuffle (base queue before shuffle + restore), repeat off/all/one, continuousFailures guard (<3 skip, x3 stop), prefetch hook 8s (skeleton — stream resolution M3)
- [x] Persist: QSettings keys `last_queue/last_song_id/last_index/is_shuffle_on/repeat_mode` (paritas `KEY_LAST_*`) JSON codec; `restoreState()` di ctor tanpa auto-play
- [x] Wiring main.cpp: PlaybackController + QueueManager, mpv signals (endReached/errorOccurred/positionChanged) → queue ops; `songStarted` → `LibraryService.recordPlayedByKey` (record play count/history)
- [x] Context property `QueueManager` untuk QML (M2.2 UI akan konsumsi)
- [x] gtest `queue_manager_test.cpp` — **12 tests** (play now/load, next wrap repeat all, stop at end, previous seek>3s, remove, reorder, shuffle current-first, repeat cycle, ended repeat-one restart, ended auto-next, persist/restore, error guard) → **53/53 PASS**
- [x] Build debug OK; smoke alive; stderr cuma deprecation `Qt.labs.settings`

Verifikasi: gtest 53/53; smoke app alive.

HOLDING (v2):
1. Mini player / Now Playing UI (M2.2)
2. Media keys & SMTC (M2.3)
3. Crossfade dual-instance (M2.4)
4. Mute property (volume 0 sementara) — expose `muted` di M2.2
5. Auto-fill up-next (stream only) — M3

Jebakan M2.1:
1. `m_continuousFailures` jangan di-reset di `loadCurrentAndPlay` (reset hanya setelah playback benar-benar jalan) — kalau tidak, guard error x3 tidak pernah tercapai.
2. `std::clamp(int, qsizetype, qsizetype)` ambigu di MSVC — cast `int(...)`.
3. Nested struct `SongInfo` butuh prefix `QueueManager::` di .cpp atau alias `using SongInfo = QueueManager::SongInfo;`.
4. `EngineHooks` pakai `std::function` supaya QueueManager testable tanpa mpv (fake engine).

---

## M1.3 — Playlists (selesai, 2026-10-24)

- [x] DB playlist API (`library_database.{h,cpp}`): `allPlaylists()` (urut nama NOCASE + songCount subquery), `playlistSongs(id)` (JOIN songs, `ORDER BY position`, tamu `avail=1`), `createPlaylist/renamePlaylist/deletePlaylist` (transaction + hapus `playlist_songs` manual — tanpa FK cascade), `setPlaylistImage`, `addToPlaylist` (append `MAX(position)+1`, `INSERT OR IGNORE` — `numRowsAffected()` untuk laporkan false saat duplikat), `removeFromPlaylist` (false kalau lagu tidak ada), `movePlaylistSong(id, songId, toPos)` (baca urutan → remove/insert → tulis ulang position, clamp)
- [x] `PlaylistListModel` (`src/models/playlist_list_model.{h,cpp}`): roles `playlistKey/playlistName/playlistImage/playlistCreatedTs/playlistSongCount`, `reload()`/`reset()`
- [x] `PlaylistSongModel` (`src/models/playlist_song_model.{h,cpp}`): isi playlist + `Q_PROPERTY playlistKey` (set → reload) + **`moveSong(songId, toPos)`** — DB rewrite lalu `beginMoveRows` (destRow+1 kalau turun, konvensi end-exclusive) → ListView animasi tanpa reset (pitfall M1.2)
- [x] `LibraryService` invokables: `createPlaylist/renamePlaylist/deletePlaylist/setPlaylistImage/addSongToPlaylist/removeSongFromPlaylist` + signal `playlistsChanged`/`playlistContentsChanged(pid)`; songKey cuma `local:<id>` — **`yt:<videoId>` ditolak sampai M2 queue** (false)
- [x] main.cpp: context property `PlaylistListModel`/`PlaylistSongModel` + connect signal → reload; `PlaylistSongModel.reload()` hanya kalau `playlistKey() == pid`
- [x] QML `AppWindow.qml`:
  - `LibraryList` dijadikan generik: props `listModel` (default `LibraryProxy`), `sectionProp` ("" = tanpa section), `reorderable` (grip 20px kiri row, drag → hitung target dari pointer Y + autoscroll pinggir viewport → `PlaylistSongModel.moveSong`); rail fast-scroller disembunyikan saat reorderable
  - Playlists pane: baris kartu (cover 56px gambar/♥ placeholder, nama, %lagu, menu ⋯: Buka/Ganti Nama/Ganti Sampul/Hapus) + tombol "Playlist Baru" + empty-state label
  - Playlist detail pane (subPane `"playlist"`; `detailPane` album/artis di-exclude): header Kembali + ⋯ kelola + `LibraryList { listModel: PlaylistSongModel; reorderable: true }`
  - Context menu row Songs: "Tambah ke Playlist" → `addPlaylistMenu` chooser (Repeater `PlaylistListModel` + "Playlist baru...")
  - `playlistNameDialog` (create/rename; return id `createPlaylist` dipakai auto-add lagu chooser), `playlistImageDialog` (`FileDialog` QtQuick.Dialogs; path disimpan sebagai `file:///` URL — Image source menerima langsung)
- [x] gtest: `PlaylistCrudAndSongOrder` (create/rename/add append/move/remove/songCount/persist delete) + `PlaylistDuplicateSongIgnoredAndGuardRails` (duplikat false, remove tak-ada false, move out-of-range false, nama kosong -1, image persist) → **41/41 PASS**

Verifikasi: build preset debug OK; gtest 41/41; smoke app alive, stderr cuma deprecation `Qt.labs.settings` (sudah diketahui M0.4).

HOLDING (diarsipkan v2):
1. DnD drag-lagu dari tab Songs → rail "Playlists" (chooser menu via klik-kanan + submenu sudah memenuhi UX tambah-lagu; baris drag DnD ke rail butuh Drag.started/DropArea per rail item, ditunda)
2. Agregasi `playlistContentsChanged` untuk image rename di header detail tak auto-refresh label — minor.

Jebakan M1.3:
1. **`INSERT OR IGNORE` tidak cukup** — exec OK meski baris duplikat tidak tertulis; gunakan `numRowsAffected() > 0` untuk kontrak API add/remove.
2. `beginMoveRows` arah turun: destRow = posisi + 1 (end-exclusive) — kalau salah, Qt assert "Invalid row moved".
3. `std::clamp(int, qsizetype, qsizetype)` gagal compile (MSVC ambigu) — cast `int(...)` kalau campur tipe int & `QVector::size()`.
4. Panjang `deletePlaylist` perlu hapus manual kedua tabel dulu sebelum commit — single transaction supaya tidak select di tengah state setengah-hapus.

---

- [x] `LibrarySongModel` (`src/models/library_song_model.{h,cpp}`): QAbstractListModel snapshot dari DB (id/path/title/artist/album/duration/bitrate/codec/art64/art256/track/year/favorite/playCount/sectionLetter + sortRole casefolded); `reload()` dipanggil main.cpp saat signal `libraryChanged`/`favoritesChanged` — semua akses DB di main thread (pitfall doc)
- [x] `LibraryProxyModel` (`src/models/library_proxy_model.{h,cpp}`): QSortFilterProxyModel — sort 5 mode (title/artist/album+track#/recently added desc/play count desc, paritas `sort_mode` Android), search filter title/artist/album case-insensitive, filter albumId/artistId untuk detail screen, `indexForLetter()` untuk fast scroller
- [x] `AlbumListModel` + `ArtistListModel` (`src/models/*.{h,cpp}`) + DB `allAlbums()/allArtists()` (count lagu per album/artist, urut nama COLLATE NOCASE)
- [x] UI Songs tab (`AppWindow.qml`): scan controls + search field (debounce 250ms Timer) + combo sort 5 mode + ListView baris 56px (thumb 48px/judul/artis/durasi/heart hover/klik-ka For context menu)
- [x] Context menu klik kanan: Tambah/Hapus Favorit, Tampilkan di Explorer (`revealLocalFile` → `explorer.exe /select,`), Tambah ke Playlist (disabled, M1.3)
- [x] Klik kiri baris → `recordLocalPlayed` — recording play counts + history 90d sudah hidup dari M1.1+ (hook real play mengunggu M2 queue)
- [x] Fast scroller A–Z rail kanan (aktif hanya sort title; sections via role `sectionLetter`)
- [x] Albums tab: GridView kotak 160×200 (art 144/placeholder huruf, nama, artis, count) + Artists tab: GridView bulat — klik → detail screen (header + LibraryList dengan `LibraryProxy.albumFilter/artistFilter`)
- [x] Detail screen album/artis: tombol Kembali; default tab list disi "Artists" (desktop, reorderable)
- [x] gtest baru: `library_model_test.cpp` (3 test: model reload+roles, proxy sort 5 mode + search, fast scroller) — total **39/39 PASS**
- [x] Build + smoke: app alive, stderr hanya deprecation Qt.labs.settings (known)

Catatan M1.2 (jebakan):
1. **QSortFilterProxyModel sorts lazily** — `setSourceModel` + `sort(0)` wajib; reuse: `invalidate()` saat reload model, jangan reset sortMode sama nilai (guard early-return).
2. `Set-alias` bubug klasik ini render: `ScrollBar.vertical` attached property harus fully qualified `C.ScrollBar.vertical` — alias import mengel Components jadi unqualified type not found ("Non-existent attached object").
3. `C.ItemDelegate.radius` tidak ada — pindah ke `background: Rectangle{ radius: } ` (warning "Cannot assign to non-existent property radius").
4. Referensi id instance dari dalam inline component (pitfall M0.4 #4): jangan refer id luar — letter rail pakai binding konteks property, `fmtDur` didefinisikan di dalam LibraryList itu sendiri.
5. `QSortFilterProxyModel.count` — property `count()` harus Q_PROPERTY sendiri (QML tidak pakai rowCount).
6. Test `LibraryProxyModel.index(row)` di C++ harus `proxy.index(row, 0)` — signature 2-arg [:catatan untuk test lain].

HOLDING (bagian M1.2 belum): Playlists tab (M1.3), fast scroller letter highlight hover (minor), album art di sort Recently Added header (minor).

---

## M1.1+ — Lanjutan M1.1: fix bug persist + paritas #7 #30 (selesai, 2026-10-24)

**Bug terbesar yang ketemu:** `LibraryDatabase::upsertSong` tidak pernah memanggil `q.exec()` — scan "berhasil" (progress jalan, commit transaksi sukses) tapi nol baris tertulis ke DB, `songCount` selalu 0. Terbukti lewat gtest baru `LibraryDbTest.SongIdLookupByPath` (upsert → lookup gagal). Fix: cek `q.exec()` + balas hasilnya. Scan M1.1 sebelumnya PRA-fix ini praktis tidak menulis apa pun.

- [x] Paritas **#7** (favorites + play counts + history 90d → SQLite):
  - `LibraryDatabase`: `setFavorite/isFavorite/favoriteKeys`, `recordPlayed` (transaksi: play_counts upsert +1 + play_history insert + trim `played_ts <= now-90d`, paritas `SongStore.recordPlayed`), `playCount/allPlayCounts/playHistory`, `songIdByPath/songIdAndDurationByPath`
  - `song_key = "local:<id>" | "yt:<videoId>"` per 02-ARCHITECTURE.md
  - `LibraryService` QML: `favoriteCount`, `isFavorite/setFavorite/toggleFavorite`, `localKeyForPath`, `recordLocalPlayed(path)` + signal `favoritesChanged`/`playStateChanged`. Hook dipanggil controller playback saat M2 track lokal mulai putar
- [x] Paritas **#30** (title cleaning regex): port 1:1 `SongTags.kt:85-162` → `src/core/title_cleaner.{h,cpp}` (namespace `SongTags`, masuk `ianplayer_core`): `cleanTitle` (6 regex TITLE_JUNK + loop repeat + trimEnd `-|([` + fallback raw), `isJunkArtist`, `resolve` ("- Topic"/VEVO cleanup, trusted-artist strip verbatim, dash split dengan guard `left<=60 && no'('`), dipakai scanner: `readTagLib` memanggil `cleanTitle(rec.title)` di akhir (junk dari tag ATAU nama file); `resolve` penuh baru dipakai M4.3
- [x] gtest baru: `title_clean_test.cpp` (17 test) + `library_db_test.cpp` (4 test) — total **36/36 PASS**
- [x] **test_main.cpp custom** — `QCoreApplication` wajib diangkat sebelum test: tanpa itu semua test DB crash `0xc0000005` (gtest_main bawaan diganti; `GTest::gtest_main` dicabut dari link)
- [x] Fix QML app mati saat start (regression ketemu smoke): `import QtQuick.Dialogs 1.3` → `import QtQuick.Dialogs` — Qt 6.8.3 cuma ekspos versi 6.x, modul `1.3` "not installed" → fail-fast -1
- [x] Fix binding undefined: `Theme.sizeH4` token baru (15/600) — reference SongsPane membuat "Unable to assign [undefined] to int"
- [x] Smoke: app alive, stderr hanya deprecation `Qt.labs.settings` (known, migrate ke QSettings saat M5.1)

Catatan M1.1+ (jebakan):
1. **Query prepared tapi tidak pernah `exec()` gagal senyap** — `upsertSong` balas `true` tanpa cek; pola: selalu propagasikan `q.exec()` result. Kontrol gtest upsert wajib ada untuk setiap fungsi persist.
2. Test Qt tanpa `QCoreApplication` → singletons (mis. registry QSqlDatabase) belum ter-init penuh; crash khas `0xc0000005` TANPA stack trace berguna. Solusi: main test custom.
3. `QtQuick.Dialogs 1.3` compile-time oke (module ada di disk) tapi runtime gagal karena versi import — smoke GUI wajib redirect stderr (`Start-Process -RedirectStandardError`), jangan cuma cek "windows muncul".

---

## M1.1 — Folder scan + metadata TagLib + SQLite library (selesai, 2026-10-24)

- [x] `LibraryScanner` (`src/core/library_scanner.{h,cpp}`): traverse rekursif multi folder; ekstensi mp3/flac/m4a/aac/wav/ogg/opus; skip folder sistem & dotfolder (System Volume Information, $RECYCLE.BIN, Windows, Program Files, AppData, dll)
- [x] TagLib: title/artist/album/track/year/duration_ms/bitrate dari tag; embedded album art (mp3 APIC, FLAC picture, m4a/aac covr) → thumbnail 256 & 64 px JPEG q85 di `%LOCALAPPDATA%\ianocent\IanPlayer\thumbnails\<sha1(album|artist)>_{256,64}.jpg`
- [x] Fallback `"Artist - Title"` dari nama file — port 1:1 `MusicRepository.kt` (dashIndex > 0), tidak ada dash → Unknown Artist
- [x] `LibraryDatabase` (`src/db/library_database.{h,cpp}`): schema per `02-ARCHITECTURE.md` (songs, albums, artists, playlists, playlist_songs, favorites, play_counts, play_history, cached_stream_urls → kolom `video_id`), `PRAGMA journal_mode=WAL`, connection `library#<dbPath>` di `%LOCALAPPDATA%\ianocent\IanPlayer\ianplayer-pc.db`
- [x] Incremental scan: `knownModifiedByPath` → skip TagLib kalau `modified_ts` masih cocok; akhir scan `upsertSong` batch transaksi + `markUnavailableExcept` + `rebuildIndexes` (artist_id/album_id + tabel albums/artists/art_256)
- [x] `QFileSystemWatcher` pada roots + debounce 2 s → rescan incrementil
- [x] `LibraryService` (`src/app/library_service.{h,cpp}`) diexpose ke QML sebagai context property `LibraryService`: `scanning`, `progressCurrent/Total`, `songCount`, `scanStatusText`, `addRoot/removeRoot/rescanNow`; roots persist QSettings (`library/folder_roots`)
- [x] `main.cpp`: daftar LibraryScanner/LibraryDatabase/LibraryService, context property, `QStandardPaths::AppLocalDataLocation/ianplayer-pc.db`; `QGuiApplication` org "ianocent" / app "IanPlayer"
- [x] `AppWindow.qml`: tab **Songs** → `SongsPane`: tombol Scan Folder (`FolderDialog`), Rescan, `ProgressBar`, status text, counter lagu; import `QtQuick.Dialogs 1.3`
- [x] `StreamUrlResolver`: migrasi kolom cache `videoId` → `video_id` (ALTER TABLE di ctor + semua query), sesuai 02-ARCHITECTURE.md
- [x] CMakeLists: `Qt6::Concurrent` + `Qt6::Gui` (tests), `find_package(TagLib CONFIG REQUIRED)` + link `TagLib::TagLib`, 4 file baru masuk exe; `library_scanner` + test baru masuk `ianplayer_tests`
- [x] gtest `ParseTaglessFileNameTest`: 5 case (dash split, multi-dash, no-dash, leading dash, empty parts) — **PASS**
- [x] Build debug via `cmake --preset debug && cmake --build --preset debug` — lolos (VsDevCmd + PATH Qt bin); `ianplayer_tests.exe` → **15/15 PASS**
- [x] Smoke clash GUI: app alive, start bersih, tidak crash

Catatan M1.1:
1. `QImage` di header scanner → forward declaration saja (`class QImage;`), include `.cpp`, agar target tests (hanya Qt Core) tidak butuh QtGui include path; tests link `Qt6::Gui` untuk QImage di implementasi scanner.
2. `QSqlDatabase::exec()` deprecated warning Qt 6.8 — biarkan, gunakan `QSqlQuery::exec()` di kode baru.
3. `Q_DECLARE_METATYPE` butuh `<QtCore/qmetatype.h>` (bukan cuma `<QtGlobal>`).
4. TagLib 2.3.1: gambar lewat `tag->frameList("APIC")` (mp3), `flac.pictureList().front()->data()`, `mp4 tag->item("covr").toCoverArtList().front().data()`.
5. Exe debug di `build\debug\Debug\ianplayer.exe`; kill proses lama dulu (LNK1168).

---

- [x] `PlaybackController` (`src/playback/`) — `mpv_create` + option `volume=100`, `gapless-audio=weak`, ao default WASAPI
- [x] `loadTrack(url)` async via `mpv_command_async`
- [x] Observe properties: `time-pos`, `duration`, `pause`, `eof-reached`, `metadata` — event loop `mpv_wait_event` di `std::thread` sendiri, sinyal Qt ke main thread (auto-queue)
- [x] Teardown order aman: stop → join thread → `mpv_terminate_destroy` (destructor stack sebelum QGuiApplication)
- [x] CLI test 1: `ianplayer.exe <file.mp3>` — LOADED + eof ✓ (`ting_iphone.mp3`)
- [x] CLI test 2: `ianplayer.exe <YouTube URL>` — resolve yt-dlp → load → seek +10 → pause → resume + volume 60 → timeout ✓
- [x] `StreamUrlResolver` (`src/net/`) — yt-dlp `-J --no-download` via `QProcess::setProgram+setArguments`, pilih format audio-only (vcodec none, acodec ada) bitrate tertinggi
- [x] Cache SQLite `cached_stream_urls` (videoId/url/resolvedAtMs/expiresAtMs) di `%LOCALAPPDATA%\ianocent\IanPlayer\ianplayer-pc.db` — file muncul ✓
- [x] Parser `expire=` epoch (pure static, testable) + fallback TTL 6 jam
- [x] gtest `ianplayer_tests` — 10 test parser expire + videoId + host filter: **10/10 PASS**
- [x] CMake: lib `ianplayer_core` (non-UI, shared exe+tests), `find_package(Sql, GTest)`, POST_BUILD copy yt-dlp.exe + libmpv-2.dll
- [x] Mode CLI attach parent console (`AttachConsole`) — subsystem GUI tak punya stdout default

Catatan M0.3 (jebakan yang ketemu & solusinya):
1. **`mpv.lib` import-lib salah nama DLL**: `lib /def:exports.def` tanpa direktif `LIBRARY` membuat import table menunjuk `exports.dll` (nama file def) → runtime `0xc0000135 STATUS_DLL_NOT_FOUND`. Solusi: tambah baris `LIBRARY libmpv-2.dll` di baris-1 def lalu regen `lib /def:...`.
2. ctest harus dijalankan dari `build/debug` (bukan source root), dan itu pun opt-in PATH Qt bin.
3. Debug-compile memakai DLL Qt bertambah `d` suffix (Qt6Cored.dll dst) → PATH `C:\Qt\6.8.3\msvc2022_64\bin` dengan Debug runtime.
4. `dumpbin /dependents` = cara cepat diagnosa DLL not found pre-lah.

Verified smoke:
```
[LOAD] file loaded, mulai putar
[END] eof tercapai
```
```
[CACHE/RESOLVE] expiresAtMs=1792803518000
[LOAD] file loaded, mulai putar
[SEEK] +10 s (pos=5.46233)
[PAUSE]
[RESUME] volume->60
[TIMEOUT]
```

---

## M0.2 — CMake skeleton (selesai, 2026-10-23)

- [x] Struktur repo lengkap (`src/{app,core,db,playback,net,models,ui,resources}`, `third_party`, `assets`, `docs`)
- [x] `CMakeLists.txt` root — target `ianplayer`, C++20, Qt6::Quick + QuickControls2, mpv.lib linked, POST_BUILD copy `libmpv-2.dll`
- [x] `CMakePresets.json` — debug/release; toolchain vcpkg **pin hardcode ke `C:/dev/vcpkg`** (VsDevCmd menimpa `VCPKG_ROOT` ke `VC\vcpkg` bawaan VS yang versi lama)
- [x] `vcpkg.json` — taglib 2.3.1 + gtest 1.18.0 installed (manifest + `builtin-baseline`, pertama kali ~4.3 menit, sekarang cache)
- [x] `src/main.cpp` — QGuiApplication + QQmlApplicationEngine + style Basic + fail-fast QML error
- [x] `src/ui/Theme.qml` — singleton design token
- [x] `src/ui/AppWindow.qml` — window 960×640 dark, sidebar 4 tab placeholder + label versi
- [x] `assets/app.manifest` — longPathAware + PerMonitorV2 + Win10/11 supportedOS
- [x] `.gitattributes` — CRLF per-bagian + binary (scope folder `ianplayer-pc/`, Android tak terpengaruh)
- [x] Flags `/W4 /permissive- /utf-8`
- [ ] Icon.ico — HOLDING, kerjakan di M0.4 (custom titlebar) bersama artwork

**Verified:** `cmake --preset debug && cmake --build --preset debug` → `build/debug/Debug/ianplayer.exe` → app RUNNING, window QML tampil.

Catatan M0.2 (jebakan yang ketemu & solusinya):
1. `qt_policy` wajib dipanggil SETELAH `find_package(Qt6)` — sebelum itu command-nya belum ada.
2. `QQuickStyle` butuh module `QuickControls2`, tidak cuma `Quick`.
3. VsDevCmd menimpa `VCPKG_ROOT` → vcpkg bawaan VS menuntut `builtin-baseline`; solusi: presets pin toolchain `C:/dev/vcpkg` + `builtin-baseline` di `vcpkg.json`.
4. Runtime dev: PATH perlu ditambah `C:\Qt\6.8.3\msvc2022_64\bin` agar `Qt6*.dll` ketemu (deploy final pakai windeployqt di M6).

---

## M0.1 — Environment (selesai, 2026-10-23)

Checklist `01-TOOLCHAIN.md` bagian 1:

- [x] Konfirmasi kerja di branch `ianplayer-pc` (bukan main)
- [x] Python 3.14.3 sudah ada
- [x] **Visual Studio 2022 Community v17.14 + workload Desktop C++** (MSVC 19.44, CMake 3.31.6, Win SDK)
- [x] **vcpkg** — `C:\dev\vcpkg` terbootstrap (vcpkg-tool 2026-09-26); env `VCPKG_ROOT` + `VCPKG_DEFAULT_TRIPLET=x64-windows` (User)
- [x] **Qt 6.8.3 msvc2022_64** — selesai diinstall via aqtinstall (`C:\Qt\6.8.3\msvc2022_64`), `qmake -version` verified; module: qtimageformats, qtshadertools, qtsvg (ikut base). Pin 6.8.3 karena 6.7.x gak punya binary `msvc2022_64`
- [x] **yt-dlp.exe** — `third_party/ytdlp/yt-dlp.exe` v2026.10.21
- [x] **libmpv dev** — GitHub shinchiro/mpv-winbuild-cmake tag 20261021; `include/mpv/*.h` + `lib/x86_64/libmpv-2.dll` (115 MB, verified penuh setelah re-ekstrak dengan 7-Zip — py7zr korup file BCJ2)
- [x] **mpv.lib (import lib MSVC)** — digenerate dari `dumpbin /exports` + `lib /def` (53 KB, 206 exports) di `lib/x86_64/mpv.lib`
- [x] CMake — pakai CMake bawaan VS (3.31.6), verified di Developer PowerShell
- [x] Sanity final: cmake/cl/vcpkg/qmake/yt-dlp semua merespon

Exit M0.1 tercapai — semua tool merespon di Developer PowerShell yang sama (VS 2022, `VsDevCmd.bat -arch=amd64`).

Catatan M0.1:
- Sumber libmpv diganti dari SourceForge ke build resmi `shinchiro/mpv-winbuild-cmake`; layout + cara generate `mpv.lib` sudah ditulis di `01-TOOLCHAIN.md`
- Sebelum VS selesai: MSVC/CMake Tools belum bisa dipakai, jangan mulai M0.2 (CMake skeleton)
- WASAPI output audio sudah built-in di libmpv build ini; tidak perlu konfigurasi tambahan, volume default 100
