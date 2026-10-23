import QtQuick
import QtQuick.Controls as C
import QtQuick.Layouts
import Qt.labs.settings
import IanPlayer.PC

C.ApplicationWindow {
    id: root

    width: 960
    height: 640
    minimumWidth: 800
    minimumHeight: 480
    visible: true
    title: qsTr("IanPlayer")
    color: Theme.bg
    flags: Qt.Window | Qt.FramelessWindowHint

    function toggleMaximize() {
        if (root.visibility === Window.Maximized)
            root.showNormal();
        else
            root.showMaximized();
    }

    // ---- persist order tab (HKCU\Software\ianocent\IanPlayer\ui) ----
    Settings {
        id: tabsSetting
        category: "ui"

        property string tabOrder: ""
    }

    ListModel {
        id: tabsModel
    }

    Component.onCompleted: {
        const defaults = [qsTr("Stream"), qsTr("Songs"), qsTr("Playlists"), qsTr("Albums")];
        const saved = JSON.parse(tabsSetting.tabOrder || "[]");
        const names = Array.isArray(saved) && saved.length > 0 ? saved : defaults;
        for (const name of names)
            tabsModel.append({ name: name });
    }

    function saveTabOrder() {
        const names = [];
        for (let i = 0; i < tabsModel.count; ++i)
            names.push(tabsModel.get(i).name);
        tabsSetting.tabOrder = JSON.stringify(names);
    }

    // ---- edge resize (frameless: 4 sisi + 4 pojok) ----
    // startSystemResize sesuai milestone; tinggal tak aktif pas maximized
    Item {
        id: resizeEdges
        anchors.fill: parent
        enabled: root.visibility !== Window.Maximized

        component EdgeResize : MouseArea {
            required property int edgeFlag
            required property int cursorShape2
            cursorShape: cursorShape2
            onPressed: root.startSystemResize(edgeFlag)
        }

        EdgeResize { anchors { top: parent.top; left: parent.left; right: parent.right } height: 6; edgeFlag: Qt.TopEdge; cursorShape2: Qt.SizeVerCursor }
        EdgeResize { anchors { bottom: parent.bottom; left: parent.left; right: parent.right } height: 6; edgeFlag: Qt.BottomEdge; cursorShape2: Qt.SizeVerCursor }
        EdgeResize { anchors { top: parent.top; bottom: parent.bottom; left: parent.left } width: 6; edgeFlag: Qt.LeftEdge; cursorShape2: Qt.SizeHorCursor }
        EdgeResize { anchors { top: parent.top; bottom: parent.bottom; right: parent.right } width: 6; edgeFlag: Qt.RightEdge; cursorShape2: Qt.SizeHorCursor }

        EdgeResize { anchors { top: parent.top; left: parent.left } width: 16; height: 16; edgeFlag: Qt.TopEdge | Qt.LeftEdge; cursorShape2: Qt.SizeFDiagCursor }
        EdgeResize { anchors { bottom: parent.bottom; right: parent.right } width: 16; height: 16; edgeFlag: Qt.BottomEdge | Qt.RightEdge; cursorShape2: Qt.SizeFDiagCursor }
        EdgeResize { anchors { bottom: parent.bottom; left: parent.left } width: 16; height: 16; edgeFlag: Qt.BottomEdge | Qt.LeftEdge; cursorShape2: Qt.SizeBDiagCursor }
        EdgeResize { anchors { top: parent.top; right: parent.right } width: 16; height: 16; edgeFlag: Qt.TopEdge | Qt.RightEdge; cursorShape2: Qt.SizeBDiagCursor }
    }

    // ---- titlebar custom ----
    Rectangle {
        id: titlebar
        anchors {
            top: parent.top
            left: parent.left
            right: parent.right
        }
        height: 40
        color: Theme.surface1
        z: 10

        MouseArea {
            anchors.fill: parent
            onPressed: root.startSystemMove()
            onDoubleClicked: root.toggleMaximize()
        }

        C.Label {
            text: qsTr("IanPlayer")
            color: Theme.textSecondary
            font {
                family: Theme.fontFamily
                pixelSize: Theme.sizeSmall
                weight: Theme.weightSemiBold
            }
            anchors {
                left: parent.left
                leftMargin: 12
                verticalCenter: parent.verticalCenter
            }
        }

        Row {
            anchors {
                top: parent.top
                right: parent.right
                bottom: parent.bottom
            }

            component WindowButton : C.AbstractButton {
                id: wbtn
                property color hoverColor: Theme.surface2
                width: 46
                height: 40
                contentItem: C.Label {
                    text: wbtn.text
                    color: wbtn.hovered ? Theme.textPrimary : Theme.textSecondary
                    horizontalAlignment: Text.AlignHCenter
                    verticalAlignment: Text.AlignVCenter
                    font.pixelSize: 12
                }
                background: Rectangle {
                    color: wbtn.hovered ? wbtn.hoverColor : "transparent"
                }
            }

            WindowButton {
                text: "\u2500"  // \u2500 minimize
                onClicked: root.showMinimized()
            }
            WindowButton {
                text: "\u25A1"  // \u25A1 restore/maximize
                onClicked: root.toggleMaximize()
            }
            WindowButton {
                text: "\u2715"  // \u2715 close
                hoverColor: Theme.destructive
                onClicked: root.close()
            }
        }
    }

    // ---- body: rail + content ----
    RowLayout {
        anchors {
            top: titlebar.bottom
            left: parent.left
            right: parent.right
            bottom: parent.bottom
        }
        spacing: 0

        Rectangle {
            Layout.fillHeight: true
            Layout.preferredWidth: 200
            color: Theme.surface1

            ListView {
                id: tabRail
                anchors {
                    top: parent.top
                    left: parent.left
                    right: parent.right
                    topMargin: 20
                }
                height: contentHeight
                model: tabsModel
                interactive: false
                spacing: 4

                property int activeIndex: 0

                delegate: C.ItemDelegate {
                    id: tabDelegate

                    required property string name
                    required property int index

                    width: tabRail.width
                    height: Theme.controlHeight
                    background: Rectangle {
                        color: tabDelegate.hovered
                               ? Theme.surface2
                               : (tabRail.activeIndex === tabDelegate.index
                                  ? Qt.rgba(0, 0, 0, 0.35) : "transparent")
                        radius: Theme.radius
                    }
                    contentItem: C.Label {
                        text: tabDelegate.name
                        color: tabRail.activeIndex === tabDelegate.index
                               ? Theme.textPrimary
                               : (tabDelegate.hovered ? Theme.textPrimary : Theme.textSecondary)
                        leftPadding: 16
                        verticalAlignment: Text.AlignVCenter
                        font {
                            family: Theme.fontFamily
                            pixelSize: Theme.sizeBody
                            weight: Theme.weightMedium
                        }
                    }

                    MouseArea {
                        anchors.fill: parent
                        cursorShape: Qt.SizeAllCursor
                        onPressed: tabRail.activeIndex = tabDelegate.index
                        onClicked: tabRail.activeIndex = tabDelegate.index
                        // Reorder: pointer lewat 20% atas/bawah bawah item
                        // -> swap satu slot; save ke Settings saat release.
                        onPositionChanged: (mouse) => {
                            const rel = mouse.y / tabDelegate.height;
                            if (rel > 0.8 && tabDelegate.index < tabsModel.count - 1) {
                                tabsModel.move(tabDelegate.index, tabDelegate.index + 1, 1);
                            } else if (rel < 0.2 && tabDelegate.index > 0) {
                                tabsModel.move(tabDelegate.index, tabDelegate.index - 1, 1);
                            }
                        }
                        onReleased: root.saveTabOrder()
                    }

                    Accessible.name: tabDelegate.name
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
                font {
                    family: Theme.fontFamily
                    pixelSize: Theme.sizeSmall
                }
            }
        }

        Rectangle {
            Layout.fillWidth: true
            Layout.fillHeight: true
            color: Theme.bg

            C.Label {
                id: contentTitle
                anchors {
                    top: parent.top
                    left: parent.left
                    margins: 32
                }
                text: tabsModel.count > 0 ? tabsModel.get(tabRail.activeIndex % tabsModel.count).name : ""
                color: Theme.textPrimary
                font {
                    family: Theme.fontFamily
                    pixelSize: Theme.sizeH1
                    weight: Theme.weightSemiBold
                }
            }
            C.Label {
                anchors {
                    top: contentTitle.bottom
                    left: contentTitle.left
                    topMargin: 8
                }
                text: qsTr("Konten menyusul di milestone M1+")
                color: Theme.textSecondary
                font {
                    family: Theme.fontFamily
                    pixelSize: Theme.sizeBody
                }
            }
        }
    }
}
