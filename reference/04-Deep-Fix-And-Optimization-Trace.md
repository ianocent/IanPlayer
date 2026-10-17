# IanPlayer — Deep Fix & Optimization Trace (Post-Implementation Audit)

**Status:** Root-cause audit of the current `files.zip` build against the 4 screenshots + the two planning docs already produced (`IANPLAYER_OPTIMIZATION_UNIFIED.md`, `PHASE_EXECUTION_PLAN.md`).
**Method:** Direct code read of `files.zip` (the actual agent-implemented state), cross-checked against screenshot behavior. This is not a re-list of the earlier PRD/Milestone points — it's what's *actually wrong in the code* and what Convx does structurally different that IanPlayer should borrow.

---

## 0. TL;DR — apa yang beneran rusak

Gwa udah trace kodenya, dan nemu **3 root cause konkret** yang match persis sama 3 dari 4 screenshot lu. Ini bukan "kurang polish", ini bug logic yang bisa langsung diperbaiki:

| Screenshot | Symptom | Root cause | File |
|---|---|---|---|
| #3 (gray panel + cut text "song by :") | Glass card meledak jadi panel setengah layar, isinya cuma text mepet di atas | `IanGlassSurface` pakai `Modifier.fillMaxSize()` untuk layer blur/tint/highlight-nya, padahal seharusnya `Modifier.matchParentSize()` | `ui/glass/IanGlass.kt` |
| #3 (artwork kecil nyangkut kiri-atas, gak morph sempurna) | Hero animation dari mini-player ke full player kelihatan "nyangkut" di tengah jalan | Feedback loop: posisi target diukur dari node yang lagi dianimasi sendiri, jadi animasi restart terus dan gak pernah selesai | `ui/NowPlayingScreen.kt` (hero morph block) |
| #2 (Stream tab kosong, cuma header "Listening to") | List for-you/trending kosong total | `fetchHomeSongs()` nembak endpoint `search` pakai chart query + params blob yang di-hardcode, semua exception ditelan `catch (_: Exception) {}` tanpa log/fallback | `data/YTMusicRepository.kt` |

Detail & fix di bawah. Bagian setelah itu adalah **perbandingan arsitektur lebih dalam vs Convx** (fetching pattern, state pattern, canvas/glass pattern) yang jadi alasan kenapa 3 bug di atas gampang muncul di struktur kode sekarang.

---

## 1. Root Cause #1 — `IanGlassSurface` meledak ukurannya

### Kode saat ini
```kotlin
// ui/glass/IanGlass.kt
fun IanGlassSurface(...) {
    Box(modifier.clip(shape)) {
        if (config.enabled) {
            if (canBlur) {
                Box(
                    Modifier
                        .fillMaxSize()          // ← masalah
                        .graphicsLayer { renderEffect = BlurEffect(...) }
                        .background(glassVibrancyBrush(config))
                )
            } else {
                Box(Modifier.fillMaxSize().background(glassVibrancyBrush(config)))   // ← masalah
            }
            Box(Modifier.fillMaxSize().background(config.tint))                     // ← masalah
            Box(Modifier.fillMaxSize().drawBehind { glassHighlight(config) })        // ← masalah
        }
        content(this)
    }
}
```

Dipanggil di `NowPlayingScreen.kt` buat card kecil "Song by :" gini:
```kotlin
Column(modifier = Modifier.weight(1f)) {
    IanGlassSurface(
        config = glassConfig,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),   // ← TIDAK ada height
        shape = RoundedCornerShape(26.dp)
    ) {
        Row(...) { Text("Song by :"); ... }
    }
}
```

### Kenapa ini bikin panel raksasa
`Box` (Compose) mengukur ukurannya sendiri dari **children terbesar**. `modifier` yang dikasih ke `IanGlassSurface` cuma `fillMaxWidth()` — gak ada `wrapContentHeight()` atau tinggi eksplisit. Begitu ada child yang pakai `Modifier.fillMaxSize()`, child itu minta tinggi **selebar constraint yang diteruskan dari parent** (`Column` di dalam `Row(verticalAlignment = Alignment.Top)`), yang dalam struktur ini efektif meneruskan constraint longgar dari container terluar (Column full-screen player yang punya tinggi hampir 1 layar). Hasilnya: Box glass ini ikut membesar setinggi itu, padahal isi aslinya (`content(this)` — cuma 1 Row teks pendek) harusnya cuma setinggi ~40dp.

