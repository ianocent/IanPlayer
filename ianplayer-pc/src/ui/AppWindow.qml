import QtQuick
import QtQuick.Controls as C
import QtQuick.Layouts
import IanPlayer.PC

ApplicationWindow {
    id: root

    width: 960
    height: 640
    minimumWidth: 800
    minimumHeight: 480
    visible: true
    title: qsTr("IanPlayer")
    color: Theme.bg

    // M0 skeleton — 4 tab placeholder, belum functional (M1 ke atas)
    RowLayout {
        anchors.fill: parent
        spacing: 0

        Rectangle {
            Layout.fillHeight: true
            Layout.preferredWidth: 200
            color: Theme.surface1

            Column {
                id: tabRail
                anchors {
                    top: parent.top
                    left: parent.left
                    right: parent.right
                    topMargin: 24
                }
                spacing: 4

                property int activeIndex: -1

                Repeater {
                    model: ["Stream", "Songs", "Playlists", "Albums"]

                    C.Button {
                        id: tabButton
                        required property string modelData
                        required property int index

                        width: tabRail.width
                        height: 40
                        flat: true

                        background: Rectangle {
                            color: tabButton.hovered
                                   ? Theme.surface2
                                   : (tabRail.activeIndex === tabButton.index
                                      ? Qt.rgba(0,0,0,0.35)
                                      : "transparent")
                            radius: Theme.radius
                        }

                        contentItem: C.Label {
                            text: tabButton.modelData
                            color: tabButton.hovered
                                   ? Theme.textPrimary
                                   : (tabRail.activeIndex === tabButton.index
                                      ? Theme.textPrimary
                                      : Theme.textSecondary)
                            leftPadding: 16
                            verticalAlignment: Text.AlignVCenter
                            font.pixelSize: 14
                        }

                        Accessible.name: tabButton.modelData
                    }
                }
            }

            C.Label {
                anchors {
                    bottom: parent.bottom
                    left: parent.left
                    margins: 12
                }
                text: qsTr("IanPlayer PC v%1").arg(Qt.application.version)
                color: Theme.textDisabled
                font.pixelSize: 12
            }
        }

        Rectangle {
            Layout.fillWidth: true
            Layout.fillHeight: true
            color: Theme.bg
        }
    }
}
