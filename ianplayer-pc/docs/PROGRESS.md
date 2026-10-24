# IanPlayer PC — Progress Log

Update file ini di tiap phase M#
 Branch kerja: `ianplayer-pc`. Android tetap `main`.

| Milestone | Status |
|---|---|
| M0 Fondasi | 🟢 M0 selesai (M0.1–M0.4) |
| M1 Local Library | 🟡 M1.2 Models + Library UI (Songs/Albums/Artists jalan) — lanjut sisa M1.2 (playlists M1.3) |
| M2 Playback Penuh | ⚪ belum |
| M3 Streaming | ⚪ belum |
| M4 Lyrics+Download | ⚪ belum |
| M5 Visual | ⚪ belum |
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

## M1.2 — Models + Library UI (bagian 1 selesai, 2026-10-24)

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
