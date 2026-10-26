pragma Singleton
import QtQuick

// Design tokens — sumber: docs/03-DESIGN-SYSTEM.md (updated)
// Dilarang hardcode hex di screen lain; selalu lewat Theme.*
QtObject {
    // Surface Base (OLED Dark)
    readonly property color bg:                  "#08080A"
    readonly property color bgGradientTop:       "#0D0E15"
    readonly property color bgGradientBottom:    "#050507"

    // Translucent Glass Surfaces
    readonly property color glassSurface:        Qt.rgba(0.08, 0.09, 0.12, 0.55)
    readonly property color glassSurfaceHover:   Qt.rgba(0.14, 0.15, 0.20, 0.70)
    readonly property color glassSurfaceActive:  Qt.rgba(0.18, 0.20, 0.26, 0.85)
    readonly property color glassPanel:          Qt.rgba(0.06, 0.07, 0.09, 0.65)

    // Glass Input & Control Surfaces
    readonly property color inputBg:             Qt.rgba(1, 1, 1, 0.06)
    readonly property color inputBgHover:        Qt.rgba(1, 1, 1, 0.10)
    readonly property color inputBgFocused:      Qt.rgba(1, 1, 1, 0.14)
    readonly property color inputBorder:         Qt.rgba(1, 1, 1, 0.12)
    readonly property color inputBorderFocused:  "#E11D48"

    readonly property color buttonBg:            Qt.rgba(1, 1, 1, 0.08)
    readonly property color buttonBgHover:       Qt.rgba(1, 1, 1, 0.15)
    readonly property color buttonBgPressed:     Qt.rgba(1, 1, 1, 0.22)

    // Legacy aliases (backward compat)
    readonly property color surface1:           "#0C0C0D"
    readonly property color surface2:           "#181818"
    readonly property color surface3:           "#1E1B4B"
    readonly property color border:              Qt.rgba(1, 1, 1, 0.10)
    readonly property color borderGlass:         Qt.rgba(1, 1, 1, 0.10)
    readonly property color borderGlassBright:   Qt.rgba(1, 1, 1, 0.20)

    // Text
    readonly property color textPrimary:         "#F8FAFC"
    readonly property color textSecondary:       "#94A3B8"
    readonly property color textMuted:           "#64748B"
    readonly property color textDisabled:        "#555B66"

    // Accent
    readonly property color accent:              "#E11D48"
    readonly property color accentHover:         "#F43F5E"
    readonly property color accentGlow:          Qt.rgba(225, 29, 72, 0.35)
    readonly property color accentContrast:      "#FFFFFF"   // text on accent button

    // Borders & Focus
    readonly property color focusRing:           "#F8FAFC"
    readonly property color destructive:         "#EF4444"

    // Overlays
    readonly property color scrim:              Qt.rgba(0, 0, 0, 0.65)
    readonly property color osdBg:              Qt.rgba(8, 8, 10, 0.85)

    // Spacing / radius
    readonly property int spacingUnit: 8
    readonly property int radius: 10
    readonly property int radiusCard: 12
    readonly property int radiusArtwork: 20

    // Typography
    readonly property string fontFamily: "Inter"
    readonly property string fontFamilyFallback: "Segoe UI"

    readonly property int sizeDisplay: 44
    readonly property int sizeH1: 32
    readonly property int sizeH2: 24
    readonly property int sizeH3: 16
    readonly property int sizeH4: 15
    readonly property int sizeBody: 14
    readonly property int sizeSmall: 12
    readonly property int sizeLabel: 11

    readonly property int weightRegular: 400
    readonly property int weightMedium: 500
    readonly property int weightSemiBold: 600
    readonly property int weightBold: 700

    readonly property int controlHeight: 40
    readonly property int rowHeight: 64
}