Ini persis kejadian di screenshot #3: panel abu-abu translucent memanjang ke bawah, padahal isinya cuma teks "Song by :" + 3 icon yang nempel di bagian paling atas panel.

### Fix
Ganti semua `Modifier.fillMaxSize()` di layer dekoratif (blur/tint/highlight) jadi **`Modifier.matchParentSize()`**. `matchParentSize()` itu modifier khusus yang cuma valid di dalam `BoxScope`, dan cara kerjanya: dia menunggu ukuran final Box ditentukan oleh sibling lain (`content()`), lalu baru ikut ukuran itu — bukan ikut nentuin ukuran duluan seperti `fillMaxSize()`. Ini pattern standar Compose untuk "background/decoration layer yang gak boleh mempengaruhi ukuran parent."

```kotlin
Box(modifier.clip(shape)) {
    if (config.enabled) {
        if (canBlur) {
            Box(
                Modifier
                    .matchParentSize()   // fix
                    .graphicsLayer { renderEffect = BlurEffect(...) }
                    .background(glassVibrancyBrush(config))
            )
        } else {
            Box(Modifier.matchParentSize().background(glassVibrancyBrush(config)))
        }
        Box(Modifier.matchParentSize().background(config.tint))
        Box(Modifier.matchParentSize().drawBehind { glassHighlight(config) })
    }
    content(this)
}
```
Setelah ini di-fix, semua pemanggil `IanGlassSurface` lain (mini-player, tombol, sheet) otomatis ikut bener ukurannya — ini bukan fix lokal, ini fix di komponen sharednya, jadi 1x perbaikan nge-cover semua tempat lu pakai glass.

---

## 2. Root Cause #2 — Hero morph animation "nyangkut" (feedback loop)

### Kode saat ini (`ui/NowPlayingScreen.kt`)
```kotlin
var targetAlbumArtRect by remember { mutableStateOf<ElementRect?>(null) }

LaunchedEffect(initialAlbumArtRect, targetAlbumArtRect) {
    val from = initialAlbumArtRect ?: return@LaunchedEffect
    val to = targetAlbumArtRect ?: return@LaunchedEffect
    heroInitScale = (from.size.width / to.size.width).coerceIn(0.15f, 1f)
    heroInitOffsetX = from.center.x - to.center.x
    heroInitOffsetY = from.center.y - to.center.y
    heroAnimProgress.snapTo(0f)
    heroAnimProgress.animateTo(1f, animationSpec = tween(400, easing = FastOutSlowInEasing))
}

Box(
    modifier = Modifier
        .size(110.dp)
        .onGloballyPositioned { coords ->                       // ← masalah
            val pos = coords.positionInWindow()
            val sz = coords.size
            targetAlbumArtRect = ElementRect(center = ..., size = ...)
        }
        .graphicsLayer {
            val p = heroAnimProgress.value
            scaleX = heroInitScale + (1f - heroInitScale) * p    // transform yang sama
            scaleY = heroInitScale + (1f - heroInitScale) * p
            translationX = heroInitOffsetX * (1f - p)
            translationY = heroInitOffsetY * (1f - p)
            ...
        }
)
```

### Kenapa ini "nyangkut" persis kayak screenshot
`onGloballyPositioned` melaporkan posisi **setelah** semua transform diterapkan — termasuk `graphicsLayer` yang barusan lu pasang di composable yang sama. Jadi:

