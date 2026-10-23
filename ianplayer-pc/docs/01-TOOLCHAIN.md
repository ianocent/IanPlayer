# IanPlayer PC — Toolchain & Setup (Windows 10)

Tujuan dokumen: dari PC kosong sampai jendela Qt QML jalan + libmpv muterin audio, tanpa langkah yang hilang.

## 1. Software yang harus terpasang

Pasang urutannya. Setiap langkah punya cara cek.

### 1.1 Visual Studio 2022 (MSVC, BUKAN Build Tools doang)

```powershell
winget install --id Microsoft.VisualStudio.2022.Community --exact
```

Saat installer terbuka, centang workload:

- **Desktop development with C++**
  - MSVC v143 x64/x86 build tools
  - Windows 10 SDK (10.0.19041.0 atau lebih baru)
  - **C++ CMake tools for Windows**

Cek:

```powershell
& "C:\Program Files\Microsoft Visual Studio\2022\Community\Common7\Tools\Launch-VsDevShell.ps1"
cmake --version   # >= 3.28
cl                # MSVC harus muncul banner-nya
```

Selalu buka code di **Developer PowerShell for VS 2022**, bukan PowerShell polos. Kalau PowerShell polos, PATH MSVC tidak ada dan konfigurasi CMake bakal gagal dengan error `CL not found`.

### 1.2 CMake

VS 2022 sudah bawa CMake. Kalau mau versi standalone:

```powershell
winget install --id Kitware.CMake --exact
```

Pilih "Add CMake to PATH" saat installer muncul opsinya.

### 1.3 Qt 6.7 LTS

```powershell
winget install --id QtProject.Qt.DesignStudio --exact  # optional, untuk desain QML
```

Download installer online Qt lebih dari itu: masuk `https://www.qt.io/download-qt-installer`, bikin akun Qt (gratis), pilih:

- **Qt 6.7.x** (LTS) → komponen minimum:
  - `MSVC 2022 64-bit`
  - `Qt Quick 3D` — optional
  - `Qt Charts` — kalau nanti perlu grafik
  - `Additional Libraries` → **Qt Advanced (Debug symbols)** biar crashes bisa di-trace
- **Developer and Designer Tools** → Qt Creator (editor QML enak, walaupun coding utama bisa tetap VS)

Lokasi default: `C:\Qt\6.7.3\msvc2022_64`. Kalau versi beda, sesuaikan di `CMAKE_PREFIX_PATH`.

### 1.4 vcpkg

```powershell
git clone https://github.com/microsoft/vcpkg C:\dev\vcpkg
C:\dev\vcpkg\bootstrap-vcpkg.bat
setx VCPKG_ROOT C:\dev\vcpkg
setx VCPKG_DEFAULT_TRIPLET x64-windows
```

Tutup dan buka ulang PowerShell supaya env var nempel. Cek:

```powershell
vcpkg version    # >= 2024
echo $env:VCPKG_ROOT
```

### 1.5 Python (opsional tapi dipakai skill UI)

Sudah terpasang: Python 3.14.3. Tidak wajib untuk build, dipakai untuk script tooling.

## 2. Struktur repo

```text
ianplayer-pc/
├── CMakeLists.txt            # root
├── CMakePresets.json         # preset debug/release biar gak ketik manual
├── vcpkg.json                # dependency manifest
├── third_party/
│   ├── mpv/
│   │   ├── include/          # client.h (render_gl.h optional)
│   │   └── lib/              # mpv-2.dll + mpv.lib (import)
│   └── ytdlp/
│       └── yt-dlp.exe        # bundel, resolve stream YouTube (lihat bawah)
├── src/
│   ├── main.cpp
│   ├── app/                  # QApplication setup, engine load, single-instance
│   ├── core/                 # settings (QSettings), folder scan, metadata (TagLib)
│   ├── db/                   # SQLite: schema, migrasi
│   ├── playback/             # MpvController (QProcess-free, C API), queue, ytdlp runner (QProcess)
│   ├── net/                  # InnerTube client (search/feed/related), Tidal, lyrics client
│   ├── models/               # QAbstractListModel: LibraryModel, PlaylistModel, QueueModel, SearchModel
│   ├── ui/                   # .qml semua screen
│   └── resources/            # qrc: ikon SVG, font Inter, Theme.qml
├── assets/
│   ├── icon.ico
│   └── packaging/                # script zip release portable + update manifest
└── docs/                         # milestone files (00..06)
```

### Libmpv (audio backend)</think><tool_call>edit<arg_key>newString</arg_key><arg_value>### Libmpv (audio backend)

**Jangan pakai vcpkg untuk libmpv.** Versi di vcpkg sering telat. Ambil build resmi:

```powershell
# Ambil rilis terbaru di https://sourceforge.net/projects/mpv-player-windows/files/libmpv/
# Simpan isi paket ke third_party/mpv/
#   include/     -> client.h (render.h & render_gl.h opsional)
#   lib/x86_64/  -> mpv.lib (import lib) + mpv-2.dll
# mpv-2.dll harus di sebelah .exe saat deploy
```

Include & link di `CMakeLists.txt`:

```cmake
set(MPV_DIR ${CMAKE_SOURCE_DIR}/third_party/mpv)
target_include_directories(ianplayer PRIVATE ${MPV_DIR}/include)
target_link_libraries(ianplayer PRIVATE ${MPV_DIR}/lib/x86_64/mpv.lib)
```

Copy `mpv-2.dll` ke folder output tiap build:

```cmake
add_custom_command(TARGET ianplayer POST_BUILD
  COMMAND ${CMAKE_COMMAND} -E copy_if_different
    ${MPV_DIR}/lib/x86_64/mpv-2.dll $<TARGET_FILE_DIR:ianplayer>)
```

