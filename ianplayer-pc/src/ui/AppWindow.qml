import QtQuick
import QtQuick.Controls as C
import QtQuick.Layouts
import QtQuick.Dialogs
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
        const defaults = [qsTr("Stream"), qsTr("Songs"), qsTr("Playlists"), qsTr("Albums"), qsTr("Artists")];
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
            id: contentArea
            Layout.fillWidth: true
            Layout.fillHeight: true
            color: Theme.bg

            // ---- state navigasi sub-pane (detail screen M1.2) ----
            property string subPane: ""       // "" | "album" | "artist"
            property int detailId: 0
            property string detailTitle: ""
            property string detailArt: ""

            function openAlbum(id, name, art) {
                LibraryProxy.sortMode = 2
                LibraryProxy.albumFilter = id
                LibraryProxy.artistFilter = 0
                subPane = "album"
                detailId = id
                detailTitle = name
                detailArt = art
            }
            function openArtist(id, name, art) {
                LibraryProxy.sortMode = 2
                LibraryProxy.artistFilter = id
                LibraryProxy.albumFilter = 0
                subPane = "artist"
                detailId = id
                detailTitle = name
                detailArt = art
            }
            function closeDetail() {
                subPane = ""
                LibraryProxy.albumFilter = 0
                LibraryProxy.artistFilter = 0
                librarySearch.text = ""
            }

            C.Label {
                id: contentTitle
                anchors {
                    top: parent.top
                    left: parent.left
                    margins: 32
                }
                text: contentArea.subPane !== "" ? contentArea.detailTitle
                    : (tabsModel.count > 0 ? tabsModel.get(tabRail.activeIndex % tabsModel.count).name : "")
                color: Theme.textPrimary
                font {
                    family: Theme.fontFamily
                    pixelSize: Theme.sizeH1
                    weight: Theme.weightSemiBold
                }
            }


            // ==== komponen library M1.2 ====

            // scoped scan controls (dipakai tab Songs)
            component ScanControls : Column {
                spacing: 12

                RowLayout {
                    spacing: 8

                    C.Button {
                        text: qsTr("Scan Folder")
                        onClicked: folderDialog.open()
                    }

                    C.Button {
                        text: qsTr("Rescan")
                        enabled: !LibraryService.scanning
                        onClicked: LibraryService.rescanNow()
                    }
                }

                FolderDialog {
                    id: folderDialog
                    title: qsTr("Pilih folder musik")
                    onAccepted: LibraryService.addRoot(folder)
                }

                C.ProgressBar {
                    Layout.fillWidth: true
                    from: 0
                    to: LibraryService.progressTotal > 0 ? LibraryService.progressTotal : 100
                    value: LibraryService.progressCurrent
                    visible: LibraryService.scanning && LibraryService.progressTotal > 0
                }

                C.Label {
                    text: LibraryService.scanStatusText
                    color: Theme.textSecondary
                    font {
                        family: Theme.fontFamily
                        pixelSize: Theme.sizeSmall
                        weight: Theme.weightMedium
                    }
                    wrapMode: Text.WordWrap
                    Layout.fillWidth: true
                }
            }

            // daftar lagu generik: baris 56px + thumbnails + fast scroller A-Z
            component LibraryList : Item {
                id: libraryListRoot

                function fmtDur(ms) {
                    if (ms <= 0) return ""
                    const total = Math.floor(ms / 1000)
                    return Math.floor(total / 60) + ":" + ("0" + total % 60).slice(-2)
                }

                property bool lettersActive:
                    LibraryProxy.sortMode === 0 && LibraryProxy.count > 0

                ListView {
                    id: songListView
                    anchors {
                        top: parent.top
                        bottom: parent.bottom
                        left: parent.left
                        right: parent.right
                        rightMargin: libraryListRoot.lettersActive ? 34 : 0
                    }
                    clip: true
                    reuseItems: true
                    model: LibraryProxy
                    spacing: 2
                    boundsBehavior: Flickable.StopAtBounds

                    // section A-Z hanya bekerja trik: role sectionLetter selalu
                    // ada; saat sort bukan judul, Qt menonaktifkan section kalau
                    // property kosong.
                    section.property: LibraryProxy.sectionProperty
                    section.labelPositioning: ViewSection.CurrentLabelInline | ViewSection.InlineLabels
                    section.delegate: C.Label {
                        text: section
                        color: Theme.textDisabled
                        font {
                            family: Theme.fontFamily
                            pixelSize: Theme.sizeLabel
                            weight: Theme.weightSemiBold
                        }
                        leftPadding: 8
                        topPadding: 6
                        bottomPadding: 2
                    }

                    C.ScrollBar.vertical: C.ScrollBar {}

                    delegate: C.ItemDelegate {
                        id: rowDelegate

                        required property int index
                        required property int songId
                        required property string path
                        required property string title
                        required property string artist
                        required property string album
                        required property int durationMs
                        required property string art64
                        required property bool favorite
                        required property int playCount

                        width: songListView.width
                        height: 56
                        hoverEnabled: true
                        background: Rectangle {
                            color: rowDelegate.hovered ? Theme.surface2 : "transparent"
                            radius: Theme.radius
                        }

                        RowLayout {
                            id: rowContent
                            anchors.fill: parent
                            anchors.leftMargin: 8
                            anchors.rightMargin: 8
                            spacing: 12

                            Rectangle {
                                Layout.preferredWidth: 48
                                Layout.preferredHeight: 48
                                color: Theme.surface2
                                radius: 6
                                clip: true

                                Text {
                                    visible: rowDelegate.art64 === ""
                                    anchors.centerIn: parent
                                    text: rowDelegate.title.slice(0, 1).toUpperCase()
                                    color: Theme.textSecondary
                                    font {
                                        family: Theme.fontFamily
                                        pixelSize: 22
                                        weight: Theme.weightSemiBold
                                    }
                                }
                                Image {
                                    visible: rowDelegate.art64 !== ""
                                    anchors.fill: parent
                                    source: rowDelegate.art64 === "" ? "" : "file:///" + rowDelegate.art64
                                    fillMode: Image.PreserveAspectCrop
                                    smooth: true
                                }
                            }

                            ColumnLayout {
                                Layout.fillWidth: true
                                spacing: 2

                                C.Label {
                                    Layout.fillWidth: true
                                    text: rowDelegate.title
                                    color: Theme.textPrimary
                                    elide: Text.ElideRight
                                    font {
                                        family: Theme.fontFamily
                                        pixelSize: Theme.sizeBody
                                        weight: Theme.weightMedium
                                    }
                                }
                                C.Label {
                                    Layout.fillWidth: true
                                    text: rowDelegate.artist
                                    color: Theme.textSecondary
                                    elide: Text.ElideRight
                                    font {
                                        family: Theme.fontFamily
                                        pixelSize: Theme.sizeSmall
                                    }
                                }
                            }

                            C.Label {
                                visible: rowDelegate.playCount > 0
                                text: "\u2665\uFE0E " + rowDelegate.playCount
                                color: Theme.textDisabled
                                font.pixelSize: Theme.sizeSmall
                            }

                            C.Label {
                                text: libraryListRoot.fmtDur(rowDelegate.durationMs)
                                color: Theme.textSecondary
                                font {
                                    family: Theme.fontFamily
                                    pixelSize: Theme.sizeSmall
                                }
                            }

                            C.AbstractButton {
                                id: heartBtn
                                width: visible ? 32 : 0
                                height: 32
                                visible: rowDelegate.hovered || rowDelegate.favorite
                                contentItem: Text {
                                    text: rowDelegate.favorite ? "\u2665" : "\u2661"
                                    color: rowDelegate.favorite ? Theme.accent : Theme.textSecondary
                                    horizontalAlignment: Text.AlignHCenter
                                    verticalAlignment: Text.AlignVCenter
                                    font.pixelSize: 16
                                }
                                onClicked: LibraryService.toggleFavorite("local:" + rowDelegate.songId)
                            }
                        }

                        // rekam play event — hook konsumen M2 plays start
                        TapHandler {
                            acceptedButtons: Qt.AllButtons
                            onSingleTapped: (eventPoint, button) => {
                                if (button === Qt.LeftButton)
                                    LibraryService.recordLocalPlayed(rowDelegate.path)
                                else if (button === Qt.RightButton)
                                    rowMenu.popup()
                            }
                        }

                        C.Menu {
                            id: rowMenu
                            C.MenuItem {
                                text: rowDelegate.favorite
                                      ? qsTr("Hapus dari Favorit")
                                      : qsTr("Tambah ke Favorit")
                                onClicked:
                                    LibraryService.toggleFavorite("local:" + rowDelegate.songId)
                            }
                            C.MenuItem {
                                text: qsTr("Tampilkan di Explorer")
                                onClicked: LibraryService.revealLocalFile(rowDelegate.path)
                            }
                            C.MenuItem {
                                text: qsTr("Tambah ke Playlist")
                                enabled: false   // M1.3
                            }
                        }
                    }
                }

                // ---- fast scroller A–Z (paritas Android; hanya sort judul) ----
                Column {
                    visible: libraryListRoot.lettersActive
                    anchors {
                        top: parent.top
                        bottom: parent.bottom
                        right: parent.right
                        topMargin: 8
                    }
                    spacing: 2

                    Repeater {
                        model: ["A","B","C","D","E","F","G","H","I","J","K","L","M",
                                "N","O","P","Q","R","S","T","U","V","W","X","Y","Z","#"]

                        delegate: C.AbstractButton {
                            width: 26
                            height: 16
                            contentItem: Text {
                                text: modelData
                                color: Theme.textDisabled
                                horizontalAlignment: Text.AlignHCenter
                                font {
                                    family: Theme.fontFamily
                                    pixelSize: 10
                                    weight: Theme.weightMedium
                                }
                            }
                            background: null
                            onClicked: {
                                const idx = LibraryProxy.indexForLetter(modelData.charCodeAt(0))
                                if (idx >= 0)
                                    songListView.positionViewAtIndex(idx, ListView.Beginning)
                            }
                        }
                    }
                }
            }

            // == Songs pane: scan controls + search + sort + list ==
            ColumnLayout {
                id: songsPaneColumn
                visible: contentArea.subPane === ""
                    && tabsModel.count > 0
                    && tabsModel.get(tabRail.activeIndex % tabsModel.count).name === qsTr("Songs")
                anchors {
                    top: contentTitle.bottom
                    left: contentTitle.left
                    right: parent.right
                    rightMargin: 24
                    bottom: parent.bottom
                    topMargin: 16
                }
                spacing: 12

                ScanControls { Layout.fillWidth: true }

                RowLayout {
                    Layout.fillWidth: true
                    spacing: 8

                    C.TextField {
                        id: librarySearch
                        Layout.preferredWidth: 280
                        placeholderText: qsTr("Cari judul/artis/album...")
                        selectByMouse: true
                        font.family: Theme.fontFamily
                        Timer {
                            id: searchDebounce
                            interval: 250
                            onTriggered: LibraryProxy.searchText = librarySearch.text
                        }
                        onTextChanged: searchDebounce.restart()
                    }

                    Item { Layout.fillWidth: true }

                    C.Label {
                        text: qsTr("%1 lagu").arg(LibraryProxy.count)
                        color: Theme.textDisabled
                        font.pixelSize: Theme.sizeSmall
                    }

                    C.ComboBox {
                        model: [qsTr("Judul"),
                                qsTr("Artis"),
                                qsTr("Album"),
                                qsTr("Baru ditambahkan"),
                                qsTr("Terakhir diputar")]
                        currentIndex: LibraryProxy.sortMode
                        onActivated: (idx) => LibraryProxy.sortMode = idx
                        font.family: Theme.fontFamily
                    }
                }

                C.Label {
                    Layout.fillWidth: true
                    visible: LibraryProxy.count === 0 && LibraryService.songCount > 0
                    text: qsTr("Tidak ada lagu yang cocok dengan pencarian/mode sort.")
                    color: Theme.textSecondary
                    font.family: Theme.fontFamily
                }

                C.Label {
                    Layout.fillWidth: true
                    visible: LibraryService.songCount === 0
                    text: qsTr("Library kosong. Scan folder musik dulu.")
                    color: Theme.textSecondary
                    font.family: Theme.fontFamily
                }

                LibraryList {
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                }
            }

            // == placeholder Stream (M3) & Playlists (M1.3) ==
            PanePlaceholder {
                visible: contentArea.subPane === ""
                    && tabsModel.count > 0
                    && ["Stream", "Playlists"].indexOf(
                        tabsModel.get(tabRail.activeIndex % tabsModel.count).name) >= 0
            }

            // == Albums pane: grid kotak (paritas Android) ==
            Item {
                id: albumsPane
                visible: contentArea.subPane === ""
                    && tabsModel.count > 0
                    && tabsModel.get(tabRail.activeIndex % tabsModel.count).name === qsTr("Albums")
                anchors {
                    top: contentTitle.bottom
                    left: contentTitle.left
                    right: parent.right
                    rightMargin: 24
                    bottom: parent.bottom
                    topMargin: 16
                }

                C.Label {
                    visible: AlbumListModel.count === 0
                    text: qsTr("Belum ada album. Scan folder musik dulu.")
                    color: Theme.textSecondary
                    font.family: Theme.fontFamily
                    anchors.top: parent.top
                }

                GridView {
                    visible: AlbumListModel.count > 0
                    anchors.fill: parent
                    clip: true
                    cellWidth: 160
                    cellHeight: 200
                    model: AlbumListModel
                    reuseItems: true

                    C.ScrollBar.vertical: C.ScrollBar {}

                    delegate: C.ItemDelegate {
                        id: albumDelegate

                        required property int index
                        required property int albumId
                        required property string albumName
                        required property string albumArtist
                        required property string albumArt
                        required property int albumSongCount

                        width: 160
                        height: 200
                        hoverEnabled: true
                        background: Rectangle {
                            color: albumDelegate.hovered ? Theme.surface2 : "transparent"
                            radius: Theme.radius
                        }
                        contentItem: Column {
                            id: albumCell
                            spacing: 4

                            Rectangle {
                                width: 144
                                height: 144
                                anchors.horizontalCenter: parent.horizontalCenter
                                color: Theme.surface2
                                radius: Theme.radius
                                clip: true

                                Text {
                                    visible: albumDelegate.albumArt === ""
                                    anchors.centerIn: parent
                                    text: albumDelegate.albumName.slice(0, 1).toUpperCase()
                                    color: Theme.textSecondary
                                    font {
                                        family: Theme.fontFamily
                                        pixelSize: 44
                                        weight: Theme.weightBold
                                    }
                                }
                                Image {
                                    visible: albumDelegate.albumArt !== ""
                                    anchors.fill: parent
                                    source: visible ? "file:///" + albumDelegate.albumArt : ""
                                    fillMode: Image.PreserveAspectCrop
                                    smooth: true
                                }
                            }

                            C.Label {
                                width: 144
                                anchors.horizontalCenter: parent.horizontalCenter
                                text: albumDelegate.albumName
                                color: Theme.textPrimary
                                elide: Text.ElideRight
                                font {
                                    family: Theme.fontFamily
                                    pixelSize: Theme.sizeSmall
                                    weight: Theme.weightMedium
                                }
                            }
                            C.Label {
                                width: 144
                                anchors.horizontalCenter: parent.horizontalCenter
                                text: albumDelegate.albumArtist + " \u00B7 "
                                      + albumDelegate.albumSongCount + " lagu"
                                color: Theme.textSecondary
                                elide: Text.ElideRight
                                font {
                                    family: Theme.fontFamily
                                    pixelSize: 11
                                }
                            }
                        }

                        onClicked:
                            contentArea.openAlbum(
                                albumDelegate.albumId,
                                albumDelegate.albumName,
                                albumDelegate.albumArt)
                    }
                }
            }

            // == Artists pane: grid kotak ==
            Item {
                id: artistsPane
                visible: contentArea.subPane === ""
                    && tabsModel.count > 0
                    && tabsModel.get(tabRail.activeIndex % tabsModel.count).name === qsTr("Artists")
                anchors {
                    top: contentTitle.bottom
                    left: contentTitle.left
                    right: parent.right
                    rightMargin: 24
                    bottom: parent.bottom
                    topMargin: 16
                }

                C.Label {
                    visible: ArtistListModel.count === 0
                    text: qsTr("Belum ada artis. Scan folder musik dulu.")
                    color: Theme.textSecondary
                    font.family: Theme.fontFamily
                    anchors.top: parent.top
                }

                GridView {
                    visible: ArtistListModel.count > 0
                    anchors.fill: parent
                    clip: true
                    cellWidth: 160
                    cellHeight: 200
                    model: ArtistListModel
                    reuseItems: true

                    C.ScrollBar.vertical: C.ScrollBar {}

                    delegate: C.ItemDelegate {
                        id: artistDelegate

                        required property int index
                        required property int artistId
                        required property string artistName
                        required property string artistArt
                        required property int artistSongCount

                        width: 160
                        height: 200
                        hoverEnabled: true
                        background: Rectangle {
                            color: artistDelegate.hovered ? Theme.surface2 : "transparent"
                            radius: Theme.radius
                        }
                        contentItem: Column {
                            id: artistCell
                            spacing: 4

                            Rectangle {
                                width: 144
                                height: 144
                                anchors.horizontalCenter: parent.horizontalCenter
                                radius: 72
                                color: Theme.surface2
                                clip: true

                                Text {
                                    visible: artistDelegate.artistArt === ""
                                    anchors.centerIn: parent
                                    text: artistDelegate.artistName.slice(0, 1).toUpperCase()
                                    color: Theme.textSecondary
                                    font {
                                        family: Theme.fontFamily
                                        pixelSize: 44
                                        weight: Theme.weightBold
                                    }
                                }
                                Image {
                                    visible: artistDelegate.artistArt !== ""
                                    anchors.fill: parent
                                    source: visible ? "file:///" + artistDelegate.artistArt : ""
                                    fillMode: Image.PreserveAspectCrop
                                    smooth: true
                                }
                            }

                            C.Label {
                                width: 144
                                anchors.horizontalCenter: parent.horizontalCenter
                                text: artistDelegate.artistName
                                color: Theme.textPrimary
                                elide: Text.ElideRight
                                horizontalAlignment: Text.AlignHCenter
                                font {
                                    family: Theme.fontFamily
                                    pixelSize: Theme.sizeSmall
                                    weight: Theme.weightMedium
                                }
                            }
                            C.Label {
                                width: 144
                                anchors.horizontalCenter: parent.horizontalCenter
                                text: artistDelegate.artistSongCount + " lagu"
                                color: Theme.textSecondary
                                elide: Text.ElideRight
                                horizontalAlignment: Text.AlignHCenter
                                font {
                                    family: Theme.fontFamily
                                    pixelSize: 11
                                }
                            }
                        }

                        onClicked:
                            contentArea.openArtist(
                                artistDelegate.artistId,
                                artistDelegate.artistName,
                                artistDelegate.artistArt)
                    }
                }
            }

            // == detail screen: daftar lagu album/artis ==
            ColumnLayout {
                id: detailPane
                visible: contentArea.subPane !== ""
                anchors {
                    top: contentTitle.bottom
                    left: contentTitle.left
                    right: parent.right
                    rightMargin: 24
                    bottom: parent.bottom
                    topMargin: 16
                }
                spacing: 12

                RowLayout {
                    spacing: 8

                    C.Button {
                        text: qsTr("\u2190 Kembali")
                        onClicked: contentArea.closeDetail()
                        font.family: Theme.fontFamily
                    }

                    C.Label {
                        text: contentArea.subPane === "album"
                              ? qsTr("Album \u00B7 %1").arg(contentArea.detailTitle)
                              : qsTr("Artis \u00B7 %1").arg(contentArea.detailTitle)
                        color: Theme.textSecondary
                        font {
                            family: Theme.fontFamily
                            pixelSize: Theme.sizeSmall
                            weight: Theme.weightMedium
                        }
                    }
                }

                LibraryList {
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                }
            }

            // == placeholder tab yang belum aktif ==
            component PanePlaceholder : C.Label {
                anchors {
                    top: contentTitle.bottom
                    left: contentTitle.left
                    topMargin: 16
                    leftMargin: 32
                }
                text: qsTr("Konten menyusul di milestone M1.3+ / M3")
                color: Theme.textSecondary
                font {
                    family: Theme.fontFamily
                    pixelSize: Theme.sizeBody
                }
            }

            PanePlaceholder {
                visible: contentArea.subPane === ""
                    && tabsModel.count > 0
                    && ["Stream", "Playlists"].indexOf(
                        tabsModel.get(tabRail.activeIndex % tabsModel.count).name) >= 0
            }
        }
    }
}
