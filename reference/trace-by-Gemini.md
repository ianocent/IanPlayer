# Traceability Matrix: Convx Features to IanPlayer Architecture

Dokumen ini memetakan implementasi dari referensi visual/mekanik di **Convx** langsung ke *file target* yang ada di dalam *source code* **IanPlayer**.

| Fitur / Mekanik dari Convx | Target File di IanPlayer | Instruksi / Catatan Optimasi |
| :--- | :--- | :--- |
| **Backdrop Blur / Refraction** <br>(`layerBackdrop`, `drawBackdrop`) | `ui/canvas/PlayerCanvas.kt`<br>`ui/theme/Theme.kt` | Hapus elemen transparansi biasa (*alpha flat*). Jadikan `PlayerCanvas.kt` sebagai pusat pelemparan *pixel background* untuk diburamkan *real-time* ke kartu UI atau *navbar* di atasnya. |
| **Theme / Layout Settings** <br>(Apple Music V17 style toggle) | `data/SettingsStore.kt`<br>`ui/NowPlayingScreen.kt`<br>`viewmodel/MusicViewModel.kt` | Simpan *state layout*. Gunakan *ViewModel* untuk melakukan *trigger re-composition* di `NowPlayingScreen` agar langsung mengubah bentuk tata letak ke mode Apple, Spotify, YT, atau Base tanpa *restart*. |
| **Bouncy / iOS Overscroll** | `ResponsiveSnapList.kt`<br>`ui/SongListComponents.kt` | Ganti kurva pergerakan animasi bawaan (*tween/easing* standar Material) menjadi fungsi `spring()`. Buat *modifier overscroll* kustom untuk efek mantul *(rubber band)* saat mentok ujung daftar. |
| **Blurred Page Transitions** | `ui/NavState.kt`<br>`MainActivity.kt` | Transisi masuk-keluar saat ganti *tab/screen* harus menyertakan efek *fade & slight blur* alih-alih cuma geser (*slide-in/out*). |
| **Synced Karaoke Lyrics** | `ui/LyricCardGenerator.kt`<br>`data/LyricRepository.kt` | Tarik waktu (*timestamp*) lirik dari `LyricRepository`, lalu *listen* pergerakan *millisecond* lagu untuk mengubah ukuran/warna tiap kata di `LyricCardGenerator`. |
| **Dominant Color Extraction** | `data/AlbumArtLoader.kt`<br>`ui/theme/Color.kt` | Harus jalan *asynchronous* dan stabil. Khusus untuk **Spotify Layout**, *Color.kt* butuh racikan *gradient brush* berdasarkan hasil palet `AlbumArtLoader`. |
| **Wave/Background Sync** | `player/WaveProjectionService.kt`<br>`ui/WaveRecordCard.kt` | Jika menggunakan **Apple/Base Layout**, *Wave Projection* harus berpadu dengan kanvas refraksi agar ombak visualizernya ikut buram (*frosted*) ketika ditimpa UI/Lirik. |