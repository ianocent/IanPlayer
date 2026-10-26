import QtQuick
import QtQuick.Controls as C

// GlassTextField (M5.2): input glass custom
Rectangle {
    id: root
    property alias text: input.text
    property string placeholderText: ""
    property int echoMode: TextInput.Normal
    signal accepted()

    height: Theme.controlHeight
    radius: Theme.radius
    color: input.activeFocus ? Theme.inputBgFocused : (mouse.containsMouse ? Theme.inputBgHover : Theme.inputBg)
    border.color: input.activeFocus ? Theme.inputBorderFocused : Theme.inputBorder
    border.width: 1

    Behavior on color { ColorAnimation { duration: 150; easing.type: Easing.OutCubic } }

    RowLayout {
        anchors.fill: parent
        anchors.leftMargin: 12
        anchors.rightMargin: 8
        spacing: 8

        Text {
            text: "\u2315"
            color: Theme.textSecondary
            font.pixelSize: 16
            visible: input.text.length === 0
        }

        TextInput {
            id: input
            Layout.fillWidth: true
            Layout.fillHeight: true
            verticalAlignment: TextInput.AlignVCenter
            color: Theme.textPrimary
            font { family: Theme.fontFamily; pixelSize: Theme.sizeBody }
            echoMode: root.echoMode
            onAccepted: root.accepted()
            onTextChanged: placeholder.visible = (text.length === 0)

            Text {
                id: placeholder
                anchors.fill: parent
                verticalAlignment: Text.AlignVCenter
                text: root.placeholderText
                color: Theme.textMuted
                font { family: Theme.fontFamily; pixelSize: Theme.sizeBody }
                visible: input.text.length === 0
            }
        }

        C.AbstractButton {
            visible: input.text.length > 0
            width: 20; height: 20
            contentItem: Text { text: "\u00D7"; color: Theme.textSecondary; horizontalAlignment: Text.AlignHCenter; verticalAlignment: Text.AlignVCenter }
            onClicked: input.clear()
        }
    }

    MouseArea {
        id: mouse
        anchors.fill: parent
        hoverEnabled: true
        acceptedButtons: Qt.NoButton
    }
}
