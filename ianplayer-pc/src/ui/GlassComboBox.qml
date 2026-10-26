import QtQuick
import QtQuick.Controls as C
import QtQuick.Layouts

C.ComboBox {
    id: root

    implicitHeight: Theme.controlHeight
    implicitWidth: 160

    font {
        family: Theme.fontFamily
        pixelSize: Theme.sizeBody
    }

    delegate: C.ItemDelegate {
        id: itemDelegate
        width: root.width
        contentItem: Text {
            text: modelData
            color: itemDelegate.hovered ? Theme.textPrimary : Theme.textSecondary
            font: root.font
            elide: Text.ElideRight
            verticalAlignment: Text.AlignVCenter
        }
        background: Rectangle {
            color: itemDelegate.hovered ? Theme.glassSurfaceHover : "transparent"
            radius: Theme.radius / 2
        }
    }

    indicator: Text {
        x: root.width - width - 12
        y: (root.height - height) / 2
        text: "\u25BE"
        color: Theme.textSecondary
        font.pixelSize: 12
    }

    contentItem: Text {
        leftPadding: 12
        rightPadding: root.indicator.width + 16
        text: root.displayText
        font: root.font
        color: Theme.textPrimary
        verticalAlignment: Text.AlignVCenter
        elide: Text.ElideRight
    }

    background: Rectangle {
        color: root.hovered ? Theme.buttonBgHover : Theme.buttonBg
        border.color: root.activeFocus ? Theme.accent : Theme.borderGlass
        border.width: 1
        radius: Theme.radius

        Behavior on color { ColorAnimation { duration: 150; easing.type: Easing.OutCubic } }
    }

    popup: C.Popup {
        y: root.height + 4
        width: root.width
        implicitHeight: contentItem.implicitHeight + 8
        padding: 4

        contentItem: ListView {
            clip: true
            implicitHeight: contentHeight
            model: root.popup.visible ? root.delegateModel : null
            currentIndex: root.highlightedIndex
            C.ScrollBar.vertical: C.ScrollBar {}
        }

        background: Rectangle {
            color: Theme.osdBg
            border.color: Theme.borderGlassBright
            border.width: 1
            radius: Theme.radius
        }
    }
}