1. Frame animasi jalan, `heroAnimProgress` naik sedikit → `scaleX/Y` dan `translationX/Y` berubah.
2. Compose re-layout node ini → `onGloballyPositioned` terpanggil lagi dengan posisi **baru** (karena posisinya sendiri lagi bergerak akibat translasi animasi) → `targetAlbumArtRect` berubah value-nya.
3. `LaunchedEffect(initialAlbumArtRect, targetAlbumArtRect)` di-key oleh `targetAlbumArtRect` → begitu value-nya berubah, **coroutine lama di-cancel dan effect block dijalanin ulang dari awal**, termasuk `heroAnimProgress.snapTo(0f)`.
4. Progress ke-reset ke 0 sebelum sempat nyampe 1 → animasi gak pernah selesai, mentok di titik yang keliatan "beku di tengah jalan" — ini biang kerok kenapa artwork di screenshot #3 masih kecil & offset ke kiri-atas walau seharusnya udah full-size di tengah.

Ini classic **measurement feedback loop**: elemen yang posisinya diukur juga sedang di-drive oleh hasil pengukuran itu sendiri.

### Fix
Jangan ukur target rect dari node yang lagi ditransform. Dua opsi, pilih salah satu:

**Opsi A (disarankan, paling simpel):** Ukur target rect dari node **sebelum** `graphicsLayer` morph dipasang — taruh `onGloballyPositioned` di modifier chain SEBELUM `graphicsLayer`, dan pastikan itu hanya dipanggil sekali saat progress belum jalan (atau gunakan `Modifier.onGloballyPositioned` di parent `Box` yang tidak ikut ditransform, bukan di node yang discale/translate).

```kotlin
Box(
    modifier = Modifier
        .size(110.dp)
        .onGloballyPositioned { coords ->
            // Hanya capture SEKALI, sebelum morph mulai — bukan tiap frame.
            if (targetAlbumArtRect == null) {
                targetAlbumArtRect = ElementRect(...)
            }
        }
        .graphicsLayer { /* transform pakai heroAnimProgress seperti biasa */ }
)
```
Karena target rect (posisi akhir di layout full player) itu **statis** — dia gak berubah selama layout gak berubah — capture sekali aja sudah cukup, gak perlu terus-menerus di-update tiap frame animasi.

**Opsi B (lebih robust untuk rotation/orientation change):** Pisahkan pengukuran target rect dari composable yang sama sekali tidak dikenai `graphicsLayer` — misalnya bungkus artwork dengan `Box` luar (tanpa transform) yang isinya `Box` dalam (yang kena transform), dan taruh `onGloballyPositioned` di Box luar.

Tambahan: key `LaunchedEffect` sebaiknya cuma `initialAlbumArtRect` (bukan `targetAlbumArtRect` juga), dan `targetAlbumArtRect` dibaca sebagai snapshot state biasa di dalam efek — supaya efek gak retrigger tiap target berubah:
```kotlin
LaunchedEffect(initialAlbumArtRect) {
    val from = initialAlbumArtRect ?: return@LaunchedEffect
    snapshotFlow { targetAlbumArtRect }
        .filterNotNull()
        .first()
        .let { to -> /* hitung offset & jalankan animasi sekali */ }
}
```

---

## 3. Root Cause #3 — Stream tab kosong (`fetchHomeSongs`)

### Kode saat ini (`data/YTMusicRepository.kt`)
```kotlin
suspend fun fetchHomeSongs(onPartial: (List<Song>) -> Unit, countryCode: String = "US"): StreamSearchResult =
    withContext(Dispatchers.IO) {
        val queries = listOf("Billboard Hot 100", "UK Official Singles Chart Top 40", ...)
        for (query in queries) {
            try {
                val body = JSONObject().apply {
                    put("context", clientContext(countryCode, "en"))
                    put("query", query)
                    put("params", "EgWKAQIIAWoKEAMQBBAFEAoQCQ==")   // ← opaque, hardcoded
                }
                val raw = post("search", body) ?: continue          // ← "home feed" via search endpoint
                ...
            } catch (_: Exception) {}                                 // ← ditelan, no log
        }
        if (allResults.isEmpty()) StreamSearchResult.Empty else StreamSearchResult.Success(allResults)
    }
```

