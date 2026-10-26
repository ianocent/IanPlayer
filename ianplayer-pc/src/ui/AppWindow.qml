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

    property bool nowPlayingOpen: false
    property bool shortcutsOpen: false
    property bool recapOpen: false

    Timer {
        id: positionPoll
        interval: 250
        running: QueueManager.queueCount > 0
        repeat: true
        onTriggered: { /* progress bound via QueueManager.positionMs() in mini/np */ }
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
                text: "\u2699"  // ⚙ settings
                onClicked: settingsDialog.open()
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
            bottom: miniPlayer.top
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

            PlayerCanvas {
                anchors.fill: parent
                z: -1
            }

            // ---- state navigasi sub-pane (detail screen M1.2) ----
            property string subPane: ""       // "" | "album" | "artist" | "playlist"
            property int detailId: 0
            property string detailTitle: ""
            property string detailArt: ""
            property int detailPlaylistKey: 0
            property int chooserSongKey: 0    // lagu sementara versi chooser menu

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
            function openPlaylist(key, name, image) {
                PlaylistSongModel.playlistKey = key
                subPane = "playlist"
                detailPlaylistKey = key
                detailId = key
                detailTitle = name
                detailArt = image
            }
            function closeDetail() {
                subPane = ""
                LibraryProxy.albumFilter = 0
                LibraryProxy.artistFilter = 0
                LibraryProxy.searchText = ""
                PlaylistSongModel.playlistKey = 0
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

                    GlassButton {
                        text: qsTr("Scan Folder")
                        onClicked: folderDialog.open()
                    }

                    GlassButton {
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

            // daftar lagu generik: baris 56px + thumbnails + fast scroller A-Z.
            // listModel/sectionProp bisa ditimpa (isian playlist detail pakai
            // PlaylistSongModel); reorderable nuju aktif grip drag.
            component LibraryList : Item {
                id: libraryListRoot

                property var listModel: LibraryProxy
                property string sectionProp: LibraryProxy.sectionProperty
                property bool showLetters:
                    LibraryProxy.sortMode === 0 && LibraryProxy.count > 0 && LibraryProxy.count > 0
                property bool reorderable: false

                function fmtDur(ms) {
                    if (ms <= 0) return ""
                    const total = Math.floor(ms / 1000)
                    return Math.floor(total / 60) + ":" + ("0" + total % 60).slice(-2)
                }

                // dipakai grip drag-reorder — hanya PlaylistSongModel saja yang
                // punya feature ini
                function moveSongJs(songId, toPos) {
                    return PlaylistSongModel.moveSong(songId, toPos)
                }

                ListView {
                    id: songListView
                    anchors {
                        top: parent.top
                        bottom: parent.bottom
                        left: parent.left
                        right: parent.right
                        rightMargin: libraryListRoot.showLetters
                                     && !libraryListRoot.reorderable ? 34 : 0
                    }
                    clip: true
                    reuseItems: true
                    model: libraryListRoot.listModel
                    spacing: 2
                    boundsBehavior: Flickable.StopAtBounds

                    // section A-Z hanya bekerja trik: role sectionLetter selalu
                    // ada; saat sort bukan judul, Qt menonaktifkan section kalau
                    // property kosong.
                    section.property: libraryListRoot.sectionProp
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
                                if (button === Qt.LeftButton
                                    && !libraryListRoot.reorderable)
                                    LibraryService.recordLocalPlayed(rowDelegate.path)
                                else if (button === Qt.RightButton)
                                    rowMenu.popup()
                            }
                        }

                        // grip drag-reorder (playlist detail): seret → posisi
                        // target dihitung dari posisi pointer; model di C++ yang
                        // melanjutkan (beginMoveRows jadi animasi halus)
                        MouseArea {
                            id: reorderDrag
                            visible: libraryListRoot.reorderable
                            enabled: libraryListRoot.reorderable
                            anchors {
                                left: parent.left
                                top: parent.top
                                bottom: parent.bottom
                            }
                            width: 20
                            cursorShape: Qt.SizeAllCursor
                            property int dragPos: -1   // posisi aktif terakhir

                            onPressed: (mouse) => {
                                dragPos = rowDelegate.index
                                mouse.accepted = true
                            }
                            onPositionChanged: (mouse) => {
                                if (!pressed || songListView.count === 0)
                                    return
                                const inList = mapToItem(songListView, 0, mouse.y)
                                const rowH = rowDelegate.height + songListView.spacing
                                let target = Math.floor(inList.y / rowH)
                                target = Math.max(0, Math.min(target, songListView.count - 1))
                                if (target !== dragPos) {
                                    if (libraryListRoot.moveSongJs(rowDelegate.songId, target))
                                        dragPos = target
                                }
                                // autoscroll kalau di pinggir viewport
                                const speed = 12
                                if (inList.y < 12)
                                    songListView.contentY = Math.max(0, songListView.contentY - speed)
                                else if (inList.y > songListView.height - 12)
                                    songListView.contentY = Math.min(
                                        songListView.contentHeight - songListView.height,
                                        songListView.contentY + speed)
                            }
                            onReleased: dragPos = -1
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
                                onClicked: {
                                    chooserSongKey = rowDelegate.songId
                                    addPlaylistMenu.popup()
                                }
                            }
                        }
                    }
                }

                // ---- fast scroller A–Z (paritas Android; hanya sort judul) ----
                Column {
                    visible: libraryListRoot.showLetters && !libraryListRoot.reorderable
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

                    GlassTextField {
                        id: librarySearch
                        Layout.preferredWidth: 280
                        placeholderText: qsTr("Cari judul/artis/album...")
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

                    GlassComboBox {
                        model: [qsTr("Judul"),
                                qsTr("Artis"),
                                qsTr("Album"),
                                qsTr("Baru ditambahkan"),
                                qsTr("Terakhir diputar")]
                        currentIndex: LibraryProxy.sortMode
                        onActivated: (idx) => LibraryProxy.sortMode = idx
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
                visible: contentArea.subPane === "album"
                    || contentArea.subPane === "artist"
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

                    GlassButton {
                        text: qsTr("\u2190 Kembali")
                        onClicked: contentArea.closeDetail()
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

            // == Playlists pane (M1.3): daftar kartu + CRUD ==
            Item {
                id: playlistsPane
                visible: contentArea.subPane === ""
                    && tabsModel.count > 0
                    && tabsModel.get(tabRail.activeIndex % tabsModel.count).name === qsTr("Playlists")
                anchors {
                    top: contentTitle.bottom
                    left: contentTitle.left
                    right: parent.right
                    rightMargin: 24
                    bottom: parent.bottom
                    topMargin: 16
                }

                RowLayout {
                    id: playlistTools
                    anchors {
                        top: parent.top
                        left: parent.left
                        right: parent.right
                    }
                    spacing: 8

                    GlassButton {
                        text: qsTr("Playlist Baru")
                        primary: true
                        onClicked: {
                            playlistNameDialog.editKey = 0
                            playlistNameDialog.name = ""
                            playlistNameDialog.open()
                        }
                    }

                    C.Label {
                        text: qsTr("%1 playlist").arg(PlaylistListModel.count)
                        color: Theme.textDisabled
                        font {
                            family: Theme.fontFamily
                            pixelSize: Theme.sizeSmall
                        }
                    }
                }

                ListView {
                    visible: PlaylistListModel.count > 0
                    anchors {
                        top: playlistTools.bottom
                        left: parent.left
                        right: parent.right
                        bottom: parent.bottom
                        topMargin: 12
                    }
                    clip: true
                    reuseItems: true
                    model: PlaylistListModel
                    spacing: 8
                    boundsBehavior: Flickable.StopAtBounds

                    C.ScrollBar.vertical: C.ScrollBar {}

                    delegate: C.ItemDelegate {
                        id: playlistRow

                        required property int index
                        required property int playlistKey
                        required property string playlistName
                        required property string playlistImage
                        required property int playlistSongCount

                        width: playlistsPane.width
                        height: 72
                        hoverEnabled: true
                        background: Rectangle {
                            color: playlistRow.hovered ? Theme.surface2 : "transparent"
                            radius: Theme.radius
                        }

                        RowLayout {
                            anchors.fill: parent
                            spacing: 12

                            Rectangle {
                                Layout.preferredWidth: 56
                                Layout.preferredHeight: 56
                                color: Theme.surface2
                                radius: Theme.radius
                                clip: true

                                C.Label {
                                    visible: playlistRow.playlistImage === ""
                                    anchors.centerIn: parent
                                    text: "\u2661"
                                    color: Theme.textSecondary
                                    font.pixelSize: 22
                                }
                                Image {
                                    visible: playlistRow.playlistImage !== ""
                                    anchors.fill: parent
                                    source: playlistRow.playlistImage
                                    fillMode: Image.PreserveAspectCrop
                                    smooth: true
                                }
                            }

                            ColumnLayout {
                                Layout.fillWidth: true
                                spacing: 2

                                C.Label {
                                    Layout.fillWidth: true
                                    text: playlistRow.playlistName
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
                                    text: qsTr("%1 lagu").arg(playlistRow.playlistSongCount)
                                    color: Theme.textSecondary
                                    font {
                                        family: Theme.fontFamily
                                        pixelSize: Theme.sizeSmall
                                    }
                                }
                            }

                            Item { Layout.fillWidth: true }

                            C.AbstractButton {
                                Layout.preferredWidth: 32
                                Layout.preferredHeight: 32
                                contentItem: Text {
                                    text: "\u22EF"
                                    color: Theme.textSecondary
                                    horizontalAlignment: Text.AlignHCenter
                                    verticalAlignment: Text.AlignVCenter
                                    font.pixelSize: 16
                                }
                                onClicked: playlistRowMenu.popup()
                            }
                        }

                        TapHandler {
                            acceptedButtons: Qt.LeftButton
                            onSingleTapped:
                                contentArea.openPlaylist(
                                    playlistRow.playlistKey,
                                    playlistRow.playlistName,
                                    playlistRow.playlistImage)
                        }

                        C.Menu {
                            id: playlistRowMenu
                            C.MenuItem {
                                text: qsTr("Buka")
                                onClicked: contentArea.openPlaylist(
                                    playlistRow.playlistKey,
                                    playlistRow.playlistName,
                                    playlistRow.playlistImage)
                            }
                            C.MenuItem {
                                text: qsTr("Ganti Nama")
                                onClicked: {
                                    playlistNameDialog.editKey = playlistRow.playlistKey
                                    playlistNameDialog.name = playlistRow.playlistName
                                    playlistNameDialog.open()
                                }
                            }
                            C.MenuItem {
                                text: qsTr("Ganti Sampul")
                                onClicked: {
                                    playlistImageDialog.dialogPlaylistKey
                                        = playlistRow.playlistKey
                                    playlistImageDialog.open()
                                }
                            }
                            C.MenuItem {
                                text: qsTr("Hapus Playlist")
                                onClicked: LibraryService.deletePlaylist(
                                    playlistRow.playlistKey)
                            }
                        }
                    }
                }

                C.Label {
                    visible: PlaylistListModel.count === 0
                    anchors.centerIn: parent
                    text: qsTr("Belum ada playlist. Klik \"Playlist Baru\" atau tambahkan lagu lewat menu klik-kanan di tab Songs.")
                    color: Theme.textSecondary
                    font.family: Theme.fontFamily
                    wrapMode: Text.WordWrap
                    horizontalAlignment: Text.AlignHCenter
                    width: 340
                }
            }

            // == playlist detail: header + daftar lagu + drag reorder ==
            ColumnLayout {
                id: playlistDetailPane
                visible: contentArea.subPane === "playlist"
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

                    GlassButton {
                        text: qsTr("\u2190 Kembali")
                        onClicked: contentArea.closeDetail()
                    }

                    C.Label {
                        text: qsTr("Playlist \u00B7 %1").arg(contentArea.detailTitle)
                        color: Theme.textSecondary
                        font {
                            family: Theme.fontFamily
                            pixelSize: Theme.sizeSmall
                            weight: Theme.weightMedium
                        }
                    }

                    Item { Layout.fillWidth: true }

                    C.AbstractButton {
                        Layout.preferredWidth: 32
                        Layout.preferredHeight: 32
                        contentItem: Text {
                            text: "\u22EF"
                            color: Theme.textSecondary
                            horizontalAlignment: Text.AlignHCenter
                            verticalAlignment: Text.AlignVCenter
                            font.pixelSize: 16
                        }
                        onClicked: playlistDetailMenu.popup()
                    }
                }

                C.Label {
                    Layout.fillWidth: true
                    visible: PlaylistSongModel.count === 0
                    text: qsTr("Playlist kosong. Tambahkan lagu lewat menu klik-kanan di tab Songs.")
                    color: Theme.textSecondary
                    font.family: Theme.fontFamily
                }

                LibraryList {
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    listModel: PlaylistSongModel
                    sectionProp: ""
                    reorderable: true
                }
            }

            // == menu kelola playlist di halaman detail ==
            C.Menu {
                id: playlistDetailMenu
                C.MenuItem {
                    text: qsTr("Ganti Nama")
                    onClicked: {
                        playlistNameDialog.editKey = contentArea.detailPlaylistKey
                        playlistNameDialog.name = contentArea.detailTitle
                        playlistNameDialog.open()
                    }
                }
                C.MenuItem {
                    text: qsTr("Ganti Sampul")
                    onClicked: {
                        playlistImageDialog.dialogPlaylistKey = contentArea.detailPlaylistKey
                        playlistImageDialog.open()
                    }
                }
                C.MenuItem {
                    text: qsTr("Hapus Playlist")
                    onClicked: {
                        contentArea.closeDetail()
                        LibraryService.deletePlaylist(contentArea.detailPlaylistKey)
                    }
                }
            }

            // == chooser "Tambah ke Playlist" (dari baris songs) ==
            C.Menu {
                id: addPlaylistMenu
                title: qsTr("Tambah ke Playlist")
                Repeater {
                    model: PlaylistListModel
                    delegate: C.MenuItem {
                        required property int playlistKey
                        required property string playlistName
                        text: playlistName
                        onClicked: LibraryService.addSongToPlaylist(
                            playlistKey, "local:" + contentArea.chooserSongKey)
                    }
                }
                C.MenuSeparator {}
                C.MenuItem {
                    text: qsTr("Playlist baru...")
                    onClicked: {
                        playlistNameDialog.editKey = 0
                        playlistNameDialog.name = ""
                        playlistNameDialog.open()
                    }
                }
            }

            // == dialog buat/rename nama playlist ==
            C.Dialog {
                id: playlistNameDialog
                property int editKey: 0
                property string name: ""
                x: (parent.width - width) / 2
                y: (parent.height - height) / 2
                parent: contentArea
                title: editKey === 0 ? qsTr("Playlist Baru") : qsTr("Ganti Nama Playlist")
                standardButtons: C.Dialog.Ok | C.Dialog.Cancel
                modal: true

                onAccepted: {
                    const n = playlistNameField.text.trim()
                    if (n === "")
                        return
                    if (editKey === 0) {
                        const newId = LibraryService.createPlaylist(n)
                        if (newId > 0 && contentArea.chooserSongKey > 0) {
                            LibraryService.addSongToPlaylist(
                                newId, "local:" + contentArea.chooserSongKey)
                            contentArea.chooserSongKey = 0
                        }
                    } else {
                        LibraryService.renamePlaylist(editKey, n)
                    }
                }

                C.TextField {
                    id: playlistNameField
                    text: playlistNameDialog.name
                    placeholderText: qsTr("Nama playlist")
                    Layout.preferredWidth: 280
                    font.family: Theme.fontFamily
                }
            }

            // == dialog pilih sampul playlist ==
            FileDialog {
                id: playlistImageDialog
                property int dialogPlaylistKey: 0
                title: qsTr("Pilih gambar sampul playlist")
                nameFilters: [qsTr("Gambar (*.png *.jpg *.jpeg *.webp)")]
                onAccepted: LibraryService.setPlaylistImage(
                    dialogPlaylistKey, playlistImageDialog.selectedFile.toString())
            }

            // == Stream pane (M3.4) ==
            ColumnLayout {
                id: streamPane
                visible: contentArea.subPane === ""
                    && tabsModel.count > 0
                    && tabsModel.get(tabRail.activeIndex % tabsModel.count).name === qsTr("Stream")
                anchors {
                    top: contentTitle.bottom
                    left: contentTitle.left
                    right: parent.right
                    rightMargin: 24
                    bottom: parent.bottom
                    topMargin: 16
                }
                spacing: 8

                RowLayout {
                    Layout.fillWidth: true
                    spacing: 8

                    GlassTextField {
                        id: streamSearchField
                        Layout.fillWidth: true
                        placeholderText: qsTr("Cari di YouTube Music / Tidal...")
                        onTextChanged: streamSearchDebounce.restart()
                    }

                    GlassButton {
                        text: qsTr("Browse")
                        onClicked: StreamService.browseHome()
                    }
                }

                Timer {
                    id: streamSearchDebounce
                    interval: 300
                    onTriggered: {
                        if (streamSearchField.text.trim() !== "")
                            StreamService.search(streamSearchField.text.trim())
                    }
                }

                ListView {
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    clip: true
                    reuseItems: true
                    model: streamSearchField.text.trim() !== ""
                        ? StreamSearchModel : StreamBrowseModel
                    spacing: 2
                    boundsBehavior: Flickable.StopAtBounds
                    C.ScrollBar.vertical: C.ScrollBar {}

                    delegate: C.ItemDelegate {
                        id: streamRow
                        required property string title
                        required property string artist
                        required property string thumbnailUrl
                        required property string videoId
                        required property string source

                        width: streamPane.width
                        height: 56
                        background: Rectangle { color: streamRow.hovered ? Theme.surface2 : "transparent"; radius: Theme.radius }

                        RowLayout {
                            anchors.fill: parent
                            spacing: 12

                            Rectangle {
                                Layout.preferredWidth: 48; Layout.preferredHeight: 48
                                color: Theme.surface2; radius: Theme.radius; clip: true
                                Image { anchors.fill: parent; source: streamRow.thumbnailUrl; fillMode: Image.PreserveAspectCrop; smooth: true }
                            }

                            ColumnLayout {
                                Layout.fillWidth: true; spacing: 2
                                C.Label { Layout.fillWidth: true; text: streamRow.title; color: Theme.textPrimary; elide: Text.ElideRight; font { family: Theme.fontFamily; pixelSize: Theme.sizeBody; weight: Theme.weightMedium } }
                                C.Label { Layout.fillWidth: true; text: streamRow.artist + " \u00B7 " + streamRow.source; color: Theme.textSecondary; elide: Text.ElideRight; font { family: Theme.fontFamily; pixelSize: Theme.sizeSmall } }
                            }

                            C.AbstractButton {
                                width: 32; height: 32
                                contentItem: Text { text: "\u25B6"; color: Theme.textPrimary; horizontalAlignment: Text.AlignHCenter; verticalAlignment: Text.AlignVCenter }
                                onClicked: StreamService.playStream(streamRow.videoId)
                            }
                            C.AbstractButton {
                                width: 32; height: 32
                                contentItem: Text { text: "\u2193"; color: Theme.textSecondary; horizontalAlignment: Text.AlignHCenter; verticalAlignment: Text.AlignVCenter }
                                onClicked: DownloaderService.downloadStream(streamRow.videoId, streamRow.title, streamRow.artist)
                            }
                        }
                    }
                }
            }

            // All tabs (Stream, Songs, Playlists, Albums, Artists) now have active panes
        }
    }

    // ---- mini player dock (M2.2 + M5.4) ----
    MiniPlayer {
        id: miniPlayer
        anchors { left: parent.left; right: parent.right; bottom: parent.bottom }
        z: 5
        onOpenNowPlayingRequested: root.nowPlayingOpen = true
    }

    // ---- Now Playing overlay ----
    IanGlass {
        id: nowPlayingOverlay
        visible: root.nowPlayingOpen
        anchors.fill: parent
        z: 20

        MouseArea { anchors.fill: parent; onClicked: root.nowPlayingOpen = false }

        IanGlass {
            anchors.centerIn: parent
            width: Math.min(parent.width - 80, 520)
            height: Math.min(parent.height - 80, 640)
            radius: Theme.radius * 2

            ColumnLayout {
                anchors { fill: parent; margins: 24 }
                spacing: 16

                Rectangle {
                    Layout.alignment: Qt.AlignHCenter
                    Layout.preferredWidth: 300; Layout.preferredHeight: 300
                    color: Theme.surface2; radius: 20; clip: true
                    Image { anchors.fill: parent; source: QueueManager.currentArt; fillMode: Image.PreserveAspectCrop; smooth: true }
                }

                C.Label { Layout.alignment: Qt.AlignHCenter; text: QueueManager.currentTitle; color: Theme.textPrimary; font { family: Theme.fontFamily; pixelSize: Theme.sizeH1; weight: Theme.weightSemiBold } }
                C.Label { Layout.alignment: Qt.AlignHCenter; text: QueueManager.currentArtist; color: Theme.textSecondary; font { family: Theme.fontFamily; pixelSize: Theme.sizeBody } }

                SeekBar {
                    Layout.fillWidth: true
                    progress: QueueManager.durationMsCurrent() > 0 ? QueueManager.positionMs() / QueueManager.durationMsCurrent() : 0
                    onSeekRequested: (frac) => {
                        if (QueueManager.durationMsCurrent() > 0)
                            QueueManager.seekToMs(frac * QueueManager.durationMsCurrent())
                    }
                }

                RowLayout {
                    Layout.alignment: Qt.AlignHCenter; spacing: 16
                    GlassButton { Layout.preferredWidth: 40; Layout.preferredHeight: 40; text: "🔀"; onClicked: QueueManager.toggleShuffle() }
                    GlassButton { Layout.preferredWidth: 40; Layout.preferredHeight: 40; text: "⏮"; onClicked: QueueManager.playPrevious() }
                    GlassButton { Layout.preferredWidth: 56; Layout.preferredHeight: 56; text: QueueManager.isPlaying ? "⏸" : "▶"; primary: true; onClicked: QueueManager.togglePlayPause() }
                    GlassButton { Layout.preferredWidth: 40; Layout.preferredHeight: 40; text: "⏭"; onClicked: QueueManager.playNext() }
                    GlassButton { Layout.preferredWidth: 40; Layout.preferredHeight: 40; text: "🔁"; onClicked: QueueManager.toggleRepeat() }
                }

                C.Label {
                    Layout.alignment: Qt.AlignHCenter
                    text: LyricService.lyricText
                    color: Theme.textSecondary
                    font { family: Theme.fontFamily; pixelSize: Theme.sizeSmall }
                    wrapMode: Text.WordWrap
                    Layout.fillWidth: true
                    Layout.preferredHeight: 80
                }
                C.Label { Layout.alignment: Qt.AlignHCenter; text: qsTr("Esc untuk minimize"); color: Theme.textDisabled; font { family: Theme.fontFamily; pixelSize: Theme.sizeSmall } }
            }
        }
    }

    // ---- recap overlay (M5.5) ----
    Rectangle {
        id: recapOverlay
        visible: root.recapOpen
        anchors.fill: parent
        color: "#CC000000"
        z: 25

        MouseArea { anchors.fill: parent; onClicked: root.recapOpen = false }

        Rectangle {
            anchors.centerIn: parent
            width: Math.min(parent.width - 80, 480)
            height: Math.min(parent.height - 80, 520)
            color: Theme.surface1
            radius: Theme.radius * 2

            ColumnLayout {
                anchors { fill: parent; margins: 20 }
                spacing: 8

                C.Label { text: qsTr("Monthly Recap"); color: Theme.textPrimary; font { family: Theme.fontFamily; pixelSize: Theme.sizeH2; weight: Theme.weightSemiBold } }

                ListView {
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    clip: true
                    reuseItems: true
                    model: LibraryService.topPlayedSongs(10)
                    spacing: 4
                    delegate: RowLayout {
                        required property var modelData
                        width: parent.width
                        C.Label { text: index + 1 + "."; color: Theme.textDisabled; font { family: Theme.fontFamily; pixelSize: Theme.sizeSmall } }
                        ColumnLayout {
                            Layout.fillWidth: true; spacing: 0
                            C.Label { text: modelData.title; color: Theme.textPrimary; font { family: Theme.fontFamily; pixelSize: Theme.sizeBody; weight: Theme.weightMedium } }
                            C.Label { text: modelData.artist; color: Theme.textSecondary; font { family: Theme.fontFamily; pixelSize: Theme.sizeSmall } }
                        }
                    }
                }

                C.Button { text: qsTr("Tutup"); onClicked: root.recapOpen = false; font.family: Theme.fontFamily }
            }
        }
    }

    // ---- shortcut overlay ----
    Rectangle {
        id: shortcutOverlay
        visible: root.shortcutsOpen
        anchors.fill: parent
        color: "#CC000000"
        z: 30
        MouseArea { anchors.fill: parent; onClicked: root.shortcutsOpen = false }
        Rectangle {
            anchors.centerIn: parent
            width: 420; height: 420
            color: Theme.surface1; radius: Theme.radius * 2
            ColumnLayout {
                anchors { fill: parent; margins: 20 }
                spacing: 6
                C.Label { text: qsTr("Keyboard Shortcuts"); color: Theme.textPrimary; font { family: Theme.fontFamily; pixelSize: Theme.sizeH2; weight: Theme.weightSemiBold } }
                Repeater {
                    model: [
                        qsTr("Space \u2014 Play/Pause"),
                        qsTr("\u2190 / \u2192 \u2014 Rewind/Forward 10s"),
                        qsTr("\u2191 / \u2193 \u2014 Volume (placeholder)"),
                        qsTr("N / P \u2014 Next / Previous"),
                        qsTr("S \u2014 Shuffle toggle"),
                        qsTr("R \u2014 Repeat cycle"),
                        qsTr("Q \u2014 Now Playing"),
                        qsTr("Esc \u2014 Minimize/Close overlay"),
                        qsTr("? \u2014 Shortcut overlay"),
                    ]
                    delegate: C.Label {
                        required property string modelData
                        text: modelData
                        color: Theme.textSecondary
                        font { family: Theme.fontFamily; pixelSize: Theme.sizeSmall }
                    }
                }
            }
        }
    }

    DownloadToast {
        anchors {
            top: titlebar.bottom
            right: parent.right
            margins: 16
        }
        z: 15
    }

    SettingsDialog {
        id: settingsDialog
    }

    Item {
        anchors.fill: parent
        focus: true
        Keys.onPressed: (event) => {
            if (event.key === Qt.Key_Space) { QueueManager.togglePlayPause(); event.accepted = true; }
            else if (event.key === Qt.Key_Left) { QueueManager.seekToMs(QueueManager.positionMs() - 10000); event.accepted = true; }
            else if (event.key === Qt.Key_Right) { QueueManager.seekToMs(QueueManager.positionMs() + 10000); event.accepted = true; }
            else if (event.key === Qt.Key_N) { QueueManager.playNext(); event.accepted = true; }
            else if (event.key === Qt.Key_P) { QueueManager.playPrevious(); event.accepted = true; }
            else if (event.key === Qt.Key_S) { QueueManager.toggleShuffle(); event.accepted = true; }
            else if (event.key === Qt.Key_R) { QueueManager.toggleRepeat(); event.accepted = true; }
            else if (event.key === Qt.Key_Q) { root.nowPlayingOpen = true; event.accepted = true; }
            else if (event.key === Qt.Key_Escape) { root.nowPlayingOpen = false; root.shortcutsOpen = false; root.recapOpen = false; event.accepted = true; }
            else if (event.text === "?") { root.shortcutsOpen = !root.shortcutsOpen; event.accepted = true; }
            else if (event.key === Qt.Key_M) { root.recapOpen = !root.recapOpen; event.accepted = true; }
        }
    }
}
