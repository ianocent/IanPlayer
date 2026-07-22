package com.ianocent.musicplayer.data

import android.net.Uri
import timber.log.Timber
import com.zemer.cipher.CipherDeobfuscator
import com.zemer.cipher.potoken.PoTokenGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.LinkedHashMap
import java.util.concurrent.Semaphore
import android.content.Context

class YTMusicRepository(context: Context) {

    private val playerSemaphore = Semaphore(9)

    private val streamCacheDao = AppDatabase.getInstance(context).streamCacheDao()

    // In-memory cache tetap dipertahankan sebagai layer tercepat (hindari hit DB
    // berkali-kali dalam 1 sesi aktif); Room jadi layer kedua yang persist antar
    // sesi app (survive app-kill), yang gak bisa dicover LinkedHashMap doang.
    private val streamUrlCache = object : LinkedHashMap<String, String>(50, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            return size > 50
        }
    }

    // Coba parse 'expire=<unix_epoch_detik>' dari URL googlevideo (paling akurat).
    // Kalau gak ketemu (misal dari fallback Invidious), asumsi aman 5 jam dari sekarang.
    private fun computeExpiryMs(url: String, resolvedAtMs: Long): Long {
        val match = Regex("[?&]expire=(\\d+)").find(url)
        val expireEpochSeconds = match?.groupValues?.get(1)?.toLongOrNull()
        return if (expireEpochSeconds != null) {
            expireEpochSeconds * 1000L
        } else {
            resolvedAtMs + (5 * 60 * 60 * 1000L)
        }
    }

    companion object {
        private val WEB_KEY = "AIzaSy" + "AO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
        private val ANDROID_KEY = "AIzaSy" + "A8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w"
        private const val BASE = "https://www.youtube.com/youtubei/v1"
        private const val UA_WEB = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
        private const val UA_ANDROID = "com.google.android.youtube/19.47.53 (Linux; U; Android 14; en_US) gzip"

        private val invidiousInstances = listOf(
            "https://inv.nadeko.net",
            "https://yewtu.be"
        )

        // Shared across the app: caches the WebView-based BotGuard session so we don't pay the
        // ~2-5s cold-start cost on every song, only when the session (visitorData) changes.
        private val poTokenGenerator = PoTokenGenerator()
    }

    // visitorData identifies our InnerTube "session" to Google; PoTokens are minted bound to it.
    // Refreshed opportunistically from any InnerTube response's responseContext.visitorData.
    @Volatile
    private var visitorData: String? = null

    private fun updateVisitorData(json: JSONObject) {
        val vd = json.optJSONObject("responseContext")?.optString("visitorData", null)
        if (!vd.isNullOrBlank()) visitorData = vd
    }

    private fun clientContext(): JSONObject = JSONObject().apply {
        put("client", JSONObject().apply {
            put("clientName", "WEB_REMIX")
            put("clientVersion", "1.20260701.01.00")
            put("hl", "en")
            put("gl", "ID")
            visitorData?.let { put("visitorData", it) }
        })
    }

    private suspend fun get(url: URL): String? {
        try {
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", UA_WEB)
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            if (conn.responseCode != 200) return null
            return conn.inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            return null
        }
    }

    private suspend fun fastGet(url: URL): String? {
        try {
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", UA_WEB)
            conn.connectTimeout = 2500
            conn.readTimeout = 2500
            if (conn.responseCode != 200) return null
            return conn.inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            return null
        }
    }

    private suspend fun post(
        endpoint: String,
        body: JSONObject,
        apiKey: String = WEB_KEY,
        userAgent: String = UA_WEB,
        origin: String = "https://music.youtube.com"
    ): String? {
        try {
            val url = URL("$BASE/$endpoint?key=$apiKey&prettyPrint=false")
            Timber.d("YTMusicRepo POST $endpoint")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            conn.setRequestProperty("Origin", origin)
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            val bytes = body.toString().toByteArray()
            conn.outputStream.use { it.write(bytes) }

            val code = conn.responseCode
            Timber.d("YTMusicRepo POST $endpoint -> $code")
            if (code != 200) {
                val errBody = try { conn.errorStream?.bufferedReader()?.readText()?.take(200) } catch (_: Exception) { null }
                Timber.w("YTMusicRepo InnerTube $endpoint -> $code: $errBody")
                return null
            }
            return conn.inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            Timber.w("YTMusicRepo InnerTube $endpoint: ${e.message}")
            return null
        }
    }

    private suspend fun extractAudioUrl(format: JSONObject, videoId: String): String? {
        val directUrl: String? = format.optString("url", null)
        if (!directUrl.isNullOrBlank()) {
            return CipherDeobfuscator.transformNParamInUrl(directUrl)
        }

        val cipher: String? = format.optString("signatureCipher", null)
        if (cipher.isNullOrBlank()) return null

        // Preferred path: proper BotGuard/WebView-based deobfuscation (handles current YouTube
        // player rotations, unlike the legacy manual "s"/"sp" passthrough below).
        try {
            val deciphered = CipherDeobfuscator.deobfuscateStreamUrl(cipher, videoId)
            if (!deciphered.isNullOrBlank()) {
                return CipherDeobfuscator.transformNParamInUrl(deciphered)
            }
        } catch (e: Exception) {
            Timber.w("YTMusicRepo CipherDeobfuscator failed for $videoId: ${e.message}")
        }

        // Legacy fallback (pre-cipher-rotation era; kept as a last resort, rarely works anymore).
        val params = mutableMapOf<String, String>()
        for (pair in cipher.split("&")) {
            val kv = pair.split("=", limit = 2)
            if (kv.size == 2) params[kv[0]] = URLDecoder.decode(kv[1], "UTF-8")
        }

        val baseUrl = params["url"] ?: return null
        val sig = params["s"] ?: return baseUrl
        val sp = params["sp"] ?: "sig"

        return "$baseUrl&$sp=$sig"
    }

    private fun walkMusicContents(json: JSONObject): JSONArray {
        val contents = json.optJSONObject("contents")

        val tabs = contents
            ?.optJSONObject("tabbedSearchResultsRenderer")
            ?.optJSONArray("tabs")

        if (tabs != null && tabs.length() > 0) {
            val tab = tabs.optJSONObject(0)
            val tabContent = tab?.optJSONObject("tabRenderer")
                ?.optJSONObject("content")
            val sections = tabContent?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents")

            if (sections != null) {
                val result = JSONArray()
                for (i in 0 until sections.length()) {
                    val section = sections.optJSONObject(i)
                    for (key in arrayOf("musicShelfRenderer", "musicCardShelfRenderer")) {
                        val shelf = section?.optJSONObject(key)
                        val items = shelf?.optJSONArray("contents")
                        if (items != null) {
                            for (j in 0 until items.length()) {
                                val item = items.optJSONObject(j)
                                    ?.optJSONObject("musicResponsiveListItemRenderer")
                                    ?: items.optJSONObject(j)
                                        ?.optJSONObject("musicTwoRowItemRenderer")
                                    ?: continue
                                result.put(item)
                            }
                        }
                    }
                }
                if (result.length() > 0) return result
            }
        }

        val riched = contents
            ?.optJSONObject("twoColumnSearchResultsRenderer")
            ?.optJSONObject("primaryContents")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents")
        if (riched != null) {
            val result = JSONArray()
            for (i in 0 until riched.length()) {
                val item = riched.optJSONObject(i)
                    ?.optJSONObject("itemSectionRenderer")
                    ?.optJSONArray("contents")
                    ?.optJSONObject(0)
                    ?.optJSONObject("videoRenderer")
                if (item != null) result.put(item)
            }
            if (result.length() > 0) return result
        }

        Timber.w("YTMusicRepo No known renderer found. Top keys: ${contents?.keys()?.asSequence()?.take(3)?.joinToString()}")
        return JSONArray()
    }

    private fun parseVideoId(item: JSONObject): String? {
        val runs: String? = item.optJSONObject("overlay")
            ?.optJSONObject("musicItemThumbnailOverlayRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("musicPlayButtonRenderer")
            ?.optJSONObject("playNavigationEndpoint")
            ?.optJSONObject("watchEndpoint")
            ?.optString("videoId", null)

        if (!runs.isNullOrBlank()) return runs

        val nav: String? = item.optJSONObject("doubleTapCommand")
            ?.optJSONObject("watchEndpoint")
            ?.optString("videoId", null)

        return nav
    }

    private fun parseTitle(item: JSONObject): String {
        val flexCols = item.optJSONArray("flexColumns")
        if (flexCols == null) {
            Timber.w("YTMusicRepo parseTitle: no flexColumns. Item keys: ${item.keys().asSequence().take(5).joinToString()}")
            return "Unknown Title"
        }
        val col = flexCols.optJSONObject(0)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?: return "Unknown Title"

        val runs = col.optJSONArray("runs")
        if (runs == null) {
            Timber.w("YTMusicRepo parseTitle: no runs. col keys: ${col.keys().asSequence().joinToString()}")
            return "Unknown Title"
        }
        val sb = StringBuilder()
        for (i in 0 until runs.length()) {
            sb.append(runs.optJSONObject(i)?.optString("text", "") ?: "")
        }
        return sb.toString().ifBlank { "Unknown Title" }
    }

    private fun parseArtist(item: JSONObject): String {
        val flexCols = item.optJSONArray("flexColumns") ?: return "Unknown Artist"
        val runs = flexCols.optJSONObject(1)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?.optJSONArray("runs")
            ?: return "Unknown Artist"

        // flexColumns[1] is a "Artist • Album • Duration" run list; every segment with a
        // browseEndpoint could be an artist OR an album (both are clickable), so we must check
        // pageType specifically rather than assume "any browseEndpoint = artist" (that previously
        // picked up album names too, e.g. "Eminem, The Eminem Show").
        val artists = mutableListOf<String>()
        for (i in 0 until runs.length()) {
            val run = runs.optJSONObject(i) ?: continue
            val text = run.optString("text", "")
            if (text.isBlank() || text == " • ") continue
            val pageType = run.optJSONObject("navigationEndpoint")
                ?.optJSONObject("browseEndpoint")
                ?.optJSONObject("browseEndpointContextSupportedConfigs")
                ?.optJSONObject("browseEndpointContextMusicConfig")
                ?.optString("pageType", "")
            if (pageType == "MUSIC_PAGE_TYPE_ARTIST") {
                artists.add(text)
            }
        }
        return if (artists.isEmpty()) "Unknown Artist" else artists.joinToString(", ")
    }

    private fun parseDuration(item: JSONObject): Long {
        val flexCols = item.optJSONArray("flexColumns") ?: return 0L
        val runs = flexCols.optJSONObject(1)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?.optJSONArray("runs")
            ?: return 0L

        // Duration is the LAST run of the "Artist • Album • Duration" column (flexColumns[2] is
        // play count, not duration, and column widths vary — e.g. no album for singles — so we
        // can't rely on a fixed index).
        val last = runs.optJSONObject(runs.length() - 1)?.optString("text", "") ?: return 0L
        if (!Regex("^\\d+(:\\d{2})+$").matches(last)) return 0L

        val parts = last.split(":")
        var total = 0L
        for (part in parts) {
            total = total * 60 + (part.toLongOrNull() ?: 0)
        }
        return total * 1000L
    }

    private fun parseAlbum(item: JSONObject): String {
        val flexCols = item.optJSONArray("flexColumns") ?: return "Unknown Album"
        val runs = flexCols.optJSONObject(1)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")
            ?.optJSONArray("runs")
            ?: return "Unknown Album"

        for (i in 0 until runs.length()) {
            val run = runs.optJSONObject(i) ?: continue
            val text = run.optString("text", "")
            if (text.isBlank() || text == " • ") continue
            val pageType = run.optJSONObject("navigationEndpoint")
                ?.optJSONObject("browseEndpoint")
                ?.optJSONObject("browseEndpointContextSupportedConfigs")
                ?.optJSONObject("browseEndpointContextMusicConfig")
                ?.optString("pageType", "")
            if (pageType == "MUSIC_PAGE_TYPE_ALBUM") {
                return text
            }
        }
        return "Unknown Album"
    }

    private fun parseThumbnail(item: JSONObject): String {
        val thumbnails = item.optJSONObject("thumbnail")
            ?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
            ?: return ""

        var best = ""
        var bestW = 0
        for (i in 0 until thumbnails.length()) {
            val t = thumbnails.optJSONObject(i) ?: continue
            val w = t.optInt("width", 0)
            if (w > bestW) {
                bestW = w
                best = t.optString("url", "")
            }
        }
        return best
    }

    private suspend fun fetchViaInvidious(videoId: String): String? {
        for (instance in invidiousInstances) {
            try {
                val raw = kotlinx.coroutines.withTimeoutOrNull(3000L) {
                    fastGet(URL("$instance/api/v1/videos/$videoId"))
                } ?: continue
                val json = JSONObject(raw)

                val formats = json.optJSONArray("adaptiveFormats")
                    ?: json.optJSONArray("formatStreams")
                    ?: continue

                for (j in 0 until formats.length()) {
                    val fmt = formats.getJSONObject(j)
                    val type = fmt.optString("type", fmt.optString("mimeType", ""))
                    if (!type.startsWith("audio/")) continue
                    val audioUrl = fmt.optString("url", null)
                    if (!audioUrl.isNullOrBlank()) {
                        Timber.d("YTMusicRepo Invidious $instance -> audio for $videoId")
                        return audioUrl
                    }
                }
            } catch (e: Exception) {
                Timber.w("YTMusicRepo Invidious $instance failed: ${e.message}")
            }
        }
        return null
    }

    private suspend fun tryPlayerContext(
        videoId: String,
        ctx: JSONObject,
        apiKey: String,
        userAgent: String,
        origin: String,
        usePoToken: Boolean = false
    ): String? {
        // WEB/WEB_REMIX playback is rejected by YouTube without a valid PoToken (BotGuard
        // attestation). Mint one via the WebView-based generator before asking for the stream;
        // ANDROID/IOS use DroidGuard/iosGuard instead, which we can't satisfy from a 3rd-party app.
        var playerRequestPoToken: String? = null
        var streamingDataPoToken: String? = null
        if (usePoToken) {
            val sessionId = visitorData
            if (!sessionId.isNullOrBlank()) {
                try {
                    val result = withContext(Dispatchers.IO) {
                        poTokenGenerator.getWebClientPoToken(videoId, sessionId)
                    }
                    playerRequestPoToken = result?.playerRequestPoToken
                    streamingDataPoToken = result?.streamingDataPoToken
                } catch (e: Exception) {
                    Timber.w("YTMusicRepo PoToken generation failed for $videoId: ${e.message}")
                }
            }
        }

        val body = JSONObject().apply {
            put("context", ctx)
            put("videoId", videoId)
            put("playbackContext", JSONObject().apply {
                put("contentPlaybackContext", JSONObject().apply {
                    put("html5Preference", "HTML5_PREF_WANTS")
                    if (usePoToken) {
                        CipherDeobfuscator.signatureTimestamp()?.let { put("signatureTimestamp", it) }
                    }
                })
            })
            put("contentCheckOk", true)
            put("racyCheckOk", true)
            playerRequestPoToken?.let { put("poToken", it) }
        }
        val raw = post("player", body, apiKey, userAgent, origin) ?: return null
        val json = JSONObject(raw)
        updateVisitorData(json)

        val playability = json.optJSONObject("playabilityStatus")
        val status = playability?.optString("status", "OK")
        if (status != "OK") {
            Timber.w("YTMusicRepo InnerTube player $videoId: $status - ${playability?.optString("reason")}")
            return null
        }

        val formats = json.optJSONObject("streamingData")
            ?.optJSONArray("adaptiveFormats")
            ?: return null

        for (j in 0 until formats.length()) {
            val fmt = formats.getJSONObject(j)
            val mime = fmt.optString("mimeType", "")
            if (!mime.startsWith("audio/")) continue
            val url = extractAudioUrl(fmt, videoId) ?: continue
            return if (!streamingDataPoToken.isNullOrBlank()) "$url&pot=$streamingDataPoToken" else url
        }
        return null
    }

    private suspend fun fetchViaInnerTube(videoId: String): String? {
        // WEB_REMIX + real PoToken is now the primary path: it's the only client whose
        // attestation (BotGuard, via zemer-cipher's WebView) we can actually satisfy.
        tryPlayerContext(
            videoId, clientContext(), WEB_KEY, UA_WEB, "https://music.youtube.com", usePoToken = true
        )?.let { return it }

        val webContext = JSONObject().apply {
            put("client", JSONObject().apply {
                put("clientName", "WEB")
                put("clientVersion", "2.20250610.01.00")
                put("hl", "en")
                put("gl", "ID")
                visitorData?.let { put("visitorData", it) }
            })
        }
        tryPlayerContext(
            videoId, webContext, WEB_KEY, UA_WEB, "https://www.youtube.com", usePoToken = true
        )?.let { return it }

        // Legacy fallbacks kept for cases where BotGuard/WebView is unavailable on-device
        // (e.g. WebView disabled) — these rarely succeed anymore without PoToken.
        val androidContext = JSONObject().apply {
            put("client", JSONObject().apply {
                put("clientName", "ANDROID")
                put("clientVersion", "19.47.53")
                put("androidSdkVersion", 34)
                put("osName", "Android")
                put("osVersion", "14")
                put("platform", "MOBILE")
                put("hl", "en")
                put("gl", "ID")
            })
        }
        tryPlayerContext(videoId, androidContext, ANDROID_KEY, UA_ANDROID, "https://www.youtube.com")?.let { return it }

        val iosContext = JSONObject().apply {
            put("client", JSONObject().apply {
                put("clientName", "IOS")
                put("clientVersion", "19.47.53")
                put("osName", "iOS")
                put("osVersion", "17.5.1.21F90")
                put("platform", "MOBILE")
                put("hl", "en")
                put("gl", "ID")
            })
        }
        tryPlayerContext(videoId, iosContext, ANDROID_KEY, UA_ANDROID, "https://www.youtube.com")?.let { return it }

        return null
    }

    suspend fun searchSongs(query: String, onPartial: (List<Song>) -> Unit): StreamSearchResult =
        withContext(Dispatchers.IO) {
            val musicResult = searchMusicSongsSafe(query, onPartial)
            if (musicResult is StreamSearchResult.Success) return@withContext musicResult

            val regularResult = searchRegularSongsSafe(query, onPartial)
            if (regularResult is StreamSearchResult.Success) return@withContext regularResult

            // Kedua jalur gagal. Kalau SALAH SATU dari keduanya nunjukin ParsingFailed (bukan
            // cuma Empty), berarti kemungkinan besar struktur JSON YouTube berubah, bukan sekadar
            // query yang gak ketemu apa-apa — user berhak tau bedanya.
            if (musicResult is StreamSearchResult.ParsingFailed || regularResult is StreamSearchResult.ParsingFailed) {
                StreamSearchResult.ParsingFailed
            } else {
                StreamSearchResult.Empty
            }
        }

    // Wrapper aman di sekitar fungsi lama, supaya walkMusicContents() dkk gak perlu diubek-ubek --
// cukup diperiksa exception & sinyal "0 item ditemukan padahal request sukses" dari sini.
    private suspend fun searchMusicSongsSafe(
        query: String,
        onPartial: (List<Song>) -> Unit
    ): StreamSearchResult = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("context", clientContext())
                put("query", query)
                put("params", "EgWKAQIIAWoKEAMQBBAFEAoQCQ==")
            }
            val raw = post("search", body)
            if (raw == null) {
                Timber.e("YTMusicRepo InnerTube search POST returned null")
                return@withContext StreamSearchResult.ParsingFailed
            }

            val response = JSONObject(raw)
            updateVisitorData(response)

            // Kalau top-level "contents" sendiri gak ada, itu jelas bukan "hasil kosong" biasa --
            // response-nya berubah bentuk total.
            if (!response.has("contents")) {
                Timber.w("YTMusicRepo Response missing 'contents' key entirely - structure likely changed")
                return@withContext StreamSearchResult.ParsingFailed
            }

            val items = walkMusicContents(response)
            val limit = minOf(items.length(), 50)
            if (limit == 0) {
                // "contents" ADA tapi walkMusicContents() gak nemu satupun renderer yang dikenal
                // (musicShelfRenderer/musicCardShelfRenderer/dst) -> kemungkinan besar YouTube
                // ganti nama/skema renderer-nya, bukan sekadar "gak ada lagu yang cocok".
                Timber.w("YTMusicRepo Zero items parsed from non-empty contents - possible structure change")
                return@withContext StreamSearchResult.ParsingFailed
            }

            val results = mutableListOf<Song>()
            for (i in 0 until limit) {
                try {
                    val item = items.getJSONObject(i)
                    val videoId = parseVideoId(item) ?: continue
                    val song = Song(
                        id = videoId.hashCode().toLong(),
                        title = parseTitle(item),
                        artist = parseArtist(item),
                        album = parseAlbum(item),
                        duration = parseDuration(item),
                        uri = Uri.parse("ytmusic://placeholder/$videoId"),
                        isStream = true,
                        remoteArtUrl = parseThumbnail(item),
                        remoteId = videoId
                    )
                    results.add(song)
                    onPartial(listOf(song))
                } catch (e: Exception) {
                    Timber.w("YTMusicRepo Parse item failed: ${e.message}")
                }
            }

            // Kalau limit>0 (ada item mentah) tapi SEMUA parsing per-item gagal (results kosong),
            // itu juga sinyal kuat field internal item berubah struktur.
            if (results.isEmpty()) StreamSearchResult.ParsingFailed else StreamSearchResult.Success(results)
        } catch (e: Exception) {
            Timber.e(e, "YTMusicRepo Search gagal: ${e.message}")
            StreamSearchResult.ParsingFailed
        }
    }

    private suspend fun searchRegularSongsSafe(
        query: String,
        onPartial: (List<Song>) -> Unit
    ): StreamSearchResult = withContext(Dispatchers.IO) {
        try {
            val webSearchContext = JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "WEB")
                    put("clientVersion", "2.20250610.01.00")
                    put("hl", "en")
                    put("gl", "ID")
                })
            }
            val body = JSONObject().apply {
                put("context", webSearchContext)
                put("query", query)
            }
            val raw = post("search", body)
            if (raw == null) return@withContext StreamSearchResult.ParsingFailed

            val response = JSONObject(raw)
            updateVisitorData(response)
            if (!response.has("contents")) return@withContext StreamSearchResult.ParsingFailed

            val items = walkVideoContents(response)
            val limit = minOf(items.length(), 20)
            if (limit == 0) return@withContext StreamSearchResult.ParsingFailed

            val results = mutableListOf<Song>()
            for (i in 0 until limit) {
                try {
                    val item = items.getJSONObject(i)
                    val videoId = item.optString("videoId", null) ?: continue
                    val song = Song(
                        id = videoId.hashCode().toLong(),
                        title = parseVideoTitle(item),
                        artist = parseVideoArtist(item),
                        album = "YouTube",
                        duration = parseVideoDuration(item),
                        uri = Uri.parse("ytmusic://placeholder/$videoId"),
                        isStream = true,
                        remoteArtUrl = parseVideoThumbnail(item),
                        remoteId = videoId
                    )
                    results.add(song)
                    onPartial(listOf(song))
                } catch (e: Exception) {
                    Timber.w("YTMusicRepo Parse regular item failed: ${e.message}")
                }
            }

            if (results.isEmpty()) StreamSearchResult.ParsingFailed else StreamSearchResult.Success(results)
        } catch (e: Exception) {
            Timber.e(e, "YTMusicRepo Regular YT search failed: ${e.message}")
            StreamSearchResult.ParsingFailed
        }
    }

    private fun walkVideoContents(json: JSONObject): JSONArray {
        val contents = json.optJSONObject("contents")
            ?.optJSONObject("twoColumnSearchResultsRenderer")
            ?.optJSONObject("primaryContents")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents")
            ?: return JSONArray()

        val result = JSONArray()
        for (i in 0 until contents.length()) {
            val section = contents.optJSONObject(i)
                ?.optJSONObject("itemSectionRenderer")
                ?.optJSONArray("contents")
                ?: continue
            for (j in 0 until section.length()) {
                val video = section.optJSONObject(j)?.optJSONObject("videoRenderer")
                if (video != null) result.put(video)
            }
        }
        return result
    }

    private fun parseVideoTitle(item: JSONObject): String {
        return item.optJSONObject("title")
            ?.optJSONArray("runs")
            ?.optJSONObject(0)
            ?.optString("text", "Unknown Title")
            ?: "Unknown Title"
    }

    private fun parseVideoArtist(item: JSONObject): String {
        return item.optJSONObject("ownerText")
            ?.optJSONArray("runs")
            ?.optJSONObject(0)
            ?.optString("text", "Unknown Artist")
            ?: "Unknown Artist"
    }

    private fun parseVideoDuration(item: JSONObject): Long {
        val text = item.optJSONObject("lengthText")
            ?.optString("simpleText", "0:00")
            ?: "0:00"
        val parts = text.split(":")
        var total = 0L
        for (part in parts) {
            total = total * 60 + (part.toLongOrNull() ?: 0)
        }
        return total * 1000L
    }

    private fun parseVideoThumbnail(item: JSONObject): String {
        val thumbnails = item.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
            ?: return ""
        var best = ""
        var bestW = 0
        for (i in 0 until thumbnails.length()) {
            val t = thumbnails.optJSONObject(i) ?: continue
            val w = t.optInt("width", 0)
            if (w > bestW) {
                bestW = w
                best = t.optString("url", "")
            }
        }
        return best
    }

    suspend fun getAudioFormats(videoId: String): List<AudioFormat> = withContext(Dispatchers.IO) {
        val formats = mutableListOf<AudioFormat>()
        
        val sessionId = visitorData
        if (sessionId.isNullOrBlank()) return@withContext emptyList()

        var playerRequestPoToken: String? = null
        var streamingDataPoToken: String? = null
        try {
            val result = withContext(Dispatchers.IO) {
                poTokenGenerator.getWebClientPoToken(videoId, sessionId)
            }
            playerRequestPoToken = result?.playerRequestPoToken
            streamingDataPoToken = result?.streamingDataPoToken
        } catch (_: Exception) {}

        val body = JSONObject().apply {
            put("context", clientContext())
            put("videoId", videoId)
            put("playbackContext", JSONObject().apply {
                put("contentPlaybackContext", JSONObject().apply {
                    put("html5Preference", "HTML5_PREF_WANTS")
                    CipherDeobfuscator.signatureTimestamp()?.let { put("signatureTimestamp", it) }
                })
            })
            put("contentCheckOk", true)
            put("racyCheckOk", true)
            playerRequestPoToken?.let { put("poToken", it) }
        }
        val raw = post("player", body) ?: return@withContext emptyList()
        val json = JSONObject(raw)
        updateVisitorData(json)

        val playability = json.optJSONObject("playabilityStatus")
        val status = playability?.optString("status", "OK")
        if (status != "OK") return@withContext emptyList()

        val adaptiveFormats = json.optJSONObject("streamingData")?.optJSONArray("adaptiveFormats") ?: return@withContext emptyList()
        
        for (i in 0 until adaptiveFormats.length()) {
            val fmt = adaptiveFormats.getJSONObject(i)
            val mime = fmt.optString("mimeType", "")
            if (!mime.startsWith("audio/")) continue
            
            val url = extractAudioUrl(fmt, videoId) ?: continue
            val finalUrl = if (!streamingDataPoToken.isNullOrBlank()) "$url&pot=$streamingDataPoToken" else url
            
            val bitrate = fmt.optInt("bitrate", 0)
            val label = when {
                bitrate >= 250000 -> "High (256kbps)"
                bitrate >= 128000 -> "Medium (128kbps)"
                else -> "Low (${bitrate / 1000}kbps)"
            }
            
            formats.add(AudioFormat(
                url = finalUrl,
                mimeType = mime,
                bitrate = bitrate,
                qualityLabel = label,
                sizeBytes = fmt.optLong("contentLength", 0L)
            ))
        }
        
        formats.sortedByDescending { it.bitrate }
    }

    /**
     * Fetch actual stream URL for a song with placeholder URI.
     * Called on-demand when song is about to be played.
     */
    suspend fun resolveStreamUrl(song: Song): String? = withContext(Dispatchers.IO) {
        val videoId = song.remoteId ?: return@withContext null

        if (!song.uri.toString().startsWith("ytmusic://placeholder/")) {
            // Already has real URL
            return@withContext song.uri.toString()
        }

        // 1. In-memory cache dulu (tercepat, hidup selama proses app masih jalan)
        streamUrlCache[videoId]?.let {
            Timber.d("YTMusicRepo Memory cache hit for: ${song.title}")
            return@withContext it
        }

        // 2. Room DB (persist antar sesi/app-kill), cek masih valid apa udah expired
        try {
            val cached = streamCacheDao.getById(videoId)
            if (cached != null && cached.expiresAtMs > System.currentTimeMillis()) {
                Timber.d("YTMusicRepo DB cache hit for: ${song.title}")
                streamUrlCache[videoId] = cached.url
                return@withContext cached.url
            }
        } catch (e: Exception) {
            Timber.w("YTMusicRepo Cache read failed: ${e.message}")
        }

        Timber.d("YTMusicRepo Resolving stream URL for: ${song.title}")
        try {
            playerSemaphore.acquire()
            var audioUrl = fetchViaInvidious(videoId)
            if (audioUrl == null) {
                audioUrl = fetchViaInnerTube(videoId)
            }
            if (audioUrl != null) {
                streamUrlCache[videoId] = audioUrl
                try {
                    val now = System.currentTimeMillis()
                    streamCacheDao.insert(
                        CachedStreamUrl(
                            videoId = videoId,
                            url = audioUrl,
                            resolvedAtMs = now,
                            expiresAtMs = computeExpiryMs(audioUrl, now)
                        )
                    )
                } catch (e: Exception) {
                    Timber.w("YTMusicRepo Cache write failed: ${e.message}")
                }
                Timber.d("YTMusicRepo Resolved stream: ${song.title} - ${song.artist}")
            } else {
                Timber.w("YTMusicRepo Failed to resolve stream for: ${song.title}")
            }
            return@withContext audioUrl
        } finally {
            playerSemaphore.release()
        }
    }
    /**
     * Fetch trending/popular songs using search API (reliable since browse
     * endpoint often requires auth). Searches for trending music queries and
     * merges results.
     */
    suspend fun fetchHomeSongs(onPartial: (List<Song>) -> Unit): StreamSearchResult =
        withContext(Dispatchers.IO) {
            val queries = listOf("trending music", "viral hits", "top songs")
            val seenIds = mutableSetOf<Long>()
            val allResults = mutableListOf<Song>()

            for (query in queries) {
                try {
                    val body = JSONObject().apply {
                        put("context", clientContext())
                        put("query", query)
                        put("params", "EgWKAQIIAWoKEAMQBBAFEAoQCQ==")
                    }
                    val raw = post("search", body) ?: continue
                    val response = JSONObject(raw)
                    updateVisitorData(response)
                    if (!response.has("contents")) continue

                    val items = walkMusicContents(response)
                    val limit = minOf(items.length(), 15)
                    for (i in 0 until limit) {
                        try {
                            val item = items.getJSONObject(i)
                            val videoId = parseVideoId(item) ?: continue
                            val id = videoId.hashCode().toLong()
                            if (id in seenIds) continue
                            seenIds.add(id)
                            val song = Song(
                                id = id,
                                title = parseTitle(item),
                                artist = parseArtist(item),
                                album = parseAlbum(item),
                                duration = parseDuration(item),
                                uri = Uri.parse("ytmusic://placeholder/$videoId"),
                                isStream = true,
                                remoteArtUrl = parseThumbnail(item),
                                remoteId = videoId
                            )
                            allResults.add(song)
                            onPartial(listOf(song))
                        } catch (_: Exception) {}
                    }
                } catch (_: Exception) {}
            }

            if (allResults.isEmpty()) StreamSearchResult.Empty else StreamSearchResult.Success(allResults)
        }

    suspend fun cleanupExpiredCache() = withContext(Dispatchers.IO) {
        streamCacheDao.deleteExpired(System.currentTimeMillis())
    }
}
