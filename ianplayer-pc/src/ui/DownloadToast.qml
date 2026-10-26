import QtQuick
import QtQuick.Controls as C
import QtQuick.Layouts
import IanPlayer.PC

Rectangle {
    id: root

    property string statusText: ""
    property string titleText: ""
    property real progress: 0.0
    property bool active: false

    width: 280
    height: 64
    radius: Theme.radiusCard
    color: Theme.osdBg
    border.color: Theme.borderGlassBright
    border.width: 1
    visible: active || hideTimer.running
    opacity: active ? 1.0 : 0.0

    Behavior on opacity { NumberAnimation { duration: 200; easing.type: Easing.OutCubic } }

    Timer {
        id: hideTimer
        interval: 4000
        onTriggered: root.active = false
    }

    Connections {
        target: DownloaderService

        function onDownloadProgress(received, total) {
            root.statusText = qsTr("Mengunduh...")
            root.progress = total > 0 ? (received / total) : 0.0
            root.active = true
            hideTimer.stop()
        }

        function onDownloadFinished(path) {
            root.statusText = qsTr("Pengunduhan Selesai")
            root.progress = 1.0
            root.active = true
            hideTimer.restart()
        }

        function onDownloadError(message) {
            root.statusText = qsTr("Gagal Mengunduh: %1").arg(message)
            root.progress = 0.0
            root.active = true
            hideTimer.restart()
        }
    }

    ColumnLayout {
        anchors.fill: parent
        anchors.margins: 10
        spacing: 4

        Text {
            text: root.statusText
            color: Theme.textPrimary
            font { family: Theme.fontFamily; pixelSize: Theme.sizeSmall; weight: Theme.weightBold }
            elide: Text.ElideRight
            Layout.fillWidth: true
        }

        C.ProgressBar {
            Layout.fillWidth: true
            value: root.progress
            from: 0.0; to: 1.0
        }
    }
}
