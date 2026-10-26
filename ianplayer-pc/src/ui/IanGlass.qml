import QtQuick
import QtQuick.Effects

// IanGlass (M5.2): panel frost sederhana. Blur via MultiEffect pada layer sendiri
// (backdrop blur penuh tidak tersedia di Qt6; ini simulasi).
Rectangle {
    id: root

    property real glassBlur: SettingsService ? SettingsService.glassBlur : 16
    property real glassVibrancy: SettingsService ? SettingsService.glassVibrancy : 0.3
    property color tintColor: Qt.rgba(1, 1, 1, 0.08)

    color: tintColor
    radius: Theme.radius * 2
    border.color: Qt.rgba(1, 1, 1, 0.12)
    border.width: 1

    layer.enabled: true
    layer.effect: MultiEffect {
        blurEnabled: true
        blur: Math.min(1.0, root.glassBlur / 40.0)
        blurMax: 64
    }
}
