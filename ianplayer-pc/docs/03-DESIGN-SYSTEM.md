# IanPlayer PC — Design System

Sumber data: ui-ux-pro-max (style: Dark Mode OLED, palette: Video Streaming/OTT, typography: Modern Dark Cinema/Inter, UX guidelines keyboard & focus).
Mode utama: **dark**, ikut default Android (`is_dark_mode` on). Referensi rasa: Spotify/musik streaming, bukan aplikasi kantor.

## 1. Design tokens (warna)

Semua warna didefinisikan sekali sebagai singleton QML (`Theme.qml`). Dilarang hardcode hex di screen lain.

```qml
// Theme.qml
pragma Singleton
import QtQuick

QtObject {
    // Surfaces — urut dari paling gelap ke paling terang
    readonly property color bg:          "#000000"   // window root, area player
    readonly property color surface1:    "#0C0C0D"   // card, list item
    readonly property color surface2:    "#181818"   // panel sekunder, sidebar
    readonly property color surface3:    "#1E1B4B"   // surface ber-aksen (dialog, pill)

    // Text
    readonly property color textPrimary:   "#F8FAFC" // heading, judul lagu aktif
    readonly property color textSecondary: "#94A3B8" // meta: artist, durasi, size
    readonly property color textDisabled:  "#555B66"

    // Accent — play red
    readonly property color accent:        "#E11D48" // tombol play, progress, state aktif
    readonly property color accentHover:   "#F43F5E"
    readonly property color onAccent:      "#FFFFFF"

    // Border & state
    readonly property color border:       "#2A2D35"
    readonly property color focusRing:    "#F8FAFC"
    readonly property color destructive:  "#EF4444"

    // Overlay khusus player / now playing
    readonly property color scrim:        Qt.rgba(0,0,0,0.6)
    readonly property color osdBg:        Qt.rgba(0,0,0,0.72)
}
```

Aturan kontras (hasil hitung, bukan perasaan):

| Pasangan | Kontras minimum | Catatan |
|---|---|---|
| textPrimary di bg/surface | 14:1+ | aman |
| textSecondary di surface1 | >=4.5:1 | `#94A3B8` di `#0C0C0D` ~5.2:1, lolos |
| accent di bg | >=3:1 (non-text) | `#E11D48` di `#000000` ~4.0:1, lolos |
| onAccent (putih di `#E11D48`) | >=4.5:1 | ~4.4:1 — hanya untuk text besar/bold; body text jangan ditaruh di atas accent |
| focusRing di semua surface | >=3:1 | lolos |

Dari anti-pattern skill, hindari:

- Background putih penuh di section manapun saat dark mode.
- Glow/drop shadow di semua item; glow hanya untuk artwork aktif dan elemen hero.
- Warna acak di luar token (EQ bars, canvas aurora kelola palette-nya sendiri tapi dibatasi token + palet artwork).

## 2. Tipografi

Satu keluarga font: **Inter** (bundel ke aplikasi; Windows 10 tidak bawa Inter).

| Token | Weight | Size | Line height | Penggunaan |
|---|---|---|---|---|
| display | 700 | 44px | 1.05 | Judul empty state, splash, recap title |
| h1 | 600 | 32px | 1.15 | Screen title |
| h2 | 600 | 24px | 1.2 | Section title |
| h3 | 500 | 18px | 1.3 | Card title, judul lagu di grid |
| body | 400 | 16px | 1.5 | Isi, deskripsi, lyrics non-aktif |
| small | 400 | 13px | 1.4 | Meta: artist, durasi, timestamp |
| label | 500 | 12px | 1.2 | Uppercase +0.8px tracking, section header kecil |

- Download Inter dari Google Fonts (file `.ttf`), taruh di `resources/fonts/`, register via `QFontDatabase::addApplicationFont` di `main.cpp`.
- Timestamp dan durasi (`01:23:45`) pakai tabular numbers (`font.features: { "tnum": 1 }`) biar tidak jitter saat seek jalan.
- Judul panjang: maksimal 2 baris lalu ellipsis. Jangan wrap lebih dari 2.
- Lyrics aktif (karaoke): boleh lebih besar (20–24px) karena ini elemen fokus, tapi hanya di lyrics panel.

## 3. Spacing & sizing

- Grid 8px: 4, 8, 12, 16, 24, 32, 48, 64.
- Padding komponen: 12px kecil, 16px standar, 24px besar.
- Radius: 8px (button, input), 12px (card, dialog), 20px (artwork besar, hero card).
- Ikon (SVG bundel): 20px (inline text), 24px (default toolbar/nav), 32px (aksi besar di now playing).
- Tinggi kontrol: button 40px, input 40px, row lagu 64px, row album/playlist 88px.
- Target hit minimum: 40x40px. Klik area seek bar >= 20px tinggi walau track yang terlihat cuma 6px.

## 4. Motion

| Interaksi | Durasi | Easing |
|---|---|---|
| Hover state | 120ms | linear |
| Now Playing buka/minimize | 220ms | OutCubic |
| Panel slide (queue, lyrics, sidebar) | 220ms | OutCubic |
| Dialog buka | 180ms | OutCubic, scale 0.96->1 + fade |
| Artwork hover scale | 200ms | OutBack, scale 1.0->1.03 |
| Ambient palette transisi | 500ms | OutCubic |
| Voice spectrum / canvas aurora | frame-driven | bebas tapi hormati motion_scale |

