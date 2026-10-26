import QtQuick
import QtQuick.Controls as C
import IanPlayer.PC 1.0

Item {
    id: root
    property real progress: 0.0 // 0.0 to 1.0
    property int styleIndex: SettingsService.seekBarStyle // 0: Classic, 1: Wave, 2: Squiggle, 3: Minimal
    property color accentColor: Theme.accent
    property color backgroundColor: Theme.surface2
    property bool interactive: true

    signal seekRequested(real fraction)

    implicitHeight: styleIndex === 3 ? 6 : (styleIndex === 1 ? 16 : 10)
    implicitWidth: 300

    MouseArea {
        id: mouseArea
        anchors.fill: parent
        enabled: root.interactive
        cursorShape: Qt.PointingHandCursor
        onClicked: (mouse) => {
            const frac = Math.max(0.0, Math.min(1.0, mouse.x / width))
            root.seekRequested(frac)
        }
        onPositionChanged: (mouse) => {
            if (pressed) {
                const frac = Math.max(0.0, Math.min(1.0, mouse.x / width))
                root.seekRequested(frac)
            }
        }
    }

    // Mode 0: Classic Bar
    Rectangle {
        anchors.centerIn: parent
        width: parent.width
        height: 6
        radius: 3
        color: root.backgroundColor
        visible: root.styleIndex === 0

        Rectangle {
            anchors { left: parent.left; top: parent.top; bottom: parent.bottom }
            width: parent.width * Math.max(0, Math.min(1, root.progress))
            radius: 3
            color: root.accentColor
        }
    }

    // Mode 1: Wave Bar (Visualizer-style vertical bars)
    Canvas {
        id: waveCanvas
        anchors.fill: parent
        visible: root.styleIndex === 1
        onPaint: {
            const ctx = getContext("2d")
            ctx.clearRect(0, 0, width, height)
            const numBars = 40
            const barWidth = (width / numBars) - 2
            const currentX = width * Math.max(0, Math.min(1, root.progress))

            for (let i = 0; i < numBars; i++) {
                const x = i * (barWidth + 2)
                // Deterministic pseudo-random height pattern based on index
                const hFactor = 0.3 + 0.7 * Math.abs(Math.sin((i + 1) * 12.9898))
                const barHeight = Math.max(4, height * hFactor)
                const y = (height - barHeight) / 2

                ctx.fillStyle = (x <= currentX) ? root.accentColor.toString() : root.backgroundColor.toString()
                ctx.beginPath()
                ctx.roundedRect(x, y, barWidth, barHeight, 2, 2)
                ctx.fill()
            }
        }
        onWidthChanged: requestPaint()
        onHeightChanged: requestPaint()
        Connections {
            target: root
            function onProgressChanged() { waveCanvas.requestPaint() }
            function onStyleIndexChanged() { waveCanvas.requestPaint() }
        }
    }

    // Mode 2: Animated Squiggle Line
    Canvas {
        id: squiggleCanvas
        anchors.fill: parent
        visible: root.styleIndex === 2
        property real phase: 0

        Timer {
            running: root.styleIndex === 2 && QueueManager.isPlaying
            repeat: true
            interval: 50
            onTriggered: {
                squiggleCanvas.phase += 0.2
                squiggleCanvas.requestPaint()
            }
        }

        onPaint: {
            const ctx = getContext("2d")
            ctx.clearRect(0, 0, width, height)
            const currentX = width * Math.max(0, Math.min(1, root.progress))

            // Draw Background Flat Line
            ctx.strokeStyle = root.backgroundColor.toString()
            ctx.lineWidth = 3
            ctx.beginPath()
            ctx.moveTo(currentX, height / 2)
            ctx.lineTo(width, height / 2)
            ctx.stroke()

            // Draw Active Squiggle Line up to progress
            if (currentX > 0) {
                ctx.strokeStyle = root.accentColor.toString()
                ctx.lineWidth = 3
                ctx.beginPath()
                const cy = height / 2
                const wavelength = 24
                const amplitude = 3

                for (let x = 0; x <= currentX; x += 2) {
                    const y = cy + Math.sin((x / wavelength) * 2 * Math.PI + squiggleCanvas.phase) * amplitude
                    if (x === 0) ctx.moveTo(x, y)
                    else ctx.lineTo(x, y)
                }
                ctx.stroke()
            }
        }
        onWidthChanged: requestPaint()
        onHeightChanged: requestPaint()
        Connections {
            target: root
            function onProgressChanged() { squiggleCanvas.requestPaint() }
            function onStyleIndexChanged() { squiggleCanvas.requestPaint() }
        }
    }

    // Mode 3: Minimal Line with Handle Dot
    Rectangle {
        anchors.centerIn: parent
        width: parent.width
        height: 2
        color: root.backgroundColor
        visible: root.styleIndex === 3

        Rectangle {
            anchors { left: parent.left; top: parent.top; bottom: parent.bottom }
            width: parent.width * Math.max(0, Math.min(1, root.progress))
            color: root.accentColor
        }

        Rectangle {
            x: Math.max(0, Math.min(parent.width - 10, parent.width * Math.max(0, Math.min(1, root.progress)) - 5))
            anchors.centerY: parent.centerY
            width: 10
            height: 10
            radius: 5
            color: root.accentColor
        }
    }
}