### Masalahnya dua lapis
1. **Bukan home-feed endpoint asli.** Ini nembak endpoint `search` dengan query teks ("Billboard Hot 100" dst) plus `params` yang isinya blob base64 opaque (protobuf-encoded filter). Kalau YT Music berubah struktur respons search (hal yang sering kejadian — sama kayak yang lu udah alami sendiri di skrip SEO python lu soal AI Mode ngerusak selector SERP), seluruh query bisa balikin bentuk JSON yang beda dan `walkMusicContents()` gak nemu apa-apa → array kosong → tab kosong, **tanpa ada tanda kesalahan apapun ke user atau ke log.**
2. **Exception ditelan total** (`catch (_: Exception) {}`) di dua level (per-item try/catch dan per-query try/catch). Kalau request gagal (network, 403, format berubah, context/visitorData basi), gak ada log Logcat, gak ada state error yang bisa ditampilkan — makanya dari luar keliatannya "cuma nge-load kosong selamanya" padahal sebenarnya *silently failing*.

### Perbandingan ke pendekatan Convx (`innertube` module)
Convx (dan proyek asal-nya, vivi-music) tidak meniru "home feed" pakai endpoint `search` + query teks. Modul `innertube` mereka pakai pendekatan **browse-based**, yaitu:
- Manggil endpoint `browse` dengan `browseId` khusus (mis. `FEmusic_home` untuk halaman Home YT Music asli), bukan `search`.
- Respons `browse` untuk home feed itu terstruktur per-section (carousel "Quick picks", "Mixed for you", "New releases", dst) dengan masing-masing section punya **continuation token** sendiri buat "load more" — bukan satu array flat hasil pencarian macam-macam kata kunci chart.
- Tiap section diparse independent, jadi kalau satu section formatnya berubah, section lain tetap jalan (isolasi kegagalan per-section, bukan per-keseluruhan-request).

