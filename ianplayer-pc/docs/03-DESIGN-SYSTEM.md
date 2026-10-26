# IanPlayer PC — Design System (Fluid Glass UI)

Sumber data: `ui-ux-pro-max` (style: Dark Mode OLED + Glassmorphism, palette: Video Streaming/OTT, typography: Inter Modern Dark, UX guidelines keyboard & focus).
Mode utama: **dark**, ikut default Android (`is_dark_mode` on). Referensi visual: Spotify Desktop + macOS Acrylic Glass + Cyberpunk Frosted Aesthetics.

---

## 1. Visual Architecture & Liquid Glass Layering

IanPlayer PC menggunakan arsitektur **3 Layer Visual**:

```
[Layer 3: Controls & Text]      — High contrast textPrimary (#F8FAFC), accent buttons (#E11D48)
[Layer 2: Translucent Glass]    — Translucent panels (5-12% opacity) + 1px white border (10% opacity) + QML MultiEffect blur
[Layer 1: Ambient Background]   — Deep OLED black (#08080A) dengan radial gradient & ambient artwork blur
```

### 1.1 QML Glass Implementation (IanGlass / GlassPanel)
- **Primary Glass Surface**: `Qt.rgba(255, 255, 255, 0.05)` s/d `Qt.rgba(255, 255, 255, 0.08)`.
- **Glass Border**: `1px` border `Qt.rgba(255, 255, 255, 0.10)` dengan `radius: 10px` s/d `16px`.
- **Top Inner Shine**: Linear gradient tipis 1px di batas atas panel (`Qt.rgba(255, 255, 255, 0.15)` -> `transparent`).
- **Backdrop Blur**: `MultiEffect` dengan `blurMax: 32`, `blur: glassBlur / 40`, `saturation: 0.2 + glassVibrancy * 0.4`.
- **Performance Fallback**: Jika GPU terdeteksi lemah atau software rendering (`llvmpipe`), matikan `MultiEffect` blur; gunakan solid semi-transparent surface (`#121216`) dengan 1px border.

---

## 2. Design Tokens (`Theme.qml`)

Semua token warna didefinisikan sebagai singleton QML (`Theme.qml`). **Dilarang keras hardcode hex acak** atau menggunakan komponen bawaan default tanpa style (seperti tombol putih polos / input box raw).

```qml
// Theme.qml
pragma Singleton
import QtQuick

QtObject {
    // Surface Base (OLED Dark)
    readonly property color bg:                  "#08080A"   // Window root & canvas background
    readonly property color bgGradientTop:       "#0D0E15"   // Subtle top ambient gradient
    readonly property color bgGradientBottom:    "#050507"   // Deep bottom background

    // Translucent Glass Surfaces
    readonly property color glassSurface:        Qt.rgba(0.08, 0.09, 0.12, 0.55) // Card, list row
    readonly property color glassSurfaceHover:   Qt.rgba(0.14, 0.15, 0.20, 0.70) // Item hover state
    readonly property color glassSurfaceActive:  Qt.rgba(0.18, 0.20, 0.26, 0.85) // Item active/pressed
    readonly property color glassPanel:          Qt.rgba(0.06, 0.07, 0.09, 0.65) // Sidebar, mini player dock

    // Glass Input & Control Surfaces (Menghilangkan box putih polos raw)
    readonly property color inputBg:             Qt.rgba(1, 1, 1, 0.06)
    readonly property color inputBgHover:        Qt.rgba(1, 1, 1, 0.10)
    readonly property color inputBgFocused:      Qt.rgba(1, 1, 1, 0.14)
    readonly property color inputBorder:         Qt.rgba(1, 1, 1, 0.12)
    readonly property color inputBorderFocused:  "#E11D48"

    readonly property color buttonBg:            Qt.rgba(1, 1, 1, 0.08)
    readonly property color buttonBgHover:       Qt.rgba(1, 1, 1, 0.15)
    readonly property color buttonBgPressed:     Qt.rgba(1, 1, 1, 0.22)

    // Text Tokens
    readonly property color textPrimary:         "#F8FAFC"   // Judul utama, heading, active state
    readonly property color textSecondary:       "#94A3B8"   // Subtitle, artist, album, meta
    readonly property color textMuted:           "#64748B"   // Placeholder text, disabled text

    // Accent (IanPlayer Rose Red)
    readonly property color accent:              "#E11D48"   // Play buttons, active indicators, sliders
    readonly property color accentHover:         "#F43F5E"   // Hover state accent
    readonly property color accentGlow:          Qt.rgba(225, 29, 72, 0.35) // Radial glow accent
    readonly property color onAccent:            "#FFFFFF"   // Text on accent button

    // Borders & Focus
    readonly property color borderGlass:         Qt.rgba(1, 1, 1, 0.10) // Standard subtle border
    readonly property color borderGlassBright:   Qt.rgba(1, 1, 1, 0.20) // Highlighted border
    readonly property color focusRing:          "#F8FAFC"   // Keyboard accessibility focus
    readonly property color destructive:         "#EF4444"   // Hapus / danger action

    // Overlays
    readonly property color scrim:              Qt.rgba(0, 0, 0, 0.65) // Dialog backdrop
    readonly property color osdBg:              Qt.rgba(8, 8, 10, 0.85) // OSD Volume / Popup
}
```

