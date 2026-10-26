# IanPlayer PC — Risk Log (Jebakan Windows)

Tiap item: gejala, penyebab, solusi. Acuan saat sesuatu gak jalan dan "kok bisa" moment.

## Build & runtime

| Risik id | Gejala | Penyebab | Solusi |
|---|---|---|---|
| R01 | `.exe` jalan di dev PC, gagal di PC lain (`missing DLL`) | Runtime MSVC / Qt DLL tidak di-copy | windeployqt dengan `--qmldir src/ui`; wajib test di clean VM (M6.3) |
| R02 | QML window putih/kosong di PC lain | QML module plugin tidak terdeploy | lupa `--qmldir`, cek folder `plugins.qmltypes` |
| R03 | `mpv-2.dll not found` | DLL tidak di samping .exe | POST_BUILD copy (01-TOOLCHAIN.md) |
| R04 | `mpv_create` gagal abis build | Arch 32/64 mismatch atau double-load DLL | semua target x64; jangan ada 2 versi dll di PATH |
| R05 | Crash saat exit | mpv destroy urutan salah | stop -> destroy -> quit; guard saat quit event |
| R06 | Rendering flicker saat blur | MultiEffect layer blur mahal di GPU lemah | kecilkan radius; cek render loop `QSG_RENDER_LOOP`; test ANGLE vs OpenGL (R14) |
| R07 | Font berubah di PC lain | font diambil dari system | bundel Inter ke app (QFontDatabase), jangan andalkan system |
| R08 | String layar berubah menjadi mengandung karakter berganda | codepage MSVC | semua string user-visible pakai pipeline UTF-8 Qt; jangan literal non-ASCII di .cpp |
| R09 | Long path gagal scan (>260 char) | policy Windows legacy | manifest `longPathAware=true` + registry `LongPathsEnabled` (documented, low-prio) |
| R10 | SQLite DB terkunci saat scan + UI baca bersama | connection dilakukan cross-thread tanpa WAL | koneksi per-thread + `PRAGMA journal_mode=WAL` |
| R11 | Double yt-dlp spawn bikin 2 resolusi | race saat user next cepat | serialize resolver (queue of 1) + idempotent cache check |

## Streaming & API (risiko terbesar)

| Risik id | Gejala | Penyebab | Solusi |
|---|---|---|---|
| R12 | Semua stream gagal saat perangkat sama masih jalan kemarin | resolusi pipeline YouTube berubah (sering) | yt-dlp bundel selalu update; version check; fallback UI yang jelas |
| R13 | InnerTube response berubah field | Google mengubah API live | isolate versi client di kode; unit test noise filter; field tak dikenal di-ignore, jangan crash |
| R14 | Rendering beda perilaku antar GPU driver (Intel vs NVIDIA) | Qt backend default beda | bake `qt.conf` default: ANGLE atau desktop GL; test dua-duanya |

## Windows native (yang Android tidak perlu susah karena platform)

| Risik id | Gejala | Penyebab | Solusi |
|---|---|---|---|
| R15 | Media keys jalan hanya saat window fokus | pakai global hook, tidak konsisten | SMTC WinRT tanpa global keyboard hook; test saat fokus di app lain |
| R16 | Frameless window: resize bermasalah, snap/Aero nyangkut | custom titlebar belum benar | pakai `startSystemMove`/`startSystemResize` (native Qt 6); test snap |
| R17 | Double-click titlebar tidak maximize | area maximize tidak dihandle | handle via native event filter (`WM_NCHITTEST`), hanya area titlebar |
| R18 | Protocol `ianplayer://` tidak jalan | registry salah | di `HKCU\Software\Classes\ianplayer`: value `URL Protocol` string kosong + key `shell\open\command`; test `start ianplayer://song?v=ID` |
| R19 | Folder portable berantakan setelah update (residu lama) | swap folder gagal / residu `_old` | updater drop folder `_old` setelah swap sukses; data user tetap di `%LOCALAPPDATA%` jadi aman |
| R20 | Audio mati saat cabut headphone / switch BT | libmpv device lama bermasalah | observe `audio-device-list`; saat device berubah, set `audio-device=auto` dan reload file yang sedang jalan jika macet |

## Vendor & layanan pihak ketiga

| Risik id | Gejala | Penyebab | Solusi |
|---|---|---|---|
| R21 | 403 semua stream | YouTube butuh PoToken / client versi baru | update yt-dlp; fallback `player_client`; pantau issue yt-dlp kalau berlanjut |
| R22 | Lyrics provider mati satu | jina.ai/LRCLIB berubah / rate-limit | timeout 8s per provider + source cycling + persist pilihan; fallback plain |
| R23 | Tidal token gagal auth | public token expiry | retry 1x; kalau tetap gagal, degrade ke metadata dari YTM saja |
| R24 | Spotify canvas tidak muncul | sp_dc invalid/expired | settings tampilkan error jelas "sp_dc tidak valid"; feature gate, jangan crash |

## Deployment & rilis

| Risik id | Gejala | Penyebab | Solusi |
|---|---|---|---|
| R25 | Installer di-reject SmartScreen | tidak signed | self-signed untuk dev; release pakai signing cert (opsional) |
| R26 | Update overwrite file yang locked | app masih running memegang .exe | updater exit dulu -> helper: tunggu process mati -> swap folder -> start app |
| R27 | yt-dlp.exe di-blokir antivirus | false positive umum | catat di FAQ; whitelist guidance; bundel hanya dari release resmi GitHub |
| R28 | Cache tumbuh disk tak terkendali | img cache + thumbnails + canvas cache | LRU cap config `cache_size_mb` (default 500MB), settings "clear cache" |

## Manual implementations (yang Android gratisan dari platform)

Daftar behavior Android harus dibikin manual di desktop:

1. **Media notification**: Android punya notification player otomatis dari Media3; di PC penggantinya SMTC + mini player bar.
2. **Wake lock** saat buffering: libmpv handle internal; tidak perlu.
3. **Interruption audio** (kebiasaan Android saat panggilan): tidak relevan; BT/headphone switch ada di R20.
4. **Deep link routing**: Android cukup declare intent filter di manifest; Windows perlu entry registry (R18).
5. **Autoplay saat headphone reconnect**: fitur baru; hook dari audio device change handler (R20).