**Rekomendasi konkret buat IanPlayer (tanpa nyontek kode Convx, cukup pola arsitekturnya):**
1. Tambah endpoint `browse` di `YTMusicRepository` (di samping `search` yang udah ada), pakai `browseId` untuk home feed asli, bukan reuse `search` dengan query teks chart.
2. Ganti `catch (_: Exception) {}` jadi minimal `catch (e: Exception) { Log.w(TAG, "fetchHomeSongs query=$query failed", e) }` — supaya kalau tab kosong lagi, ada jejak di Logcat buat debug, bukan misteri.
3. Ubah `StreamSearchResult` biar bisa bawa **state error eksplisit** (`StreamSearchResult.Error(reason)`), bukan cuma `Empty` vs `Success`. Di UI (`StreamTab.kt`), tampilkan state kosong-karena-gagal beda dari kosong-karena-emang-gak-ada-data (misal: "Gagal memuat rekomendasi, coba lagi" + tombol retry — daripada card putih kosong selamanya kayak di screenshot #2).
4. Karena `params` base64 itu opaque dan gampang basi, treat dia sebagai *fragile config* — taruh di satu tempat (constant terpisah, dikomentari sumbernya/tanggal terakhir diverifikasi), bukan inline literal di tengah fungsi, biar gampang di-swap kalau YT ubah struktur lagi.

---

## 4. Kenapa bug-bug ini gampang muncul — gap arsitektur vs Convx (lebih dalam dari list layout kemarin)

Ini bagian yang lu minta: bukan cuma "4 layout" tapi kenapa secara struktural IanPlayer rawan begini, dibanding gimana Convx menghindarinya.

### 4.1 State — satu composable narik 20 flow sekaligus
`rememberPlayerUiState()` (di `ui/player/PlayerUiState.kt`) `collectAsState()` di atas **20 flow terpisah** dari satu `MusicViewModel` (1386 baris) dalam satu composable function, lalu dipanggil ulang di **setiap layout** (`BASE_IAN`, `YT_MUSIC`, `SPOTIFY`, `APPLE_MUSIC` masing-masing manggil `rememberPlayerUiState(viewModel)` sendiri-sendiri). Konsekuensi:
- Setiap kali SATU flow berubah (misal cuma `currentPosition` tiap detik), seluruh composable yang manggil `rememberPlayerUiState` re-run, lalu semua *turunan* `remember(...)` di layout ikut kena recomposition scope yang lebih lebar dari yang perlu (karena `PlayerUiState` adalah satu data class besar — Compose gak bisa skip-recompose sebagian field-nya, beda kasusnya sama kalau tiap field di-collect & dipakai independent di titik pemakaiannya).
- Convx sengaja pecah ViewModel jadi lebih granular per-concern (PRD kemarin udah nyebut ini di tabel perbandingan) — tapi belum dieksekusi. Efek nyatanya baru kelihatan sekarang: makin banyak layout yang ditambahin (4 layout x 20 flow), makin berat kerja recomposition per detik playback berjalan, apalagi kalau ditambah animasi infinite (gradient, marquee) yang jalan terus selama song playing.

**Rekomendasi:** pecah `PlayerUiState` jadi minimal 3 slice yang dikonsumsi terpisah sesuai kebutuhan tiap bagian UI: `PlaybackSlice` (posisi/durasi/isPlaying, update tiap detik), `MetadataSlice` (song/art/lyrics, update tiap ganti lagu), `PreferenceSlice` (theme/motion/glass setting, jarang berubah). Bagian UI yang cuma butuh metadata (misal title/artist) gak perlu ikut recompose tiap detik gara-gara posisi playback jalan.

### 4.2 Glass — "blur" yang sebenarnya bukan backdrop blur
Convx punya real **backdrop capture**: `rememberLayerBackdrop()` + `Modifier.layerBackdrop(...)` benar-benar merekam pixel di belakang suatu subtree, lalu surface lain (nav bar, sheet, tombol) bisa nge-sample & refract pixel itu lewat `drawBackdrop(...)` — makanya kalau ada artwork/canvas warna-warni di belakang, kaca beneran keliatan blur dari warna itu.

`IanGlassSurface` versi sekarang **bukan backdrop blur** — dia blur `Modifier.background(glassVibrancyBrush(config))` miliknya sendiri (gradient flat putih transparan), bukan konten di belakangnya. Efeknya: walau dipasang `BlurEffect`, visualnya tetap keliatan "kaca buram polos", bukan "kaca yang merefleksikan warna canvas di belakangnya" — beda jauh sama kesan Liquid Glass Convx meskipun namanya sama-sama "glass". Ini alasan kenapa walau bug #1 di atas udah di-fix (ukuran bener), hasilnya tetap gak akan "kaya Convx" — karena memang secara desain ini fake-glass, bukan real backdrop sampling.

**Untuk beneran deket sama referensi tanpa nyalin kode GPL Convx:** implementasi minimal backdrop sampling di Compose modern bisa pakai `Modifier.graphicsLayer` + `RenderNode`/`drawWithContent` pattern untuk capture subtree ke layer terpisah, lalu sample layer itu di surface lain — ini tersedia di Compose versi yang mendukung `GraphicsLayer` API (`rememberGraphicsLayer()`, `Modifier.drawWithContent { layer.record { ... }; drawLayer(layer) }`, direct API dari androidx.compose.ui.graphics.layer). Ini scope-nya cukup besar (worth jadi milestone sendiri), tapi kalau mau "smooth transisi & enjoyable" match ke referensi, item ini yang paling menentukan kesan visual "premium" — bukan cuma ukuran panel yang dibenerin.

### 4.3 Canvas — masih 1:1 swap, belum provider chain
Planning doc kemarin (M2) udah nyebut "Canvas provider + cache architecture", dan di kode emang udah ada folder `data/canvas/providers/CanvasProvider.kt` — tapi cek isinya: baru interface kosong/skeleton, belum ada implementasi provider kedua (misal remote canvas source) atau priority chain fallback yang nyata dipakai `PlayerBackdrop`. `rememberPlayerBackdrop()` masih langsung `if (canvasMode == VIDEO) resolve local video else null`, artinya kalau video local gak ketemu, fallback-nya diam-diam ke `Modifier` kosong (background gradient doang) — gak ada percobaan provider lain. Ini baseline yang sama kayak sebelum optimasi, cuma dipindah lokasi filenya doang.

### 4.4 Motion tokens didefinisikan tapi gak dipakai konsisten
`ui/motion/MotionTokens.kt` udah bagus — ada `FAST/STANDARD/EXPRESSIVE`, named spring (`artworkMorph`, `paletteTransition`, dst). Tapi di 3 layout (`YTMusicPlayerLayout`, `SpotifyPlayerLayout`, `AppleMusicPlayerLayout`) dan di `NowPlayingScreen.kt`, animasi masih ditulis manual (`tween(400, ...)`, `tween(500)`, `infiniteRepeatable(tween(30000))` inline) — bukan `MotionTokens.artworkMorph` dkk. Efeknya: rasa transisi antar layout gak konsisten satu sama lain padahal itu tujuan awal (M1: "spring-based motion primitives dan motion tokens yang konsisten"). Ini murni disiplin refactor: ganti semua angka durasi/easing hardcoded di 4 file layout + NowPlayingScreen jadi refer ke `MotionTokens`.

### 4.5 `reduceMotion` & `motionScale` setengah diterapkan
`PlayerUiState` udah punya `reduceMotion: Boolean` dan `motionScale: Float` (dari screenshot #1, user bahkan sudah nge-toggle "Reduce Motion" ON dan animation scale ke ~30%). Tapi dari layout yang ke-trace:
- `motionScale` **gak dipakai sama sekali** di 3 layout + backdrop — semua durasi tween fixed, gak dikalikan `motionScale`.
- `reduceMotion` cuma dicek di 2 tempat (`MarqueeText` di YT layout, `gradientAngle` di `rememberPlayerBackdrop`) — hero morph animation di `NowPlayingScreen.kt` (root cause #2 di atas) **sama sekali gak cek `reduceMotion`**, padahal itu justru animasi paling berat/paling gampang keliatan glitch kalau setengah jalan.

**Rekomendasi:** buat 1 helper terpusat, misal `MotionTokens.scaledTween(baseDurationMs, motionScale, reduceMotion)` yang tiap composable panggil, bukan masing-masing manual cek kondisi. Lebih gampang audit "apakah semua animasi di app ini respect Reduce Motion" kalau cuma ada 1 titik masuk.

---

## 5. Prioritas eksekusi (revisi dari Milestone doc, versi "fix dulu baru cantik")

Urutan ini beda dari milestone besar kemarin — ini prioritas berdasarkan **apa yang bikin app keliatan rusak sekarang juga**, sebelum lanjut nambah fitur baru:

1. **Fix `IanGlassSurface` (`matchParentSize`)** — 1 baris pattern, dampaknya nyebar ke semua tempat pakai glass. Paling murah, paling tinggi dampak visual.
2. **Fix hero morph feedback loop** (capture target rect sekali, bukan tiap frame; key `LaunchedEffect` cuma dari `initialAlbumArtRect`). Ini yang bikin transisi mini→full player keliatan "smooth" beneran, sesuai tujuan awal lu.
3. **Fix `fetchHomeSongs` silent failure** — minimal tambah logging + state error eksplisit dulu (jangan langsung buru-buru pindah ke `browse` endpoint kalau belum tau apakah `search` endpoint-nya emang beneran gagal atau cuma parsing yang gak cocok — cek log dulu baru putuskan).
4. **Terapkan `MotionTokens` + `reduceMotion`/`motionScale` secara konsisten** di 4 layout & `NowPlayingScreen` — sekali beres, semua transisi otomatis punya "bahasa gerak" yang sama, dan setting yang user udah utak-atik di screenshot #1 (Reduce Motion ON, Animation Scale) beneran ngefek ke semua tempat, bukan cuma 2 titik.
5. **Baru lanjut ke item lama yang belum tersentuh:** real backdrop-sampling glass (§4.2), canvas provider chain nyata (§4.3), pecah `PlayerUiState` jadi slice (§4.1) — ini tiga item besar yang layak jadi milestone tersendiri, bukan quick-fix.

---

## 6. Catatan lisensi (tetap berlaku)

Semua rekomendasi di atas — termasuk pola `browse`-based home feed, real backdrop sampling via GraphicsLayer API, dan state-slicing — adalah **pola arsitektur umum di ekosistem Compose/InnerTube-client**, bukan kode yang disalin dari Convx (GPL-3.0). Implementasi tetap harus ditulis ulang sesuai gaya kode IanPlayer sendiri (nama fungsi, struktur file, konvensi yang sudah ada di `YTMusicRepository.kt`/`data/canvas/`).
