# PRD — IanPlayer UI/UX & Playback Optimization
**Reference project:** [cosmictaserdev-creator/Convx](https://github.com/cosmictaserdev-creator/Convx) (fork of vivi-music, GPL-3.0)
**Target project:** IanPlayer ([ianocent/IanPlayer](https://github.com/ianocent/IanPlayer)) — Kotlin, Jetpack Compose, Media3 ExoPlayer

---

## 1. Background

IanPlayer udah punya fondasi kuat: `PlaybackService` (Media3), `PlayerCanvas` (generative/video/ken-burns canvas), `NowPlayingScreen`, `MusicViewModel`, `SettingsStore` berbasis SharedPreferences, YT Music streaming lewat InnerTube (`YTMusicRepository`), plus fitur unik (WaveRecordSheet screen recording, voice assistant, social signals, recap builder).

Convx adalah player sejenis (streaming YT Music + Media3) yang lebih matang di sisi **visual system, transisi, dan personalisasi player screen**. Convx tidak dipakai sebagai source code untuk di-copy (GPL-3.0 — lisensi beda dari IanPlayer), tapi dipakai sebagai **reference arsitektur & UX pattern** buat jadi acuan optimasi.

## 2. Problem Statement

- Transisi antar layar/mini-player ke full player masih standar Compose default, belum "smooth premium feel".
- Tema cuma ngikutin Material dynamic color (`Theme.kt`), belum ada sistem layout player yang bisa di-switch sesuai selera user.
- Canvas mode udah ada (`CanvasMode`: GENERATIVE/VIDEO/KEN_BURNS) tapi belum terhubung ke satu sistem "player skin" yang lebih luas (background, kontrol, artwork shape, dsb).
- Belum ada crossfade/gapless/auto-DJ mixing di `PlaybackService`.
- Lyrics belum ada mode karaoke word-by-word.

## 3. Goals

1. **Multiple Player Layout** — user bisa milih gaya tampilan Now Playing screen: `YT Music Layout`, `Spotify Layout`, `Apple Music Layout`, `Base IanPlayer Layout` (default existing).
2. **Smoother transitions** — animasi antar state (mini-player ↔ full player, track change, tab switch) pakai spring-based motion, bukan default linear/ease.
3. **Enjoyable playback experience** — crossfade antar lagu, opsional auto-mix, canvas/background yang lebih hidup nyambung ke layout yang dipilih.
4. **Canvas detail per-layout** — tiap layout referensi (YT Music/Spotify/Apple Music) punya treatment canvas/background sendiri (blur style, gradient dari artwork, dsb), dicatat detail di trace doc.
5. Tetap 100% original implementation (tidak menyalin kode Convx secara verbatim karena beda lisensi) — hanya mengadopsi **konsep/pattern arsitektur**.

## 4. Non-Goals

- Tidak mem-fork / menyalin source Convx (GPL) langsung ke IanPlayer.
- Tidak menambahkan fitur sosial berat (Listen Together / Discord RPC) di fase ini — dicatat sebagai future idea saja.
- Tidak mengganti backend streaming (tetap InnerTube-based `YTMusicRepository`, bukan migrasi ke modul `innertube` Convx).

## 5. Target Users

Ianocent sendiri sebagai primary user/daily driver, dengan potensi rilis publik terbatas (APK sideload) buat testing.

## 6. Feature Scope

### 6.1 Player Layout System (Settings → Player Theme)
- Setting baru `playerLayoutMode` di `SettingsStore` (int enum, mirip pola `canvasMode` yang udah ada).
- 4 opsi: `BASE_IANPLAYER`, `YT_MUSIC`, `SPOTIFY`, `APPLE_MUSIC`.
- Tiap layout = varian composable Now Playing screen dengan:
  - Susunan kontrol (posisi seek bar, tombol shuffle/repeat, queue button) beda per referensi.
  - Artwork treatment beda (square vs rounded vs full-bleed).
  - Typography & spacing beda (nama lagu/artist).
  - Background/canvas treatment beda (lihat trace doc §Canvas).
- `NowPlayingScreen.kt` di-refactor jadi **shell + slot composables** supaya 4 layout share state (`MusicViewModel`) tapi render berbeda, hindari 1 file monolitik 1020 baris makin membengkak.

### 6.2 Transition & Motion System
- Ganti transisi mini-player → full player pakai shared-element-ish spring animation (`animateFloatAsState` dgn `spring(dampingRatio, stiffness)` bukan `tween` linear).
- Page/tab switch pakai `AnimatedContent` dengan slide+fade, bukan instant swap.
- Album art & background cross-dissolve saat ganti lagu (durasi pendek, non-blocking terhadap playback).

### 6.3 Crossfade / Smooth Track Transition (PlaybackService)
- Tambah opsi crossfade (durasi configurable, mis. 2–8 detik) di `PlaybackService`, mengelola dua `ExoPlayer`/`MediaItem` overlap secara volume-fade, bukan hard cut.
- Opsional "Auto-Mix" toggle terpisah dari crossfade dasar (tempo-aware jadi fase lanjut/backlog, bukan wajib MVP).

### 6.4 Canvas Enhancement per Layout
- Extend `CanvasMode` enum atau buat lapisan baru `LayoutCanvasStyle` yang menentukan treatment default background sesuai `playerLayoutMode` (contoh: YT Music → blurred artwork ambient, Spotify → dominant-color gradient flat, Apple Music → animated gradient mesh, Base → existing generative/video/ken-burns pilihan user).
- Detail teknis tiap gaya dicatat di `03-Trace-Reference-Detail.md`.

### 6.5 Lyrics Polish (stretch, lower priority)
- Karaoke-style word highlight di lyrics existing (`LyricCardGenerator`, `LyricRepository`) — inspirasi dari fitur lyrics Convx, tapi implementasi native pakai sumber lirik yang IanPlayer udah punya.

## 7. Success Metrics (kualitatif, personal project)

- Switch antar 4 layout tanpa reset playback / tanpa jank.
- Transisi terasa "smooth" secara subjektif (tidak ada linear cut abrupt).
- Crossfade jalan tanpa audio glitch/dobel-suara.
- Tidak ada regresi di fitur existing (housekeeping-equivalent buat IanPlayer: streaming cache, download, wave record, voice assistant).

## 8. Constraints & Risks

- **Lisensi:** Convx = GPL-3.0. Semua adopsi harus rewrite bebas (clean-room), bukan copy-paste kode/asset dari Convx maupun vivi-music.
- **Kompleksitas state:** 4 layout berbagi 1 `MusicViewModel` — risiko state desync kalau tiap layout implement listener sendiri-sendiri. Mitigasi: layout hanya boleh baca state via shared UI-state holder, tidak boleh side-effect langsung ke player.
- **Performance:** backdrop-blur ala Convx (real-time blur/refraction) berat di device low-end. IanPlayer harus fallback ke blur murah (RenderEffect/BlurMaskFilter API level check, atau gradient overlay biasa) di device lama.
- **Scope creep:** fitur Convx sangat luas (DIY sticker editor, Listen Together, Discord RPC, tablet layout, Auto-DJ tempo match). PRD ini sengaja membatasi ke *layout switch + transition + canvas + crossfade* saja untuk fase 1.

## 9. Out of Scope for v1 (Backlog / Future)
- DIY player editor (drag stickers/custom icon).
- Listen Together / real-time sync playback antar device.
- Discord Rich Presence.
- Tempo-matched Auto-DJ mixing (baru sebatas crossfade volume-based).
- Tablet-optimized layout.

---
*Lanjut ke `02-Milestones-Roadmap.md` untuk breakdown eksekusi, dan `03-Trace-Reference-Detail.md` untuk detail teknis referensi Convx per fitur/canvas.*
