# Trace — Reference Detail (Convx → IanPlayer)

Dokumen ini nyatet detail teknis dari repo referensi ([cosmictaserdev-creator/Convx](https://github.com/cosmictaserdev-creator/Convx), fork dari [vivi-music](https://github.com/vivizzz007/vivi-music), lisensi **GPL-3.0**) dan posisi IanPlayer saat ini, sebagai bahan acuan implementasi. **Catatan lisensi: semua poin di bawah adalah catatan konsep/arsitektur untuk di-rewrite sendiri, bukan kode yang boleh disalin langsung** — GPL-3.0 (Convx) tidak compatible untuk copy-paste ke proyek IanPlayer kecuali IanPlayer juga di-lisensikan GPL-3.0.

---

## 1. Convx — Architecture Snapshot

- **UI:** Jetpack Compose, MVVM (`ui/screens` + `viewmodels`), navigasi via `NavigationBuilder.kt`.
- **Liquid Glass system:** `ui/component/GlassEffect.kt` → `Modifier.liquidGlass(...)`, dibangun di atas vendored copy `Kyant0/backdrop` (`ui/component/backdrop/`). Konsepnya: sebuah `Backdrop` (`rememberLayerBackdrop()` + `Modifier.layerBackdrop(...)`) menangkap pixel real dari subtree tertentu; surface lain yang refer ke backdrop yang sama bisa sample/blur/refract lewat `drawBackdrop(...)`. Nav bar floating, tombol back/share bundar, dan sheet semuanya adalah "glass surface" yang sample backdrop terdekat.
- **Playback:** Media3 `ExoPlayer` service (`playback/MusicService.kt`) — sama keluarga dengan `PlayerGateway`/`PlaybackService` IanPlayer.
- **Data:** Room DB (`db/`) + DataStore preferences (`utils/DataStore.kt`, `constants/PreferenceKeys.kt`) — analog ke `AppDatabase.kt` + `SettingsStore` (SharedPreferences) IanPlayer.
- **YouTube Music access:** modul terpisah `innertube/` (unofficial InnerTube client) — analog `YTMusicRepository.kt` IanPlayer, tapi Convx modularize jadi package sendiri di root repo, bukan cuma 1 file di `data/`.
- **Module directories tambahan** yang relevan buat referensi fitur: `applecanvas/`, `canvas/`, `vivimusiccanvas/`, `betterlyrics/`, `youlyplus/`, `spotify/`, `shazamkit/`, `lastfm/`, `kizzy/` (Discord RPC), `spine/` (lossless audio path).

## 2. Player Layout Reference — per gaya

### 2.1 "Apple Music Player V17" style (disebut eksplisit di README Convx: *"The Apple Music Player V17 full-screen player style (Settings → Player Theme) is ported from vivi-music's Apple Music player UI"*)
- Ada di module `applecanvas/` (dan style rendering terkait `vivimusiccanvas/`).
- Karakteristik yang perlu ditiru (rewrite sendiri) di IanPlayer:
  - Artwork besar, rounded corner tegas, sering full-bleed ke tepi layar dengan sedikit padding.
  - Animasi scale halus artwork mengikuti play/pause state (artwork sedikit membesar saat playing).
  - Background: gradient/mesh animasi lembut yang diturunkan dari warna dominan artwork (bukan blur foto langsung, lebih ke "color wash").
  - Kontrol besar, tipografi bold untuk judul lagu.
  - Lyrics full-screen dengan highlight kata berjalan (karaoke-style).
- **Setting entry point di Convx:** `Settings → Player Theme` — pola ini persis yang diminta user (opsi player theme/layout), jadi struktur setting IanPlayer (`playerLayoutMode` di `SettingsStore`) sebaiknya juga hidup di bagian Settings → Player.

### 2.2 "YT Music"-flavored reference
- Convx sendiri berbasis streaming YT Music (`innertube`), tapi *UI*-nya di-reimagine jadi Liquid Glass — bukan niru tampilan YT Music app asli.
- Untuk kebutuhan IanPlayer ("YT Music Layout" sebagai salah satu opsi), acuan **bukan dari kode Convx** (karena Convx justru menjauh dari tampilan YT Music asli) — melainkan dari pengetahuan umum UI YT Music resmi: full-bleed artwork, tab Up Next/Lyrics/Related di bawah player, mini-player bar tipis di atas nav, ambient blur background dari artwork (fitur "Ambient mode" khas YT Music).
- Implementasi tetap original — hanya konsep layout (posisi elemen, ambient background) yang dijadikan acuan.

### 2.3 "Spotify" reference
- Sama seperti YT Music, Convx tidak punya kode Spotify-clone — folder `spotify/` di repo Convx kemungkinan modul integrasi (misal Spotify metadata/canvas video, bukan UI Spotify). Perlu dicek isi module ini kalau mau diselidiki lebih jauh; untuk saat ini treat sebagai *data reference*, bukan UI reference.
- Untuk "Spotify Layout" di IanPlayer, acuan visual (bukan kode) yang umum dikenal: artwork card dengan shadow tegas, seekbar tipis minimalis, warna dominan artwork jadi solid/gradient background flat (bukan blur), tombol shuffle/repeat di kiri-kanan bawah seekbar, tombol like (heart) di pojok.

### 2.4 "Base IanPlayer Layout"
- Ini adalah UI existing IanPlayer (`ui/NowPlayingScreen.kt`, 1020 baris) sebagai-adanya — jadi opsi default, tidak perlu direferensikan ke Convx sama sekali, cukup diekstrak jadi composable terpisah (lihat Milestone M0).

## 3. Canvas / Background Detail

IanPlayer saat ini (`ui/canvas/PlayerCanvas.kt`, 443 baris) sudah punya:
```kotlin
enum class CanvasMode(val persisted: Int) {
    GENERATIVE(SettingsStore.CANVAS_GENERATIVE),
    VIDEO(SettingsStore.CANVAS_VIDEO),
    KEN_BURNS(SettingsStore.CANVAS_KEN_BURNS);
}
```
dengan `SettingsStore.canvasMode` (int, default `CANVAS_OFF = 0`) dan constants `CANVAS_GENERATIVE/VIDEO/KEN_BURNS`. Video canvas resolve file custom lokal (video di samping file audio, atau folder dedicated) — logic ini sudah ada dan tidak perlu diubah.

Convx punya sistem "Liquid Glass" yang jauh lebih berat (real backdrop blur & refraction lewat `Kyant0/backdrop`) — ini **referensi konsep, bukan target 1:1** karena:
- Berat secara performa (real-time pixel capture + blur) — berisiko untuk device low-end.
- Butuh library backdrop capture yang IanPlayer belum punya.

**Rekomendasi trace untuk IanPlayer (bertahap, bukan all-or-nothing):**

| Layout | Background treatment yang diadopsi (versi ringan) | Teknik Android/Compose |
|---|---|---|
| Base IanPlayer | Tetap `CanvasMode` existing (Generative/Video/Ken Burns), user-selectable seperti sekarang | Sudah ada, no change |
| YT Music | Ambient blur dari artwork (blur radius sedang, darkened overlay) | `RenderEffect.createBlurEffect` (API 31+) via `Modifier.graphicsLayer { renderEffect = ... }`, fallback: `Palette`-based gradient overlay di API <31 (Palette API sudah dipakai IanPlayer untuk `NowPlayingScreen` gradient, tinggal extend) |
| Spotify | Solid/gradient flat dari 1-2 warna dominan (`Palette.dominantSwatch`, `Palette.vibrantSwatch`), tanpa blur | `Brush.verticalGradient` dari swatch warna, tidak perlu RenderEffect sama sekali → paling ringan |
| Apple Music | Animated soft gradient mesh (2-3 warna dominan, di-animate posisinya pelan pakai `infiniteRepeatable`) | Kombinasi `Palette` + `Brush.radialGradient`/`sweepGradient` yang posisinya dianimasikan via `rememberInfiniteTransition` (pola animasi sudah ada contohnya di `PlayerCanvas.kt` generative mode, tinggal reuse pendekatan animasi yang sama) |

Catatan: `PlayerCanvas.kt` generative mode kemungkinan sudah pakai `rememberInfiniteTransition` + `animateFloat` (terlihat dari import `infiniteRepeatable`, `animateFloat` di head file) — pola ini bisa **direuse langsung** untuk animated gradient Apple Music-style tanpa nulis infra animasi baru.

## 4. Crossfade / Auto-DJ Reference

Dari Convx release notes (v1.3, v1.5.1):
- v1.3 memperkenalkan **Auto-DJ Mixing**: "songs back-to-back with tempo-matched crossfades: the incoming song's speed is gently nudged to match the outgoing one while they blend, so the mix stays locked to a beat."
- v1.5.1 menambah **Creative Transitions**: dependency chain eksplisit — *Crossfade* harus dinyalakan dulu → baru *Auto-DJ Mixing* → baru *Creative Transitions* (fallback non-seamless transition seperti "echo tail" atau "turntable brake" untuk track yang gak bisa di-mix mulus).

**Implikasi untuk IanPlayer (M4):**
- Jangan langsung loncat ke tempo-matching (kompleks, butuh beat detection/tempo analysis) — itu backlog jangka panjang.
- MVP crossfade IanPlayer: **volume-fade dua track overlap durasi X detik**, tanpa tempo adjustment. Ini sudah cukup untuk "smooth transisi" yang diminta user.
- Struktur setting berjenjang (crossfade → auto-mix → creative transition) dari Convx adalah pola UX yang baik buat dicontoh: jangan expose semua toggle sekaligus, buat dependency yang jelas di Settings.

## 5. Lyrics Reference

- Convx pakai kombinasi **Better Lyrics** + **SimpMusic** (sumber lirik tersinkron) dan **YouLyPlus** (styling lirik in-app), plus fitur "karaoke-style word-by-word highlighting".
- IanPlayer sudah punya `LyricRepository` + `LyricCardGenerator` sendiri — tidak perlu ambil dependency Convx punya (beda sumber data/API), cukup ambil **konsep UX**: highlight per-kata kalau data timestamp mendukung, fallback highlight per-baris.

## 6. Hal yang Sengaja TIDAK Diadopsi (out of scope, dicatat biar jelas alasannya)

| Fitur Convx | Alasan tidak diadopsi ke v1 |
|---|---|
| DIY Player Editor (drag sticker, custom icon per elemen) | Kompleksitas tinggi, bukan prioritas "smooth & enjoyable", masuk backlog |
| Listen Together (sync playback antar device) | Butuh infra realtime (server/socket), scope besar |
| Discord Rich Presence (`kizzy/`) | Fitur sosial tambahan, bukan core playback/UX |
| Tablet-optimized layout / collapsible sidebar | IanPlayer saat ini fokus phone-first |
| Real backdrop blur (Kyant0/backdrop, Liquid Glass) | Terlalu berat untuk device low-end tanpa opt-in performance tier; diganti versi ringan (RenderEffect/gradient) |
| Local-only mode toggle (rewiring Home ke local library) | IanPlayer sudah punya arsitektur sumber lokal & stream terpisah (`SongSource`, `YTMusicRepository`) yang beda desain — tidak relevan langsung |

## 7. File-file IanPlayer yang Bakal Kena Dampak (existing, hasil audit zip)

| File | Baris (approx) | Peran saat ini | Perubahan yang diantisipasi |
|---|---|---|---|
| `ui/NowPlayingScreen.kt` | 1020 | Monolitik: seluruh Now Playing UI | Dipecah jadi shell + 4 layout composable (M0, M3) |
| `ui/canvas/PlayerCanvas.kt` | 443 | Generative/Video/Ken Burns canvas | Ditambah `LayoutCanvasStyle` per layout (M2) |
| `data/SettingsStore.kt` | 85 | Semua preference key (dark mode, canvas mode, dst) | Tambah `playerLayoutMode`, `crossfadeDurationMs` (M0, M4) |
| `ui/theme/Theme.kt` | 57 | Material3 dynamic color only | Kemungkinan tetap dipakai sebagai base color scheme; layout-specific styling dilakukan di level composable, bukan ganti `MaterialTheme` global |
| `viewmodel/MusicViewModel.kt` | 1386 | State holder utama playback/UI | Tambah state `playerLayoutMode` observable, tanpa mengubah playback logic inti |
| `player/PlaybackService.kt` | 201 | Media3 service | Tambah crossfade logic (M4) |
| `player/PlayerGateway.kt` | — | Interface/gateway ke service | Kemungkinan tambah method terkait crossfade config |

---

*Semua detail di atas adalah hasil observasi publik (README, release notes GitHub Convx) + audit langsung isi `ianplayer.zip` yang diupload. Implementasi aktual tetap harus ditulis ulang sesuai gaya kode IanPlayer sendiri, mengingat perbedaan lisensi dengan Convx (GPL-3.0).*
