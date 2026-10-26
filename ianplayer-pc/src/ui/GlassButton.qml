import QtQuick
import QtQuick.Controls as C

// GlassButton (M5.2)
Rectangle {
    id: root
    property string text: ""
    property bool primary: false
    signal clicked()

    height: Theme.controlHeight
    radius: Theme.radius
    color: primary ? Theme.accent : (mouse.pressed ? Theme.buttonBgPressed : (mouse.containsMouse ? Theme.buttonBgHover : Theme.buttonBg))
    border.color: primary ? Theme.accent : Theme.borderGlass
    border.width: 1
    scale: mouse.pressed ? 0.98 : (mouse.containsMouse ? 1.02 : 1.0)

    Behavior on color { ColorAnimation { duration: 150; easing.type: Easing.OutCubic } }
    Behavior on scale { NumberAnimation { duration: 150; easing.type: Easing.OutCubic } }

    Text {
        anchors.centerIn: parent
        text: root.text
        color: root.primary ? Theme.onAccent : Theme.textPrimary
        font { family: Theme.fontFamily; pixelSize: Theme.sizeBody; weight: root.primary ? Theme.weightBold : Theme.weightMedium }
    }

    MouseArea {
        id: mouse
        anchors.fill: parent
        hoverEnabled: true
        cursorShape: Qt.PointingHandCursor
        onClicked: root.clicked()
    }
}
