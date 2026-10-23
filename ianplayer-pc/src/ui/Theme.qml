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
    readonly property color onAccent:      "#FFFFFF"

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
}