### Kontras & Accessibility Rules
- `textPrimary` di `bg`/`glassSurface`: **15:1+** (sangat aman).
- `textSecondary` di `glassSurface`: **>= 4.5:1** (lulus WCAG AA).
- `textMuted` sebagai placeholder: **>= 3:1** (hanya untuk placeholder, **dilarang menumpuk/overlap dengan text terisi**).
- `focusRing`: Indikator fokus keyboard wajib terlihat jelas 2px outline dengan offset 2px.

---

## 3. Custom Glass Components Specification

Semua UI kontrol di-refactor menggunakan komponen custom berbasis `GlassPanel` untuk membuang gaya default Qt Quick Controls yang tampak seperti box putih polos.

### 3.1 `GlassTextField.qml` (Input Pencarian & Teks)
- **Bentuk**: Rounded rectangle `radius: 10px`, tinggi `40px`.
- **Background**: Translucent `Theme.inputBg`, berubah ke `inputBgHover` saat mouse hover, dan `inputBgFocused` saat terfokus.
- **Border**: `1px border` `Theme.inputBorder`, berubah menjadi `Theme.accent` dengan subtle glow saat terfokus.
- **Placeholder Handling**: Teks placeholder `Theme.textMuted` tersembunyi total begitu ada input teks (`text.length > 0`). **Mencegah bug text overlapping**.
- **Ikon**: Magnifying glass SVG di sebelah kiri (`18px`, `textSecondary`), clear text button (`×`) di sebelah kanan saat input terisi.

### 3.2 `GlassButton.qml` (Tombol Aksi & Navigasi)
- **Bentuk**: Rounded rectangle `radius: 10px` (atau `20px` pill style), tinggi `40px`.
- **Varian**:
  - `Secondary / Standard`: Background `Theme.buttonBg`, border `Theme.borderGlass`, text `Theme.textPrimary`.
  - `Primary / Accent`: Background `Theme.accent`, glow `Theme.accentGlow`, text `Theme.onAccent` (Bold).
- **Interaksi**:
  - Hover: Scale `1.02`, background memterang (`buttonBgHover`), border memterang.
  - Press: Scale `0.98`, background `buttonBgPressed`.
  - Transititon: `Behavior on scale`, `Behavior on color` (150ms `OutCubic`).

### 3.3 `GlassComboBox.qml` (Dropdown Sorting & Filter)
- **Bentuk**: Custom glass container `radius: 10px`, tinggi `40px`, background `Theme.buttonBg`.
- **Popup List**: Translucent frosted glass panel dengan drop shadow `blur: 16`, item list ber-highlight hover `Theme.glassSurfaceHover`.
- **Arrow**: Chevron down SVG teranimasi rotasi 180° saat popup terbuka.

### 3.4 `GlassCard.qml` (Kartu Album / Artist / Playlist)
- **Bentuk**: Square / rounded card `radius: 12px`, padding `12px`.
- **Background**: `Theme.glassSurface` + 1px `Theme.borderGlass`.
- **Hover Effect**:
  - Card lift: Translate Y `-4px`, Scale `1.02`.
  - Artwork zoom: Scale artwork inside clipping rect `1.05`.
  - Play button overlay: Floating accent play button `44px` fade-in di kanan bawah artwork.
  - Duration: 200ms `OutCubic`.

---

## 4. Tipografi

Satu keluarga font utama: **Inter** (termasuk static TTF di `resources/fonts/`).

| Token | Weight | Size | Line Height | Penggunaan |
|---|---|---|---|---|
| display | 700 (Bold) | 44px | 1.05 | Title splash, empty state hero, recap |
| h1 | 700 (Bold) | 32px | 1.15 | Screen title (Songs, Stream, Playlists) |
| h2 | 600 (SemiBold) | 24px | 1.20 | Section header, header detail album |
| h3 | 600 (SemiBold) | 16px | 1.30 | Judul lagu di list/grid, nama playlist |
| body | 400 (Regular) | 14px | 1.45 | Subtitle, deskripsi, lirik non-aktif |
| small | 400 (Regular) | 12px | 1.40 | Meta: nama artis, durasi, bitrate, year |
| label | 600 (SemiBold) | 11px | 1.20 | Uppercase section tracking +0.8px |

