# Milestones — IanPlayer Optimization Roadmap

Scope mengacu ke `01-PRD-IanPlayer-Optimization.md`. Tiap milestone diasumsikan bisa dikerjain incremental tanpa break existing build.

---

## M0 — Prep & Refactor Foundation
**Tujuan:** siapin struktur kode supaya nggak numpuk di 1 file, sebelum nambah 4 layout.

- [ ] Audit `NowPlayingScreen.kt` (1020 baris) → pisah jadi:
  - `NowPlayingShell.kt` (host: state hoisting, gesture, shared logic)
  - `layouts/BaseIanPlayerLayout.kt` (extract existing UI apa adanya, jadi default)
  - Folder baru `ui/nowplaying/layouts/`
- [ ] Tambah enum `PlayerLayoutMode` (`BASE_IANPLAYER, YT_MUSIC, SPOTIFY, APPLE_MUSIC`) + field `playerLayoutMode: Int` di `SettingsStore.kt` (pola sama kayak `canvasMode`).
- [ ] Tambah entry Settings UI: "Player Layout" (dropdown/segmented control), persist via `SettingsStore`.
- [ ] Pastikan build tetap hijau, behavior default (`BASE_IANPLAYER`) identik ke UI lama.

**Definition of Done:** app jalan normal, setting baru ada tapi belum ngefek ke UI (masih render base layout untuk semua opsi — stub).

---

## M1 — Motion & Transition System
**Tujuan:** transisi lebih smooth, independen dari fitur layout (biar berasa manfaatnya duluan).

- [ ] Ganti animasi mini-player ↔ full player: dari state binary jadi `animateFloatAsState(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium))`.
- [ ] `AnimatedContent`/`Crossfade` untuk pergantian tab (Songs/Albums/Stream/Playlists) di root nav.
- [ ] Album art & judul lagu pakai crossfade saat track berganti (bukan snap).
- [ ] Overscroll bounce ringan di list utama (opsional, hemat kalau ada composable overscroll effect Compose bawaan yang cocok).

**Definition of Done:** buka/tutup full player & ganti tab terasa smooth, tidak ada regresi scroll performance di list panjang.

---

## M2 — Canvas System Extension
**Tujuan:** perluas `CanvasMode` supaya bisa nyambung ke tiap layout referensi.

- [ ] Definisikan `LayoutCanvasStyle` (lihat detail di trace doc) sebagai default background per `PlayerLayoutMode`, tapi tetap override-able lewat `CanvasMode` existing (generative/video/ken-burns) kalau user mau custom.
- [ ] Implement blur/ambient background dari artwork (pakai `Palette` API yang IanPlayer udah punya di NowPlayingScreen untuk gradient, extend ke blur-lite pakai `RenderEffect` di API 31+, fallback gradient-only di bawahnya).
- [ ] Uji performa di device rendah spek (kalau available) — canvas jangan nge-drop FPS list scroll di belakang.

**Definition of Done:** tiap `PlayerLayoutMode` punya nuansa background berbeda secara default, toggle-able, tidak crash di API level lama.

---

## M3 — 4 Player Layouts (UI Implementation)
**Tujuan:** implementasi visual nyata untuk tiap opsi.

- [ ] `BaseIanPlayerLayout` — finalize hasil ekstraksi M0 (no visual change, jadi baseline pembanding).
- [ ] `YtMusicLayout` — full-bleed artwork besar, kontrol minimal di bawah, ambient blur background dari artwork, tab lyrics/queue swipe-able.
- [ ] `SpotifyLayout` — artwork card dengan shadow, seekbar tipis, dominant-color solid/gradient background flat (bukan blur berat), tombol like/kontrol khas Spotify (icon shape, posisi shuffle-repeat di kiri-kanan seek bar).
- [ ] `AppleMusicLayout` — rounded artwork besar dengan animasi scale halus saat play/pause, gradient mesh animasi lembut, kontrol dengan tipografi lebih besar/bold, lyrics highlight style Apple.
- [ ] Wire keempatnya ke `NowPlayingShell` via `when(playerLayoutMode)`.
- [ ] Settings preview thumbnail per layout (opsional, nice-to-have).

**Definition of Done:** user bisa switch 4 layout dari Settings, playback tidak ke-interrupt saat switch, semua state (progress, queue, like, lyrics) tetap konsisten lintas layout.

---

## M4 — Crossfade & Smooth Playback Transition
**Tujuan:** "lebih enjoyable" dari sisi audio, bukan cuma visual.

- [ ] Tambah setting `crossfadeDurationMs` (0 = off, default) di `SettingsStore`.
- [ ] Modifikasi `PlaybackService`/`PlayerGateway`: saat mendekati akhir track & ada next track, overlap volume-fade dua `ExoPlayer` instance (atau satu instance dengan dual `MediaItem` transition + volume automation jika Media3 versi yang dipakai mendukung gapless+fade).
- [ ] Test edge case: skip manual saat crossfade sedang jalan, radio/stream track (bukan file lokal), track terakhir di queue (no crossfade, natural stop).

**Definition of Done:** crossfade nyala smooth tanpa gap suara/dobel, bisa dimatikan, tidak break existing queue/notification controls.

---

## M5 — Lyrics Polish (stretch)
- [ ] Karaoke-style per-word highlight di komponen lyrics existing (`LyricCardGenerator`/`LyricRepository`), asalkan sumber data lirik yang dipakai IanPlayer mendukung timestamp per kata (kalau cuma per-line, fallback tetap per-line highlight yang lebih halus animasinya).

**Definition of Done:** stretch goal, tidak blocking rilis M0–M4.

---

## M6 — QA, Polish, Regression Pass
- [ ] Full regression: streaming (YTMusicRepository), download, housekeeping-equivalent existing features (voice assistant, wave record, recap, social signal) tetap jalan di semua 4 layout.
- [ ] Battery/perf sanity check dengan canvas+crossfade+animasi aktif bareng.
- [ ] Update README/CHANGELOG IanPlayer, catat kredit konsep referensi (Convx/vivi-music) di dokumentasi internal (bukan source code).

---

## Priority Order (kalau waktu terbatas)
1. M0 (wajib, fondasi)
2. M1 (impact langsung ke "smooth" tanpa effort besar)
3. M3 + M2 bareng (fitur utama yang diminta: layout switcher + canvas)
4. M4 (crossfade, nice tapi lebih rumit di playback service)
5. M5 (stretch)
