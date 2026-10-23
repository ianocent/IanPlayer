# IanPlayer PC — Progress Log

Update file ini di tiap phase M#
 Branch kerja: `ianplayer-pc`. Android tetap `main`.

| Milestone | Status |
|---|---|
| M0 Fondasi | 🔵 M0.1 sedang |
| M1 Local Library | ⚪ belum |
| M2 Playback Penuh | ⚪ belum |
| M3 Streaming | ⚪ belum |
| M4 Lyrics+Download | ⚪ belum |
| M5 Visual | ⚪ belum |
| M6 Desktop & Rilis | ⚪ belum |

---

## M0.1 — Environment (in progress, 2026-10-23)

Checklist `01-TOOLCHAIN.md` bagian 1:

- [x] Konfirmasi kerja di branch `ianplayer-pc` (bukan main)
- [x] Python 3.14.3 sudah ada
- [ ] **Visual Studio 2022 Community + workload Desktop C++** — jalan background (winget), belum kelar
- [x] **vcpkg** — `C:\dev\vcpkg` terbootstrap (vcpkg-tool 2026-09-26); env `VCPKG_ROOT` + `VCPKG_DEFAULT_TRIPLET=x64-windows` (User)
- [x] **Qt 6.8.3 msvc2022_64** — diinstall via aqtinstall ke `C:\Qt` (pin 6.8.3 karena 6.7.x gak punya binary `msvc2022_64`); module: qtimageformats, qtshadertools
- [x] **yt-dlp.exe** — `third_party/ytdlp/yt-dlp.exe` v2026.10.21
- [x] **libmpv dev** — GitHub shinchiro/mpv-winbuild-cmake tag 20261021; `include/mpv/*.h` + `lib/x86_64/libmpv-2.dll` sudah di tempat
- [ ] **mpv.lib (import lib MSVC)** — TERTAHOLD sampai VS kelar, lalu: `dumpbin /exports` + `lib /def` (perintah di `01-TOOLCHAIN.md` bagian Libmpv)
- [ ] CMake standalone — pakai CMake bawaan VS; cek `cmake --version` di Developer PowerShell setelah VS jadi
- [ ] Sanity final: semua tool merespon `--version` di Developer PowerShell terpasang

Catatan M0.1:
- Sumber libmpv diganti dari SourceForge ke build resmi `shinchiro/mpv-winbuild-cmake`; layout + cara generate `mpv.lib` sudah ditulis di `01-TOOLCHAIN.md`
- Sebelum VS selesai: MSVC/CMake Tools belum bisa dipakai, jangan mulai M0.2 (CMake skeleton)
- WASAPI output audio sudah built-in di libmpv build ini; tidak perlu konfigurasi tambahan, volume default 100
