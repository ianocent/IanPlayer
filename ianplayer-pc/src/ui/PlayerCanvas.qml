import QtQuick
import IanPlayer.PC

Item {
    id: root

    property string canvasMode: "generative" // "off" | "generative" | "kenburns"
    property bool isPlaying: QueueManager.isPlaying

    // Mode 1: Generative Aurora
    Item {
        id: auroraLayer
        anchors.fill: parent
        visible: root.canvasMode === "generative"
        opacity: 0.45

        Rectangle {
            id: blob1
            width: parent.width * 0.7
            height: parent.height * 0.7
            radius: width / 2
            x: parent.width * 0.1
            y: parent.height * 0.1
            color: Theme.accentGlow

            NumberAnimation on rotation {
                from: 0; to: 360
                duration: 24000
                loops: Animation.Infinite
                running: root.isPlaying && root.canvasMode === "generative"
            }
        }

        Rectangle {
            id: blob2
            width: parent.width * 0.6
            height: parent.height * 0.6
            radius: width / 2
            x: parent.width * 0.4
            y: parent.height * 0.3
            color: Qt.rgba(0.12, 0.11, 0.29, 0.5)

            NumberAnimation on rotation {
                from: 360; to: 0
                duration: 32000
                loops: Animation.Infinite
                running: root.isPlaying && root.canvasMode === "generative"
            }
        }

        Rectangle {
            id: blob3
            width: parent.width * 0.5
            height: parent.height * 0.5
            radius: width / 2
            x: parent.width * 0.2
            y: parent.height * 0.4
            color: Qt.rgba(0.01, 0.52, 0.78, 0.35)

            SequentialAnimation on opacity {
                loops: Animation.Infinite
                running: root.isPlaying && root.canvasMode === "generative"
                NumberAnimation { to: 0.2; duration: 4000; easing.type: Easing.InOutQuad }
                NumberAnimation { to: 0.6; duration: 4000; easing.type: Easing.InOutQuad }
            }
        }
    }

    // Mode 2: Ken Burns Artwork Animation
    Item {
        id: kenBurnsLayer
        anchors.fill: parent
        visible: root.canvasMode === "kenburns" && QueueManager.currentArt !== ""
        clip: true

        Image {
            id: kbImage
            anchors.centerIn: parent
            width: parent.width * 1.2
            height: parent.height * 1.2
            source: QueueManager.currentArt
            fillMode: Image.PreserveAspectCrop
            opacity: 0.25

            SequentialAnimation on scale {
                loops: Animation.Infinite
                running: root.isPlaying && root.canvasMode === "kenburns"
                NumberAnimation { to: 1.15; duration: 15000; easing.type: Easing.InOutQuad }
                NumberAnimation { to: 1.0; duration: 15000; easing.type: Easing.InOutQuad }
            }
        }
    }
}
