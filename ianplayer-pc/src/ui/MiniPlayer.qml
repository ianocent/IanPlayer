import QtQuick
import QtQuick.Layouts
import QtQuick.Controls
import QtQuick.Controls as C
import IanPlayer.PC 1.0

Item {
    id: root
    visible: QueueManager.queueCount > 0
    property int layoutIndex: SettingsService.miniLayoutIndex // 0: Standard, 1: Compact Pill, 2: Glass Card, 3: Expanded
    property bool isPillAtBottom: SettingsService.isPillAtBottom

    signal openNowPlayingRequested()

    // Height depends on layout index
    implicitHeight: layoutIndex === 3 ? 84 : (layoutIndex === 1 ? 56 : 72)
    implicitWidth: parent ? parent.width : 960

    // ---- Variant 0: Standard Dock (Full Width Bar) ----
    IanGlass {
        id: standardDock
        visible: root.layoutIndex === 0
        anchors.fill: parent

        SeekBar {
            anchors { top: parent.top; left: parent.left; right: parent.right }
            progress: QueueManager.durationMsCurrent() > 0
                      ? QueueManager.positionMs() / QueueManager.durationMsCurrent() : 0
            onSeekRequested: (frac) => {
                if (QueueManager.durationMsCurrent() > 0)
                    QueueManager.seekToMs(frac * QueueManager.durationMsCurrent())
            }
        }

        RowLayout {
            anchors { fill: parent; leftMargin: 12; rightMargin: 12; topMargin: 8; bottomMargin: 8 }
            spacing: 12

            Rectangle {
                Layout.preferredWidth: 48; Layout.preferredHeight: 48
                color: Theme.surface2; radius: Theme.radius; clip: true
                Image { anchors.fill: parent; source: QueueManager.currentArt; fillMode: Image.PreserveAspectCrop; smooth: true }
                MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: root.openNowPlayingRequested() }
            }

            ColumnLayout {
                Layout.fillWidth: true; spacing: 2
                C.Label {
                    Layout.fillWidth: true
                    text: QueueManager.currentTitle.length > 0 ? QueueManager.currentTitle : qsTr("Belum Ada Lagu")
                    color: Theme.textPrimary
                    elide: Text.ElideRight
                    font { family: Theme.fontFamily; pixelSize: Theme.sizeBody; weight: Theme.weightMedium }
                }
                C.Label {
                    Layout.fillWidth: true
                    text: QueueManager.currentArtist
                    color: Theme.textSecondary
                    elide: Text.ElideRight
                    font { family: Theme.fontFamily; pixelSize: Theme.sizeSmall }
                }
            }

            GlassButton {
                Layout.preferredWidth: 36; Layout.preferredHeight: 36
                text: "⏮"
                onClicked: QueueManager.playPrevious()
            }
            GlassButton {
                Layout.preferredWidth: 42; Layout.preferredHeight: 42
                text: QueueManager.isPlaying ? "⏸" : "▶"
                primary: true
                onClicked: QueueManager.togglePlayPause()
            }
            GlassButton {
                Layout.preferredWidth: 36; Layout.preferredHeight: 36
                text: "⏭"
                onClicked: QueueManager.playNext()
            }
            GlassButton {
                Layout.preferredWidth: 36; Layout.preferredHeight: 36
                text: "▲"
                onClicked: root.openNowPlayingRequested()
            }
        }
    }

    // ---- Variant 1: Compact Pill ----
    IanGlass {
        id: compactPill
        visible: root.layoutIndex === 1
        width: Math.min(480, parent.width - 32)
        height: 52
        anchors.horizontalCenter: parent.horizontalCenter
        anchors.bottom: root.isPillAtBottom ? parent.bottom : undefined
        anchors.top: !root.isPillAtBottom ? parent.top : undefined
        anchors.margins: 12
        radius: 26

        RowLayout {
            anchors { fill: parent; leftMargin: 8; rightMargin: 12; topMargin: 4; bottomMargin: 4 }
            spacing: 10

            Rectangle {
                Layout.preferredWidth: 40; Layout.preferredHeight: 40
                color: Theme.surface2; radius: 20; clip: true
                Image { anchors.fill: parent; source: QueueManager.currentArt; fillMode: Image.PreserveAspectCrop; smooth: true }
                MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: root.openNowPlayingRequested() }
            }

            C.Label {
                Layout.fillWidth: true
                text: QueueManager.currentTitle + (QueueManager.currentArtist ? " • " + QueueManager.currentArtist : "")
                color: Theme.textPrimary
                elide: Text.ElideRight
                font { family: Theme.fontFamily; pixelSize: Theme.sizeBody; weight: Theme.weightMedium }
            }

            GlassButton {
                Layout.preferredWidth: 36; Layout.preferredHeight: 36
                text: QueueManager.isPlaying ? "⏸" : "▶"
                primary: true
                onClicked: QueueManager.togglePlayPause()
            }
            GlassButton {
                Layout.preferredWidth: 32; Layout.preferredHeight: 32
                text: "⏭"
                onClicked: QueueManager.playNext()
            }
        }
    }

    // ---- Variant 2: Floating Glass Card ----
    GlassCard {
        id: glassCard
        visible: root.layoutIndex === 2
        width: Math.min(380, parent.width - 32)
        height: 68
        anchors.right: parent.right
        anchors.bottom: parent.bottom
        anchors.margins: 16

        RowLayout {
            anchors { fill: parent; margins: 8 }
            spacing: 10

            Rectangle {
                Layout.preferredWidth: 50; Layout.preferredHeight: 50
                color: Theme.surface2; radius: 10; clip: true
                Image { anchors.fill: parent; source: QueueManager.currentArt; fillMode: Image.PreserveAspectCrop; smooth: true }
                MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: root.openNowPlayingRequested() }
            }

            ColumnLayout {
                Layout.fillWidth: true; spacing: 2
                C.Label {
                    Layout.fillWidth: true
                    text: QueueManager.currentTitle
                    color: Theme.textPrimary
                    elide: Text.ElideRight
                    font { family: Theme.fontFamily; pixelSize: Theme.sizeBody; weight: Theme.weightMedium }
                }
                C.Label {
                    Layout.fillWidth: true
                    text: QueueManager.currentArtist
                    color: Theme.textSecondary
                    elide: Text.ElideRight
                    font { family: Theme.fontFamily; pixelSize: Theme.sizeSmall }
                }
            }

            GlassButton {
                Layout.preferredWidth: 38; Layout.preferredHeight: 38
                text: QueueManager.isPlaying ? "⏸" : "▶"
                primary: true
                onClicked: QueueManager.togglePlayPause()
            }
        }
    }

    // ---- Variant 3: Expanded Controls ----
    IanGlass {
        id: expandedDock
        visible: root.layoutIndex === 3
        anchors.fill: parent

        ColumnLayout {
            anchors { fill: parent; margins: 8 }
            spacing: 4

            SeekBar {
                Layout.fillWidth: true
                progress: QueueManager.durationMsCurrent() > 0
                          ? QueueManager.positionMs() / QueueManager.durationMsCurrent() : 0
                onSeekRequested: (frac) => {
                    if (QueueManager.durationMsCurrent() > 0)
                        QueueManager.seekToMs(frac * QueueManager.durationMsCurrent())
                }
            }

            RowLayout {
                Layout.fillWidth: true; Layout.fillHeight: true
                spacing: 12

                Rectangle {
                    Layout.preferredWidth: 48; Layout.preferredHeight: 48
                    color: Theme.surface2; radius: Theme.radius; clip: true
                    Image { anchors.fill: parent; source: QueueManager.currentArt; fillMode: Image.PreserveAspectCrop; smooth: true }
                    MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: root.openNowPlayingRequested() }
                }

                ColumnLayout {
                    Layout.fillWidth: true; spacing: 2
                    C.Label {
                        Layout.fillWidth: true
                        text: QueueManager.currentTitle
                        color: Theme.textPrimary
                        elide: Text.ElideRight
                        font { family: Theme.fontFamily; pixelSize: Theme.sizeBody; weight: Theme.weightSemiBold }
                    }
                    C.Label {
                        Layout.fillWidth: true
                        text: QueueManager.currentArtist + (QueueManager.currentAlbum ? " — " + QueueManager.currentAlbum : "")
                        color: Theme.textSecondary
                        elide: Text.ElideRight
                        font { family: Theme.fontFamily; pixelSize: Theme.sizeSmall }
                    }
                }

                RowLayout {
                    spacing: 6
                    GlassButton {
                        Layout.preferredWidth: 32; Layout.preferredHeight: 32
                        text: "🔀"
                        onClicked: QueueManager.toggleShuffle()
                    }
                    GlassButton {
                        Layout.preferredWidth: 36; Layout.preferredHeight: 36
                        text: "⏮"
                        onClicked: QueueManager.playPrevious()
                    }
                    GlassButton {
                        Layout.preferredWidth: 44; Layout.preferredHeight: 44
                        text: QueueManager.isPlaying ? "⏸" : "▶"
                        primary: true
                        onClicked: QueueManager.togglePlayPause()
                    }
                    GlassButton {
                        Layout.preferredWidth: 36; Layout.preferredHeight: 36
                        text: "⏭"
                        onClicked: QueueManager.playNext()
                    }
                    GlassButton {
                        Layout.preferredWidth: 32; Layout.preferredHeight: 32
                        text: "🔁"
                        onClicked: QueueManager.toggleRepeat()
                    }
                }
            }
        }
    }
}
