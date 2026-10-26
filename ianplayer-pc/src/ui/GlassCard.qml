import QtQuick

// GlassCard (M5.2)
Rectangle {
    id: root
    property alias content: contentItem.data

    radius: Theme.radiusCard
    color: Theme.glassSurface
    border.color: Theme.borderGlass
    border.width: 1
    scale: mouse.containsMouse ? 1.02 : 1.0
    transform: Translate { y: mouse.containsMouse ? -4 : 0 }

    Behavior on scale { NumberAnimation { duration: 200; easing.type: Easing.OutCubic } }

    Item {
        id: contentItem
        anchors.fill: parent
        anchors.margins: 12
    }

    MouseArea {
        id: mouse
        anchors.fill: parent
        hoverEnabled: true
    }
}