- Durasi & timestamp (`03:45`): Menggunakan tabular numbers (`font.features: { "tnum": 1 }`) untuk menghentikan jitter saat seek.

---

## 5. Spacing, Layout & Rail Navigation

- **Grid System**: Kelipatan 8px (`4px`, `8px`, `12px`, `16px`, `24px`, `32px`, `48px`).
- **Sidebar Rail (Navigasi Kiri)**:
  - Translucent frosted panel (`width: 220px`).
  - Active Tab Pill: Accent indicator bar `4px` di tepi kiri + background `Theme.glassSurfaceActive` dengan rounded radius `8px`.
  - Reorderable tabs via drag dengan smooth row animation.
- **Main View Area**:
  - Content padding `24px`.
  - Header area: Judul H1 + Search bar & Filter controls di sebelah kanan sejajar secara visual.
- **Mini Player Dock (Bawah Window)**:
  - Frosted glass bar tinggi `72px`, terpasang di bagian bawah window.
  - Top edge accent line `1px` `Theme.borderGlassBright`.
  - Progress bar mini `2px` di paling atas bar dengan hover expand ke `6px`.

---

## 6. Fluid Motion & Transition Specifications

Semua animasi diatur agar terasa responsif (120ms – 220ms) tanpa membuat UI terasa lambat atau janky.

```qml
// Standard Transition Behaviours
Behavior on opacity { NumberAnimation { duration: 180; easing.type: Easing.OutCubic } }
Behavior on scale   { NumberAnimation { duration: 150; easing.type: Easing.OutCubic } }
Behavior on color   { ColorAnimation  { duration: 150; easing.type: Easing.OutCubic } }
```

### 6.1 Transition Scenarios
1. **Tab Switching**:
   - Fade out current tab content (`opacity 1 -> 0`, `duration 100ms`), fade in new tab content (`opacity 0 -> 1`, `translate Y 8px -> 0px`, `duration 180ms`).
2. **Hover Interactions**:
   - Button / Card: Color fade `150ms`, Scale `1.0 -> 1.02` `150ms OutCubic`.
3. **Now Playing Panel Open / Minimize**:
   - Slide & scale from mini player: Y offset transition `220ms OutCubic`.
4. **Dialog Open**:
   - Scrim fade-in `150ms`, Dialog container scale `0.95 -> 1.0` + opacity `0 -> 1` (`180ms OutCubic`).
5. **Accessibility / Reduce Motion**:
   - Jika `SettingsService.reduceMotion` aktif atau OS `reduceMotion` terdeteksi, matikan animasi scale dan translate. Gunakan instant opacity transition (50ms).

---

## 7. Keyboard Focus & Accessibility

- **Focus Indicator**: Indikator fokus keyboard wajib terlihat berupa ring outline 2px `Theme.focusRing` saat elemen difokuskan via keyboard navigation.
- **Accessible Names**: Semua ikon tombol (`Play`, `Pause`, `Next`, `Previous`, `Shuffle`, `Repeat`, `Scan`) wajib menyertakan `Accessible.name`.
- **Keyboard Shortcuts**:
  - `Space`: Play / Pause toggle.
  - `Left` / `Right`: Seek ±10s.
  - `Up` / `Down`: Adjust Volume.
  - `N` / `P`: Next / Previous track.
  - `S` / `R`: Shuffle / Repeat toggle.
  - `Esc`: Close dialog / minimize Now Playing.

---

## 8. Implementation Checklist for UI Refactoring

- [ ] **Theme.qml**: Update token warna & glass translucency variables.
- [ ] **GlassTextField.qml**: Implementasi komponen input glass custom untuk menggantikan `TextField` raw.
- [ ] **GlassButton.qml**: Implementasi tombol glass custom untuk menggantikan `Button` raw.
- [ ] **GlassComboBox.qml**: Implementasi dropdown sorting glass custom untuk menggantikan `ComboBox` raw.
- [ ] **GlassCard.qml**: Implementasi kartu album/artist/playlist dengan hover zoom & lift effect.
- [ ] **AppWindow.qml**: Refactor layout utama dengan background gradient & sidebar glass rail.
- [ ] **SongsPane.qml**: Pasang `GlassTextField`, `GlassButton`, `GlassComboBox`, dan styling list row 64px.
- [ ] **StreamPane.qml**: Pasang `GlassTextField` & `GlassButton` (menghilangkan text overlapping).
- [ ] **PlaylistsPane.qml**: Pasang `GlassButton` & Empty state glass card.
- [ ] **AlbumsPane & ArtistsPane**: Grid view dengan `GlassCard` dan empty state visual.
