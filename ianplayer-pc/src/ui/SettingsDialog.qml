import QtQuick
import QtQuick.Controls as C
import QtQuick.Layouts
import IanPlayer.PC

C.Dialog {
    id: root

    title: qsTr("Pengaturan")
    modal: true
    anchors.centerIn: parent
    width: Math.min(parent.width - 60, 640)
    height: Math.min(parent.height - 60, 520)

    background: Rectangle {
        color: Theme.osdBg
        border.color: Theme.borderGlassBright
        border.width: 1
        radius: Theme.radiusCard
    }

    contentItem: ColumnLayout {
        spacing: 16
        anchors.margins: 16

        RowLayout {
            Layout.fillWidth: true

            Text {
                text: qsTr("Pengaturan IanPlayer")
                color: Theme.textPrimary
                font {
                    family: Theme.fontFamily
                    pixelSize: Theme.sizeH2
                    weight: Theme.weightBold
                }
            }

            Item { Layout.fillWidth: true }

            GlassButton {
                text: "\u2715"
                implicitWidth: 32
                implicitHeight: 32
                onClicked: root.close()
            }
        }

        RowLayout {
            Layout.fillWidth: true
            Layout.fillHeight: true
            spacing: 16

            // Sidebar Kategori
            Rectangle {
                Layout.fillHeight: true
                Layout.preferredWidth: 140
                color: Theme.glassSurface
                radius: Theme.radius
                border.color: Theme.borderGlass
                border.width: 1

                ColumnLayout {
                    anchors.fill: parent
                    anchors.margins: 8
                    spacing: 4

                    property int selectedCategory: 0

                    GlassButton {
                        Layout.fillWidth: true
                        text: qsTr("Visual & UI")
                        primary: parent.selectedCategory === 0
                        onClicked: parent.selectedCategory = 0
                    }

                    GlassButton {
                        Layout.fillWidth: true
                        text: qsTr("Playback")
                        primary: parent.selectedCategory === 1
                        onClicked: parent.selectedCategory = 1
                    }

                    GlassButton {
                        Layout.fillWidth: true
                        text: qsTr("Profil User")
                        primary: parent.selectedCategory === 2
                        onClicked: parent.selectedCategory = 2
                    }

                    GlassButton {
                        Layout.fillWidth: true
                        text: qsTr("Integrasi")
                        primary: parent.selectedCategory === 3
                        onClicked: parent.selectedCategory = 3
                    }

                    Item { Layout.fillHeight: true }
                }
            }

            // Konten Kategori
            StackLayout {
                id: categoryStack
                Layout.fillWidth: true
                Layout.fillHeight: true
                currentIndex: 0

                // Category 0: Visual & UI
                ScrollView {
                    clip: true
                    ColumnLayout {
                        width: parent.width - 16
                        spacing: 12

                        Text {
                            text: qsTr("Efek Glass & Motion")
                            color: Theme.accent
                            font { family: Theme.fontFamily; pixelSize: Theme.sizeH3; weight: Theme.weightSemiBold }
                        }

                        RowLayout {
                            Layout.fillWidth: true
                            Text { text: qsTr("Glass Blur (%1)").arg(SettingsService.glassBlur); color: Theme.textPrimary; font.family: Theme.fontFamily; Layout.preferredWidth: 140 }
                            C.Slider {
                                Layout.fillWidth: true
                                from: 4; to: 40; stepSize: 1
                                value: SettingsService.glassBlur
                                onMoved: SettingsService.glassBlur = value
                            }
                        }

                        RowLayout {
                            Layout.fillWidth: true
                            Text { text: qsTr("Glass Vibrancy (%1)").arg(SettingsService.glassVibrancy.toFixed(2)); color: Theme.textPrimary; font.family: Theme.fontFamily; Layout.preferredWidth: 140 }
                            C.Slider {
                                Layout.fillWidth: true
                                from: 0.0; to: 0.6; stepSize: 0.05
                                value: SettingsService.glassVibrancy
                                onMoved: SettingsService.glassVibrancy = value
                            }
                        }

                        RowLayout {
                            Layout.fillWidth: true
                            Text { text: qsTr("Motion Scale (%1x)").arg(SettingsService.motionScale.toFixed(1)); color: Theme.textPrimary; font.family: Theme.fontFamily; Layout.preferredWidth: 140 }
                            C.Slider {
                                Layout.fillWidth: true
                                from: 0.5; to: 2.0; stepSize: 0.1
                                value: SettingsService.motionScale
                                onMoved: SettingsService.motionScale = value
                            }
                        }

                        Rectangle { Layout.fillWidth: true; height: 1; color: Theme.borderGlass }

                        Text {
                            text: qsTr("Player Layouts")
                            color: Theme.accent
                            font { family: Theme.fontFamily; pixelSize: Theme.sizeH3; weight: Theme.weightSemiBold }
                        }

                        RowLayout {
                            Layout.fillWidth: true
                            Text { text: qsTr("Style Seek Bar"); color: Theme.textPrimary; font.family: Theme.fontFamily; Layout.preferredWidth: 140 }
                            GlassComboBox {
                                Layout.fillWidth: true
                                model: [qsTr("Classic"), qsTr("Wave"), qsTr("Squiggle"), qsTr("Minimal")]
                                currentIndex: SettingsService.seekBarStyle
                                onActivated: (idx) => SettingsService.seekBarStyle = idx
                            }
                        }

                        RowLayout {
                            Layout.fillWidth: true
                            Text { text: qsTr("Mini Player Layout"); color: Theme.textPrimary; font.family: Theme.fontFamily; Layout.preferredWidth: 140 }
                            GlassComboBox {
                                Layout.fillWidth: true
                                model: [qsTr("Default Dock"), qsTr("Compact"), qsTr("Floating Pill"), qsTr("Queue First")]
                                currentIndex: SettingsService.miniLayoutIndex
                                onActivated: (idx) => SettingsService.miniLayoutIndex = idx
                            }
                        }
                    }
                }

                // Category 1: Playback
                ScrollView {
                    clip: true
                    ColumnLayout {
                        width: parent.width - 16
                        spacing: 12

                        Text {
                            text: qsTr("Pengaturan Audio")
                            color: Theme.accent
                            font { family: Theme.fontFamily; pixelSize: Theme.sizeH3; weight: Theme.weightSemiBold }
                        }

                        RowLayout {
                            Layout.fillWidth: true
                            Text { text: qsTr("Crossfade Duration (%1 ms)").arg(QueueManager.crossfadeMs); color: Theme.textPrimary; font.family: Theme.fontFamily; Layout.preferredWidth: 160 }
                            C.Slider {
                                Layout.fillWidth: true
                                from: 0; to: 2000; stepSize: 100
                                value: QueueManager.crossfadeMs
                                onMoved: QueueManager.crossfadeMs = value
                            }
                        }
                    }
                }

                // Category 2: Profil User
                ScrollView {
                    clip: true
                    ColumnLayout {
                        width: parent.width - 16
                        spacing: 12

                        Text {
                            text: qsTr("Profil Pengguna")
                            color: Theme.accent
                            font { family: Theme.fontFamily; pixelSize: Theme.sizeH3; weight: Theme.weightSemiBold }
                        }

                        RowLayout {
                            Layout.fillWidth: true
                            Text { text: qsTr("Nama User"); color: Theme.textPrimary; font.family: Theme.fontFamily; Layout.preferredWidth: 120 }
                            GlassTextField {
                                Layout.fillWidth: true
                                text: SettingsService.userName
                                placeholderText: qsTr("Masukkan nama anda")
                                onAccepted: SettingsService.userName = text
                            }
                        }
                    }
                }

                // Category 3: Integrasi
                ScrollView {
                    clip: true
                    ColumnLayout {
                        width: parent.width - 16
                        spacing: 12

                        Text {
                            text: qsTr("Integrasi Layanan")
                            color: Theme.accent
                            font { family: Theme.fontFamily; pixelSize: Theme.sizeH3; weight: Theme.weightSemiBold }
                        }

                        Text {
                            text: qsTr("Spotify Cookie (sp_dc)")
                            color: Theme.textPrimary
                            font { family: Theme.fontFamily; pixelSize: Theme.sizeBody; weight: Theme.weightMedium }
                        }

                        GlassTextField {
                            Layout.fillWidth: true
                            text: SettingsService.spotifySpDc
                            placeholderText: qsTr("Tempel cookie sp_dc di sini...")
                            echoMode: TextInput.Password
                            onAccepted: SettingsService.spotifySpDc = text
                        }

                        Text {
                            text: qsTr("Catatan Keamanan: Jangan berikan cookie ini kepada siapa pun. Cookie digunakan lokal untuk mengambil Spotify Canvas.")
                            color: Theme.textMuted
                            font { family: Theme.fontFamily; pixelSize: Theme.sizeSmall }
                            wrapMode: Text.WordWrap
                            Layout.fillWidth: true
                        }
                    }
                }
            }
        }
    }
}