libmpv untuk project ini dipakai **audio-first**: decode mp3/flac/m4a/ogg/opus/wav lokal plus stream URL hasil yt-dlp (AAC/Opus dari googlevideo). Render video (opsi kanvas video notifikasi visual) cuma kalau lanjut fitur video mode.

### yt-dlp (stream resolver)

Download `yt-dlp.exe` dari https://github.com/yt-dlp/yt-dlp/releases (single exe, no install). Taruh di `third_party/ytdlp/`. Ini dipanggil sebagai child process (`QProcess`), output JSON-nya di-parse:

```powershell
third_party\ytdlp\yt-dlp.exe "https://youtube.com/watch?v=VIDEOID" -J --no-download
```

Field yang diambil dari JSON: `formats[]` (filter audio-only `acodec != none`, sort bitrate), `duration`, `title`, `uploader`, `thumbnail`.

Penting: yt-dlp **harus selalu diupdate** karena YouTube sering ganti mekanisme resolusi. Sejak milestone M1 masuk tugas "check yt-dlp version" di app; jangan pernah bundel versi tua lama dan lupa.

## 3. vcpkg.json (dependency manifest)

```json
{
  "name": "ianplayer-pc",
  "version": "0.1.0",
  "dependencies": [
    "taglib",
    { "name": "gtest", "version>=": "1.14.0" }
  ]
}
```

Qt via vcpkg **tidak direkomendasi** (lama build, konflik dengan installer resmi). Qt tetap dari installer resmi, dicari lewat `CMAKE_PREFIX_PATH`.

Deps penting via vcpkg:

- `taglib` — baca/tulis metadata ID3/MP4/FLAC/Ogg (pengganti mp3agic + jaudiotagger Android), plus ekstrak embedded album art
- `gtest` — unit test core (parsing judul, cache TTL logic)

## 4. CMakePresets.json

```json
{
  "version": 6,
  "configurePresets": [
    {
      "name": "debug",
      "generator": "Visual Studio 17 2022",
      "architecture": { "value": "x64", "strategy": "set" },
      "binaryDir": "${sourceDir}/build/${presetName}",
      "toolchainFile": "$env{VCPKG_ROOT}/scripts/buildsystems/vcpkg.cmake",
      "cacheVariables": {
        "CMAKE_PREFIX_PATH": "C:/Qt/6.7.3/msvc2022_64",
        "CMAKE_TOOLCHAIN_FILE": "",
        "VCPKG_TARGET_TRIPLET": "x64-windows"
      }
    },
    {
      "name": "release",
      "inherits": "debug",
      "cacheVariables": { "CMAKE_BUILD_TYPE": "Release" }
    }
  ],
  "buildPresets": [
    { "name": "debug",   "configurePreset": "debug" },
    { "name": "release", "configurePreset": "release", "configuration": "Release" }
  ]
}
```

## 5. Smoke test "Hello Audio" (exit criteria M0)

Dua target, urut:

1. Buka file audio lokal (`test.mp3`, `test.flac`) via argumen command line → terputar, pause/resume, volume up/down, seek presisi, next otomatis saat selesai.
2. `IanPlayer.exe "https://youtube.com/watch?v=VIDEOID"` → app memanggil yt-dlp (`-J`), ambil URL audio terbaik, feed ke libmpv, mulai putar. Kalau gagal, cek `06-RISK-LOG.md`.

Checklist error umum dan penyebabnya:

| Gejala | Penyebab | Solusi |
|---|---|---|
| `mpv-2.dll not found` saat run | DLL gak di samping .exe | Copy via POST_BUILD di atas |
| `mpv_create` return NULL | libmpv 32/64 tidak match dengan exe | Pastikan semua x64 |
| Flac/opus gak bunyi, mp3 bunyi | libmpv build tanpa codec tersebut | Pakai build libmpv resmi (sudah lengkap) |
| yt-dlp error 403 saat play stream | resolusi YouTube butuh client/PoToken | update yt-dlp; cek flag `--extractor-args youtube:player_client` |
| QML error saat app start | module QML gak terdeploy | `windeployqt --qmldir src/ui` (bagian 7) |
| Crash saat penutupan | mpv_destroy dipanggil setelah QApplication bubar | urutan teardown: stop playback → destroy mpv instance → quit app |

## 6. Windows SDK

Windows 10 SDK minimal 10.0.19041.0 sudah cukup. Tidak perlu SDK Windows 11.

## 7. Runtime deploy (folder gak perlu installer)

Jalan di PC anyar tanpa install, yang harus di-padding folder `deploy/`:

```text
IanPlayer.exe
mpv-2.dll
yt-dlp.exe          # bundel dari third_party/ytdlp
Qt*.dll             # windeployqt generate otomatis
*.pdb               # simpan sendiri (jangan dibundel), unt	debug crash dari user
```

```powershell
windeployqt --release --qmldir src/ui build/release/IanPlayer.exe
```

`--qmldir` penting: tanpainya, plugin QML (`QtQuick`, `QtQuick.Controls`) gak ke-copy dan jendela bakal putih/kosong di PC lain.

## 8. Kesepakatan environment

- Semua path pakai `/` di CMake, bukan `\`.
- Source file: UTF‑8 with BOM kalau ada string non-ASCII literal; lebih safe lagi semua string non-ASCII ditaruh di .qml atau rc file, bukan inline di .cpp.
- Line ending: CRLF untuk semua file C++ dalam repo. Kunci lewat `.gitattributes`.
- Nama file pakai lowercase snake_case biar gak bingung case-sensitivity kalau nanti ada tooling lintas OS.