Anti-pattern: transisi 300ms+ di hover, animasi masuk yang terlalu lambat, animasi berat di seek bar (bikin janky saat lagu jalan). Hormati setting `reduce_motion` (sama seperti Android): matikan animasi non-kritis saat aktif, dan `qApp->styleHints()->reduceMotion()` dari sistem juga dihormati.

## 5. Komponen kunci

### 5.1 Now Playing screen (paling penting)

Di desktop Now Playing bukan fullscreen paksa seperti Android, tapi **panel besar yang bisa minimize ke mini player** (jembatan dari gesture vertical-dismiss Android).

- **Root:** artwork besar (radius 20, max 420px) di atas, title + artist di bawahnya, seek bar, kontrol, toggle queue di kanan.
- **Ambient palette:** latar gradasi lembut dari warna dominan artwork (dominan + sekunder, didim-masukkan >80% supaya text tetap kontras). Paritas dengan `AmbientPaletteController` Android. Detail teknis di `02-ARCHITECTURE.md`.
- **Seek bar:** 4 style dari Android tetap ada: `classic / wave / squiggle / minimal` (key `seek_bar_style`). Track 6px, thumb 14px muncul saat hover/pause. Buffer track tidak relevan (stream buffer pendek), buang.
- **Kontrol:** shuffle, prev, play/pause (56px, accent), next, repeat (off/all/one), queue, lyrics. Icon 24px, tooltip.
- **Lyrics panel:** overlay + highlight karaoke per baris (LRC). Source indicator nomor 1..8 di pojok, persis Android. Baris aktif: textPrimary besar; baris yang belum: textSecondary redup; yang sudah lewat: nyaris hilang.
- **Ultra glass (IanGlass):** panel frost di belakang info lagu (default `glass_blur` 16, `glass_vibrancy` 0.3). Implementasi QML di dokumen arsitektur.

Keyboard Now Playing (slot kebiasaan desktop, geser dari gesture Android yang ada):

| Key | Aksi |
|---|---|
| `Space` | play/pause |
| `<-` / `->` | rewind/forward 10s |
| `Up` / `Down` | volume |
| `N` / `P` | next / previous |
| `S` | shuffle toggle |
| `R` | repeat cycle |
| `L` | lyrics toggle |
| `Q` | queue panel |
| `Esc` | minimize Now Playing |

### 5.2 Library view

- **Grid album:** square 1:1, tile 180-220px, jumlah kolom responsif terhadap lebar window.
- **Row lagu:** artwork kecil 48px + judul (h3) + artist (small) + durasi (small, tabular). Hover: surface1 ke surface2 + aksi tombol muncul.
- **Fast scroller:** vertical di kanan (paritas fitur Android), preview huruf A-Z saat di-drag.
- **Empty state:** display font + ilustrasi line-art SVG + tombol "Scan Folder".
- Tab (Stream / Songs / Playlists / Albums) bisa reorder; persist pakai QSettings `tab_order`, format sama dengan Android: `"stream,songs,playlists,albums"`.

### 5.3 Mini player

Paritas dengan 4 layout Android (`mini_layout_index`: Default/Floating/Compact/Queue, `is_pill_at_bottom`):

- Gaya yang disarankan di desktop: dock bar full-width di bawah window, 72px.
- Isi: artwork 56px, title + artist, kontrol prev/play/next, seek bar mini full-width di tepi atas bar, tombol queue + volume.
- 4 variant Android diterjemahkan sebagai 4 mode density mini bar (full/compact/floating card/queue-first) di milestone visual.

### 5.4 Dialog, settings, recap

- Dialog: max width 480px, radius 12, surface1, drop shadow blur 24 opacity 0.5.
- Settings: sidebar kiri kategori + konten kanan (bukan tab horizontal). Semua key dari `SettingsStore` Android dipetakan 1:1 (`motion_scale`, `canvas_mode`, `glass_blur`, `glass_vibrancy`, `seek_bar_style`, `spotify_sp_dc`, dsb.).
- Recap (Monthly Recap ala Wrapped): full screen kartu statistik — top lagu, top artist, menit listen, taste comment. Nanti di M6 bisa tambah export PNG untuk share (fitur baru desktop, bukan port).

## 6. Keyboard & accessibility (wajib, dari UX guidelines)

- **Setiap kontrol interaktif punya focus ring terlihat** (2px focusRing + offset 2px). `visible: activeFocus && !mouseOnly`. Jangan hilangkan indikator fokus di dark mode.
- Tab order mengikuti urutan visual.
- Shortcut overlay: tekan `?` buka dialog semua shortcut, dikelompokkan Playback / Volume / Navigation / Queue.
- Semua tombol ikon punya `Accessible.name`. Tidak ada tombol ikon tanpa nama aksesibilitas.
- Dialog scrim: diuji 60% hitam, konten di belakang tidak terbaca, fokus terkunci di dalam dialog (focus trap manual via `KeyNavigation`).
- Kontras 4.5:1 untuk semua body text kalau light mode nanti diproduksi.

## 7. Futures (out of scope sekarang)

- Light mode. Kalau mau, tambah token set kedua di Theme.qml, jangan hardcode.
- Dynamic accent dari sistem Windows. Skip.
- Font custom user via settings (load file .ttf). Phase M5.
