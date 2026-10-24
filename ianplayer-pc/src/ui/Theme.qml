pragma Singleton
import QtQuick

// Design tokens — sumber: docs/03-DESIGN-SYSTEM.md
// Dilarang hardcode hex di screen lain; selalu lewat Theme.*
QtObject {
    // Surfaces — urut dari paling gelap ke paling terang
    readonly property color bg:          "#000000"
    readonly property color surface1:    "#0C0C0D"
    readonly property color surface2:    "#181818"
    readonly property color surface3:    "#1E1B4B"

    // Text
    readonly property color textPrimary:   "#F8FAFC"
    readonly property color textSecondary: "#94A3B8"
    readonly property color textDisabled:  "#555B66"

    // Accent — play red
    readonly property color accent:        "#E11D48"
    readonly property color accentHover:   "#F43F5E"
    readonly property color accentContrast: "#FFFFFF"

    // Border & state
    readonly property color border:       "#2A2D35"
    readonly property color focusRing:    "#F8FAFC"
    readonly property color destructive:  "#EF4444"

    // Overlay khusus player / now playing
    readonly property color scrim:        Qt.rgba(0,0,0,0.6)
    readonly property color osdBg:        Qt.rgba(0,0,0,0.72)

    readonly property int spacingUnit: 8
    readonly property int radius: 8
    readonly property int radiusCard: 12
    readonly property int radiusArtwork: 20

    // Tipografi — keluarga "Inter" (registered via QFontDatabase, 4 weight)
    // token sesuai docs/03-DESIGN-SYSTEM.md bagian 2
    readonly property string fontFamily: "Inter"
    readonly property string fontFamilyFallback: "Segoe UI"

    readonly property int sizeDisplay: 44   // 700 — empty state, splash
    readonly property int sizeH1: 32        // 600 — screen title
    readonly property int sizeH2: 24        // 600 — section title
    readonly property int sizeH3: 18        // 500 — card title
    readonly property int sizeH4: 15        // 600 — pane sub-title (Songs pane)
    readonly property int sizeBody: 16      // 400 — isi
    readonly property int sizeSmall: 13     // 400 — artist, durasi
    readonly property int sizeLabel: 12     // 500 — uppercase, +0.8 tracking

    readonly property int weightRegular: 400
    readonly property int weightMedium: 500
    readonly property int weightSemiBold: 600
    readonly property int weightBold: 700

    // Kontrol baku (bagian 3)
    readonly property int controlHeight: 40
    readonly property int rowHeight: 64
}
