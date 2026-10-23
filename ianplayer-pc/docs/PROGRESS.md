# IanPlayer PC — Progress Log

Update file ini di tiap phase M#
 Branch kerja: `ianplayer-pc`. Android tetap `main`.

| Milestone | Status |
|---|---|
| M0 Fondasi | 🟢 M0.1 + M0.2 + M0.3 selesai — lanjut M0.4 |
| M1 Local Library | ⚪ belum |
| M2 Playback Penuh | ⚪ belum |
| M3 Streaming | ⚪ belum |
| M4 Lyrics+Download | ⚪ belum |
| M5 Visual | ⚪ belum |
| M6 Desktop & Rilis | ⚪ belum |

---

## M0.3 — libmpv audio chain (selesai, 2026-10-24)

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
