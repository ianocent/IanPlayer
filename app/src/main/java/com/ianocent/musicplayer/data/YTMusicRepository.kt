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
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import android.content.Context

class YTMusicRepository(context: Context) {

    private val playerSemaphore = Semaphore(9)

    private val streamCacheDao = AppDatabase.getInstance(context).streamCacheDao()

    // In-memory cache tetap dipertahankan sebagai layer tercepat (hindari hit DB
    // berkali-kali dalam 1 sesi aktif); Room jadi layer kedua yang persist antar
    // sesi app (survive app-kill), yang gak bisa dicover LinkedHashMap doang.
    // Wrapped ConcurrentHashMap untuk thread-safety dari multiple coroutine.
    private val streamUrlCache: MutableMap<String, String> = Collections.synchronizedMap(
        object : LinkedHashMap<String, String>(50, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
                return size > 50
            }
        }
    )

    // Resolved video streams for video mode (session-scoped; not persisted,
    // since these expire like the audio ones and are only needed while visible).
    private val videoStreamCache: MutableMap<String, VideoStream> = Collections.synchronizedMap(
        object : LinkedHashMap<String, VideoStream>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, VideoStream>?): Boolean {
                return size > 16
            }
        }
    )

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

    /**
     * True when a googlevideo URL's `expire` epoch is still in the future (with a
     * small safety margin). URLs without `expire` (Invidious fallbacks) are
     * assumed fresh. Signed URLs that have expired return 403 — a stale cache hit
     * used to be handed straight to the player, whose error path skips to the
     * next track ("lagu pindah-pindah" while toggling music/video mode).
     */
    private fun isUrlFresh(url: String): Boolean {
        val match = Regex("[?&]expire=(\\d+)").find(url) ?: return true
        val expireEpochSeconds = match.groupValues[1].toLongOrNull() ?: return true
        return expireEpochSeconds * 1000L > System.currentTimeMillis() + 60_000L
    }

    companion object {
        // Hard chart/playlist phrases: killed even when the uploader channel repeats them
        // (e.g. artist = "Billboard Top 100 Hits").
        // Hard chart/playlist phrases: killed even when the uploader channel repeats them
        // (e.g. artist = "Billboard Top 100 Hits").
        private val COMPILATION_PHRASE = Regex("\\b(Billboard|Top \\d+ (Hits|Songs|Tracks)?|Top 40|Greatest Hits|Best Of|Nonstop|Compilation|Masters|Hit Maker|Hit Co|Originally Performed By|Exitos|Internacionales|Del Momento|Le Meilleur|Music To|Music For|Relaxing Music|Background Music|Sleep Music|Workout Music|Study Music)\\b", RegexOption.IGNORE_CASE)
        // Non-original versions only. Deliberately narrow: over-filtering here was
        // hiding legitimate songs (covers, remixes, live cuts, and entire regions
        // are real music users search for), so only clearly not-the-real-track
        // markers are killed.
        private val VERSION_MARKER = Regex("\\b(karaoke|instrumental|tribute|originally performed by|made famous by|in the style of|hit maker|the karaoke)\\b", RegexOption.IGNORE_CASE)

        // Client identifiers from BuildConfig (keys.properties, gitignored).
        private val WEB_KEY = com.ianocent.musicplayer.BuildConfig.YTMUSIC_WEB_KEY
        private val ANDROID_KEY = com.ianocent.musicplayer.BuildConfig.YTMUSIC_ANDROID_KEY
        private const val BASE = "https://www.youtube.com/youtubei/v1"
        private const val UA_WEB = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
        private const val UA_ANDROID = "com.google.android.youtube/19.47.53 (Linux; U; Android 14; en_US) gzip"
        private const val UA_TV = "Mozilla/5.0 (SMART-TV; Linux; Tizen 2.4.0) AppleWebKit/538.1 (KHTML, like Gecko) Version/2.4 Chrome/79.0.3945.130 TV Safari/538.1"

        // TV_EMBEDDED often returns 256kbps opus without PoToken requirement
        private val TV_KEY = WEB_KEY

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

    // gl/hl override: ensures results come from requested region or fallback to US/ID.
    // Explicitly avoids 'IN' (India) to prevent Bollywood leaks in global feeds.
    private fun clientContext(gl: String? = null, hl: String? = null): JSONObject = JSONObject().apply {
        val locale = java.util.Locale.getDefault()
        val country = gl ?: locale.country.ifBlank { "US" }
        // Guard: If detected country is India (IN), force it to US or ID based on language
        val safeGl = if (country.equals("IN", true)) {
            if (locale.language.equals("in", true) || locale.language.equals("id", true)) "ID" else "US"
        } else country

        put("client", JSONObject().apply {
            put("clientName", "WEB_REMIX")
            put("clientVersion", "1.20260701.01.00")
            put("hl", hl ?: locale.language)
            put("gl", safeGl)
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
        // Primary path: flexColumns[1] runs with MUSIC_PAGE_TYPE_ARTIST
        val flexCols = item.optJSONArray("flexColumns")
        if (flexCols != null) {
            val runs = flexCols.optJSONObject(1)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")
                ?.optJSONArray("runs")
            if (runs != null) {
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
                if (artists.isNotEmpty()) return artists.joinToString(", ")
            }

            // Fallback: try flexColumns[0] (some responses put artist in first column)
            val runs0 = flexCols.optJSONObject(0)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")
                ?.optJSONArray("runs")
            if (runs0 != null) {
                for (i in 0 until runs0.length()) {
                    val run = runs0.optJSONObject(i) ?: continue
                    val pageType = run.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("browseEndpoint")
                        ?.optJSONObject("browseEndpointContextSupportedConfigs")
                        ?.optJSONObject("browseEndpointContextMusicConfig")
                        ?.optString("pageType", "")
                    if (pageType == "MUSIC_PAGE_TYPE_ARTIST") {
                        val text = run.optString("text", "")
                        if (text.isNotBlank() && text != " • ") return text
                    }
                }
            }
        }

        // Fallback: byline text (video-type items mixed into search)
        val byline = item.optJSONObject("longBylineText")
            ?: item.optJSONObject("shortBylineText")
        val bylineArtist = byline?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
        if (!bylineArtist.isNullOrBlank() && bylineArtist != "Unknown Artist") return bylineArtist

// Last resort: extract from "Song - Artist" video titles
        val titleText = item.optJSONObject("title")
            ?.optJSONArray("runs")
            ?.optJSONObject(0)
            ?.optString("text", "")
            ?: ""
        artistFromCombinedTitle(titleText)?.let { return it }

        return "Unknown Artist"
    }

/**
     * Attempts to extract an artist from a combined video title. Re-uploaded
     * videos are titled "Song - Artist" ("Without Me - Eminem"), so the RIGHT
     * side is the artist. Only trusted when it looks like a name: 2..40
     * chars, no parentheses, no digits.
     */
    private fun artistFromCombinedTitle(title: String): String? {
        val dashIndex = title.indexOf(" - ")
        if (dashIndex <= 0) return null
        val candidate = title.substring(dashIndex + 3).trim()
        if (candidate.isBlank() || candidate.length > 40 ||
            candidate.contains('(') || candidate.any { it.isDigit() })
        {
            return null
        }
        return candidate
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

        return durationTextToMs(last)
    }

    /** Parses "h:mm:ss" / "m:ss" duration text into milliseconds. */
    private fun durationTextToMs(text: String): Long {
        var total = 0L
        for (part in text.split(":")) {
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

    /**
     * POSTs a `player` request for one client and returns the parsed response
     * together with the streaming-data PoToken (if one was minted), or null when
     * the request/playability check failed.
     */
    private suspend fun postPlayer(
        videoId: String,
        ctx: JSONObject,
        apiKey: String,
        userAgent: String,
        origin: String,
        usePoToken: Boolean = false
    ): Pair<JSONObject, String?>? {
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
        return json to streamingDataPoToken
    }

    private suspend fun tryPlayerContext(
        videoId: String,
        ctx: JSONObject,
        apiKey: String,
        userAgent: String,
        origin: String,
        usePoToken: Boolean = false,
        selector: (JSONObject) -> JSONObject? = ::pickBestAudioFormat
    ): String? {
        val (json, streamingDataPoToken) =
            postPlayer(videoId, ctx, apiKey, userAgent, origin, usePoToken) ?: return null
        val format = selector(json) ?: return null
        val url = extractAudioUrl(format, videoId) ?: return null
        return appendPoToken(url, streamingDataPoToken)
    }

    private fun appendPoToken(url: String, poToken: String?): String =
        if (!poToken.isNullOrBlank()) "$url&pot=$poToken" else url

    /** A candidate player client (context, key, UA, origin, PoToken need) for stream resolution. */
    private data class PlayerClient(
        val ctx: JSONObject,
        val key: String,
        val userAgent: String,
        val origin: String,
        val usePoToken: Boolean
    )

    /** The ordered client fallbacks tried when resolving a stream. */
    private fun playerClients(): List<PlayerClient> {
        val tvContext = JSONObject().apply {
            put("client", JSONObject().apply {
                put("clientName", "TV_EMBEDDED")
                put("clientVersion", "2.0")
                put("hl", "en")
                put("gl", "US")
                visitorData?.let { put("visitorData", it) }
            })
        }
        val androidMusicContext = JSONObject().apply {
            put("client", JSONObject().apply {
                put("clientName", "ANDROID_MUSIC")
                put("clientVersion", "5.03.51")
                put("androidSdkVersion", 34)
                put("osName", "Android")
                put("osVersion", "14")
                put("platform", "MOBILE")
                put("hl", "en")
                put("gl", "US")
                visitorData?.let { put("visitorData", it) }
            })
        }
        val webContext = JSONObject().apply {
            put("client", JSONObject().apply {
                put("clientName", "WEB")
                put("clientVersion", "2.20250610.01.00")
                put("hl", "en")
                put("gl", "ID")
                visitorData?.let { put("visitorData", it) }
            })
        }
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
        return listOf(
            PlayerClient(clientContext(), WEB_KEY, UA_WEB, "https://music.youtube.com", true),
            PlayerClient(tvContext, TV_KEY, UA_TV, "https://www.youtube.com", false),
            PlayerClient(androidMusicContext, ANDROID_KEY, UA_ANDROID, "https://music.youtube.com", true),
            PlayerClient(webContext, WEB_KEY, UA_WEB, "https://www.youtube.com", true),
            PlayerClient(androidContext, ANDROID_KEY, UA_ANDROID, "https://www.youtube.com", false),
            PlayerClient(iosContext, ANDROID_KEY, UA_ANDROID, "https://www.youtube.com", false)
        )
    }

    /** Best adaptive audio format: opus > mp4a, higher bitrate, quality-label boost. */
    private fun pickBestAudioFormat(json: JSONObject): JSONObject? {
        val formats = json.optJSONObject("streamingData")
            ?.optJSONArray("adaptiveFormats")
            ?: return null
        return (0 until formats.length())
            .mapNotNull { i ->
                val fmt = formats.getJSONObject(i)
                val mime = fmt.optString("mimeType", "")
                if (!mime.startsWith("audio/")) return@mapNotNull null
                val bitrate = fmt.optInt("bitrate", 0)
                val audioQuality = fmt.optString("audioQuality", "AUDIO_QUALITY_LOW")
                val isOpus = mime.contains("opus")
                val qualityScore = when {
                    isOpus && bitrate >= 250000 -> 4000 + bitrate
                    isOpus -> 3000 + bitrate
                    bitrate >= 250000 -> 2000 + bitrate
                    else -> bitrate
                }
                // Boost for HIGH/MEDIUM quality labels
                val qualityBoost = when (audioQuality) {
                    "AUDIO_QUALITY_HIGH" -> 500
                    "AUDIO_QUALITY_MEDIUM" -> 200
                    else -> 0
                }
                fmt to (qualityScore + qualityBoost)
            }
            .maxByOrNull { it.second }
            ?.first
    }

    /**
     * Best progressive (muxed audio+video) format. Progressive streams carry
     * both tracks in one file, so ExoPlayer can render video without a
     * MergingMediaSource; YouTube tops these out around 720p.
     */
    private fun pickBestMuxedVideoFormat(json: JSONObject): JSONObject? {
        val formats = json.optJSONObject("streamingData")?.optJSONArray("formats") ?: return null
        var best: JSONObject? = null
        var bestScore = -1
        for (i in 0 until formats.length()) {
            val fmt = formats.optJSONObject(i) ?: continue
            val mime = fmt.optString("mimeType", "")
            if (!mime.startsWith("video/")) continue
            val height = fmt.optInt("height", 0)
            val bitrate = fmt.optInt("bitrate", 0)
            val score = height * 100_000 + bitrate.coerceAtMost(99_999)
            if (score > bestScore) {
                bestScore = score
                best = fmt
            }
        }
        return best
    }

    /**
     * Best video-only adaptive format (no audio track of its own), capped at
     * 720p to bound bandwidth. Paired with [pickBestAudioFormat] and merged by
     * the player, this is what modern YouTube videos expose.
     */
    private fun pickBestVideoOnlyFormat(json: JSONObject): JSONObject? {
        val formats = json.optJSONObject("streamingData")?.optJSONArray("adaptiveFormats") ?: return null
        var best: JSONObject? = null
        var bestScore = -1
        for (i in 0 until formats.length()) {
            val fmt = formats.optJSONObject(i) ?: continue
            val mime = fmt.optString("mimeType", "")
            if (!mime.startsWith("video/")) continue
            val height = fmt.optInt("height", 0)
            if (height <= 0 || height > 720) continue
            val bitrate = fmt.optInt("bitrate", 0)
            val score = height * 100_000 + bitrate.coerceAtMost(99_999)
            if (score > bestScore) {
                bestScore = score
                best = fmt
            }
        }
        return best
    }

    /** Turns one player response into a [VideoStream], preferring progressive then adaptive-pair. */
    private suspend fun parseVideoStream(json: JSONObject, videoId: String, poToken: String?): VideoStream? {
        pickBestMuxedVideoFormat(json)?.let { progressive ->
            extractAudioUrl(progressive, videoId)?.let {
                return VideoStream(appendPoToken(it, poToken), null)
            }
        }
        val videoFormat = pickBestVideoOnlyFormat(json) ?: return null
        val videoUrl = extractAudioUrl(videoFormat, videoId) ?: return null
        val audioUrl = pickBestAudioFormat(json)?.let { extractAudioUrl(it, videoId) }
        return VideoStream(appendPoToken(videoUrl, poToken), audioUrl?.let { appendPoToken(it, poToken) })
    }

    /** Resolves a playable video (progressive or video+audio pair) for [videoId]. */
    private suspend fun fetchVideoStream(videoId: String): VideoStream? {
        for (client in playerClients()) {
            val (json, poToken) = postPlayer(
                videoId, client.ctx, client.key, client.userAgent, client.origin,
                usePoToken = client.usePoToken
            ) ?: continue
            parseVideoStream(json, videoId, poToken)?.let { return it }
        }
        return null
    }

    private suspend fun fetchViaInnerTube(
        videoId: String,
        selector: (JSONObject) -> JSONObject? = ::pickBestAudioFormat
    ): String? {
        for (client in playerClients()) {
            tryPlayerContext(
                videoId, client.ctx, client.key, client.userAgent, client.origin,
                usePoToken = client.usePoToken, selector = selector
            )?.let { return it }
        }
        return null
    }

    suspend fun searchSongs(
        query: String,
        onPartial: (List<Song>) -> Unit,
        gl: String? = null,
        hl: String? = null
    ): StreamSearchResult =
        withContext(Dispatchers.IO) {
            // Songs catalogue first, then uploads/videos. Many tracks — and mixes /
            // DJ sets / re-uploads — exist only as videos, never in the YouTube Music
            // songs catalogue, so a songs-only search silently misses them. Both
            // paths stream their results through onPartial so the UI fills in live.
            val musicResult = searchMusicSongsSafe(query, onPartial, gl, hl)
            val regularResult = searchRegularSongsSafe(query, onPartial, gl)

            val merged = (
                (musicResult as? StreamSearchResult.Success)?.songs.orEmpty() +
                    (regularResult as? StreamSearchResult.Success)?.songs.orEmpty()
                ).distinctBy { it.id }

            when {
                merged.isNotEmpty() -> StreamSearchResult.Success(merged)
                // Both paths empty. If either even failed to parse, the response shape
                // probably changed — the user deserves to know it's not just "no matches".
                musicResult is StreamSearchResult.ParsingFailed ||
                    regularResult is StreamSearchResult.ParsingFailed -> StreamSearchResult.ParsingFailed
                else -> StreamSearchResult.Empty
            }
        }

    // Wrapper aman di sekitar fungsi lama, supaya walkMusicContents() dkk gak perlu diubek-ubek --
// cukup diperiksa exception & sinyal "0 item ditemukan padahal request sukses" dari sini.
    private suspend fun searchMusicSongsSafe(
        query: String,
        onPartial: (List<Song>) -> Unit,
        gl: String? = null,
        hl: String? = null
    ): StreamSearchResult = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("context", clientContext(gl, hl))
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
            val limit = minOf(items.length(), 100)
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
                    val title = parseTitle(item)
                    val artist = parseArtist(item)
                    val duration = parseDuration(item)

                    // Strict filter to exclude compilations, playlists masquerading as songs, and long mixes
                    if (duration > 15 * 60 * 1000L || isCompilation(title, artist) ||
                        title.contains("full album", ignoreCase = true)) continue

                    val song = Song(
                        id = videoId.hashCode().toLong(),
                        title = title,
                        artist = parseArtist(item),
                        album = parseAlbum(item),
                        duration = duration,
                        uri = StreamSources.placeholderUri(videoId),
                        source = StreamSources.YOUTUBE,
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
        onPartial: (List<Song>) -> Unit,
        gl: String? = null
    ): StreamSearchResult = withContext(Dispatchers.IO) {
        try {
            val webSearchContext = JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "WEB")
                    put("clientVersion", "2.20250610.01.00")
                    put("hl", "en")
                    put("gl", gl ?: "US")
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
            val limit = minOf(items.length(), 50)
            if (limit == 0) return@withContext StreamSearchResult.ParsingFailed

            val results = mutableListOf<Song>()
            for (i in 0 until limit) {
                try {
                    val item = items.getJSONObject(i)
                    val videoId = item.optString("videoId", null) ?: continue
                    val title = parseVideoTitle(item)
                    val duration = parseVideoDuration(item)

                    // Filter out non-song content. Videos/mixes can legitimately run long,
                    // so a generous cap (3h) only drops true marathons/24-7 streams.
                    val artist = parseVideoArtist(item)
                    if (duration > 3 * 60 * 60 * 1000L || isCompilation(title, artist)) continue

                    val song = Song(
                        id = videoId.hashCode().toLong(),
                        title = title,
                        artist = artist,
                        album = "YouTube",
                        duration = duration,
                        uri = StreamSources.placeholderUri(videoId),
                        source = StreamSources.YOUTUBE,
                        isStream = true,
                        remoteArtUrl = parseVideoThumbnail(item),
                        remoteId = videoId,
                        isVideo = true
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

    private val INDIAN_KEYWORD_REGEX = Regex(
        "\\b(t-series|tseries|zee music|speed records|yrf music|yrf|sony music india|saregama|tips official|desi music|bollywood|punjabi|hindi|tamil|telugu|bhojpuri|marathi|gujarati|kannada|malayalam|bhangra|garba|qawwali|bhajan|kirtan|mantra|aarti|ghazal|tollywood|kollywood|sandalwood|mollywood|lofi hindi|sad hindi|bollywood lofi|lofi bollywood|punjabi lofi|slowed reverb hindi|desibeats|desihits|indian music|indian pop|indian song|indian songs|badshah|arijit|diljit|neha kakkar|yo yo honey singh|jubin nautiyal|shreya ghoshal|lata mangeshkar|kishore kumar|alka yagnik|udit narayan|kumar sanu|sonu nigam|a.r. rahman|ar rahman|anirudh|pritam|vishal shekhar|mithoon|sachin jigar|guru randhawa|jassie gill|hardy sandhu|sidhu moose wala|karan aujla|amrit maan|parmish verma|garry sandhu|mankirt aulakh|jass manak|tony kakkar|tulsi kumar|dhvani bhanushali|payal dev|asees kaur|jasleen royal|palak muchhal|kanika kapoor|neeti mohan|sunidhi chauhan|monali thakur|shalmali kholgade|shirley setia|jonita gandhi|stebin ben|darshan raval|arman malik|amal mallik|zack knight|divine|raftaar|emiway|mc stan|king|srishti bhandari)\\b",
        RegexOption.IGNORE_CASE
    )

    private fun containsIndicScript(text: String): Boolean {
        for (ch in text) {
            val code = ch.code
            if (code in 0x0900..0x097F || // Devanagari
                code in 0x0980..0x09FF || // Bengali
                code in 0x0A00..0x0A7F || // Gurmukhi (Punjabi)
                code in 0x0A80..0x0AFF || // Gujarati
                code in 0x0B00..0x0B7F || // Oriya
                code in 0x0B80..0x0BFF || // Tamil
                code in 0x0C00..0x0C7F || // Telugu
                code in 0x0C80..0x0CFF || // Kannada
                code in 0x0D00..0x0D7F || // Malayalam
                code in 0x0D80..0x0DFF    // Sinhala
            ) {
                return true
            }
        }
        return false
    }

    private fun isIndianContent(title: String, artist: String): Boolean {
        if (containsIndicScript(title) || containsIndicScript(artist)) return true
        val combined = "$title $artist".lowercase()
        if (INDIAN_KEYWORD_REGEX.containsMatchIn(combined)) return true
        return false
    }

    private fun isCompilation(title: String, artist: String): Boolean {
        if (title.startsWith("Unknown")) return true // unparsed playlist cards (no flexColumns)
        if (isIndianContent(title, artist)) return true

        val combined = "$title $artist".lowercase()

        // 1. Version/Cover/Tribute markers (Hard Kill)
        if (VERSION_MARKER.containsMatchIn(title) || VERSION_MARKER.containsMatchIn(artist)) return true

        // 2. Compilation phrases (Hard Kill)
        if (COMPILATION_PHRASE.containsMatchIn(title) || COMPILATION_PHRASE.containsMatchIn(artist)) return true

        // 3. Generic "Music" titles/artists (Hard Kill)
        // Kills: "Music To Make You Happy", "Upbeat Morning Music", "Relaxing Piano Music", etc.
        if (combined.contains("music to") || combined.contains("music for") ||
            (combined.contains("music") && (combined.contains("uplifting") || combined.contains("happy") || combined.contains("morning")))) return true

        // 4. Year markers in artist name usually indicate a yearly compilation (e.g. "Hits 2023")
        if (Regex("\\b(19|20)\\d{2}\\b").containsMatchIn(artist)) return true

        return false
    }

    private fun parseVideoTitle(item: JSONObject): String {
        return item.optJSONObject("title")
            ?.optJSONArray("runs")
            ?.optJSONObject(0)
            ?.optString("text", "Unknown Title")
            ?: "Unknown Title"
    }

    private fun parseVideoArtist(item: JSONObject): String {
        // Primary: ownerText
        val ownerArtist = item.optJSONObject("ownerText")
            ?.optJSONArray("runs")
            ?.optJSONObject(0)
            ?.optString("text")
        if (!ownerArtist.isNullOrBlank()) return ownerArtist

        // Fallback: longBylineText / shortBylineText
        val bylineArtist = item.optJSONObject("longBylineText")
            ?.optJSONArray("runs")
            ?.optJSONObject(0)
            ?.optString("text")
            ?: item.optJSONObject("shortBylineText")
                ?.optJSONArray("runs")
                ?.optJSONObject(0)
                ?.optString("text")
        if (!bylineArtist.isNullOrBlank()) return bylineArtist

        // Last resort: extract from combined "Song - Artist" title
        val titleText = item.optJSONObject("title")
            ?.optJSONArray("runs")
            ?.optJSONObject(0)
            ?.optString("text", "")
            ?: ""
        artistFromCombinedTitle(titleText)?.let { return it }

        return "Unknown Artist"
    }

    private fun parseVideoDuration(item: JSONObject): Long =
        durationTextToMs(
            item.optJSONObject("lengthText")?.optString("simpleText", "0:00") ?: "0:00"
        )

    private fun parseThumbnail(item: JSONObject): String =
        bestThumbnailUrl(
            item.optJSONObject("thumbnail")
                ?.optJSONObject("musicThumbnailRenderer")
                ?.optJSONObject("thumbnail")
                ?.optJSONArray("thumbnails")
        )

    private fun parseVideoThumbnail(item: JSONObject): String =
        bestThumbnailUrl(item.optJSONObject("thumbnail")?.optJSONArray("thumbnails"))

    /** Picks the highest-width thumbnail URL from any thumbnails array shape. */
    private fun bestThumbnailUrl(thumbnails: JSONArray?): String {
        if (thumbnails == null) return ""
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

    suspend fun fetchRelatedSongs(videoId: String): List<Song> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("context", clientContext())
                put("videoId", videoId)
            }
            val raw = post("next", body) ?: return@withContext emptyList()
            val response = JSONObject(raw)

            val results = mutableListOf<Song>()

            // Mencari array playlist dari berbagai kemungkinan struktur JSON YouTube
            val contents = response.optJSONObject("contents")
                ?.optJSONObject("singleColumnMusicWatchNextResultsRenderer")
                ?.optJSONObject("tabbedRenderer")
                ?.optJSONObject("watchNextTabbedResultsRenderer")
                ?.optJSONArray("tabs")
                ?.optJSONObject(0)
                ?.optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("musicQueueRenderer")
                ?.optJSONArray("content")
                ?.optJSONObject(0)
                ?.optJSONObject("playlistPanelRenderer")
                ?.optJSONArray("contents")
                ?: response.optJSONObject("contents")
                    ?.optJSONObject("twoColumnWatchNextResultsRenderer")
                    ?.optJSONObject("secondaryResults")
                    ?.optJSONObject("secondaryResults")
                    ?.optJSONArray("results")

            if (contents == null) return@withContext emptyList()

            for (i in 0 until contents.length()) {
                val item = contents.optJSONObject(i)?.optJSONObject("playlistPanelVideoRenderer")
                    ?: contents.optJSONObject(i)?.optJSONObject("compactVideoRenderer")
                    ?: continue

                val vId = item.optString("videoId") ?: continue
                if (vId == videoId) continue

                val title = item.optJSONObject("title")?.optString("simpleText")
                    ?: item.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                    ?: "Unknown"

                val artist = item.optJSONObject("longBylineText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                    ?: item.optJSONObject("shortBylineText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                    ?: "Unknown Artist"

                // No Indian content in the queue, ever
                if (isIndianContent(title, artist)) continue

                results.add(Song(
                    id = vId.hashCode().toLong(),
                    title = title,
                    artist = artist,
                    album = "Related",
                    duration = 0L,
                    uri = StreamSources.placeholderUri(vId),
                    source = StreamSources.YOUTUBE,
                    isStream = true,
                    remoteArtUrl = bestThumbnailUrl(item.optJSONObject("thumbnail")?.optJSONArray("thumbnails")).ifEmpty { null },
                    remoteId = vId
                ))
            }
            results
        } catch (e: Exception) {
            Timber.e(e, "YTMusicRepo fetchRelated failed")
            emptyList()
        }
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
                bitrate >= 320000 -> "Ultra (320kbps)"
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

        val m4aFormats = formats.filter { it.mimeType.contains("mp4") || it.mimeType.contains("aac") }
        val result = if (m4aFormats.isNotEmpty()) m4aFormats else formats
        result.sortedByDescending { it.bitrate }
    }

    /**
     * Builds a playable [Song] from a bare YouTube video id (deep link / share).
     * Metadata comes from the public oEmbed endpoint (title, channel, art);
     * duration stays 0 because the player resolves it when the stream starts.
     */
    suspend fun fetchSongByVideoId(videoId: String, preferVideo: Boolean): Song? = withContext(Dispatchers.IO) {
        if (!videoId.matches(Regex("^[A-Za-z0-9_-]{11}$"))) return@withContext null

        val watchUrl = "https://www.youtube.com/watch?v=$videoId"
        val oEmbedUrl = "https://www.youtube.com/oembed?url=${Uri.encode(watchUrl)}&format=json"
        val raw = get(URL(oEmbedUrl))

        var title = "YouTube video"
        var artist = "Unknown Artist"
        var thumb: String = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

        if (raw != null) {
            try {
                val json = JSONObject(raw)
                title = json.optString("title", title).ifBlank { title }
                artist = json.optString("author_name", artist).ifBlank { artist }
                thumb = json.optString("thumbnail_url", thumb).ifBlank { thumb }
            } catch (e: Exception) {
                Timber.w("YTMusicRepo oEmbed parse failed for $videoId: ${e.message}")
            }
        }

        Song(
            id = videoId.hashCode().toLong(),
            title = title,
            artist = artist,
            album = if (preferVideo) "YouTube" else "YouTube Music",
            duration = 0L,
            uri = StreamSources.placeholderUri(videoId),
            source = StreamSources.YOUTUBE,
            isStream = true,
            remoteArtUrl = thumb,
            remoteId = videoId,
            isVideo = preferVideo
        )
    }

    suspend fun resolveStreamUrl(song: Song, highPriority: Boolean = false): String? = withContext(Dispatchers.IO) {
        val videoId = song.remoteId ?: return@withContext null

        if (!song.uri.toString().startsWith(StreamSources.PLACEHOLDER_PREFIX)) {
            val cachedUri = song.uri.toString()
            if (isUrlFresh(cachedUri)) return@withContext cachedUri
            // Stale signed URL on the Song (restored from persisted state): fall
            // through to the cache/network instead of replaying a dead URL.
            streamUrlCache.remove(videoId)
        }

        // 1. In-memory cache dulu (tercepat, hidup selama proses app masih jalan)
        streamUrlCache[videoId]?.let {
            if (isUrlFresh(it)) {
                Timber.d("YTMusicRepo Memory cache hit for: ${song.title}")
                return@withContext it
            }
            Timber.d("YTMusicRepo Memory cache EXPIRED for: ${song.title}")
            streamUrlCache.remove(videoId)
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
            if (!highPriority) {
                playerSemaphore.acquire()
            }

            // Coba InnerTube dengan multiple clients (WEB_REMIX, TV_EMBEDDED, ANDROID_MUSIC, dll)
            var audioUrl = fetchViaInnerTube(videoId)

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
                Timber.e("YTMusicRepo ALL methods failed to resolve stream for: ${song.title} ($videoId)")
            }
            return@withContext audioUrl
        } finally {
            if (!highPriority) {
                playerSemaphore.release()
            }
        }
    }

    /**
     * Resolves a playable video stream for video mode: a progressive (muxed)
     * file when available, otherwise a video-only + audio-only pair that the
     * player merges. Returns null when the video exposes nothing playable.
     */
    suspend fun resolveVideoStream(song: Song): VideoStream? = withContext(Dispatchers.IO) {
        val videoId = song.remoteId ?: return@withContext null
        videoStreamCache[videoId]?.let {
            if (isUrlFresh(it.videoUrl) && (it.audioUrl.isNullOrBlank() || isUrlFresh(it.audioUrl))) {
                Timber.d("YTMusicRepo Video cache hit for: ${song.title}")
                return@withContext it
            }
            Timber.d("YTMusicRepo Video cache EXPIRED for: ${song.title}")
            videoStreamCache.remove(videoId)
        }
        val stream = fetchVideoStream(videoId)
        if (stream != null) {
            videoStreamCache[videoId] = stream
            Timber.d("YTMusicRepo Resolved video stream: ${song.title} - ${song.artist} (progressive=${stream.isProgressive})")
        } else {
            Timber.w("YTMusicRepo No playable video format for ${song.title} ($videoId)")
        }
        stream
    }

    /**
     * Fetch trending/popular songs using search API (reliable since browse
     * endpoint often requires auth). Searches chart queries and merges results.
     * @param countryCode ISO country code for region-specific results (e.g. "ID", "US")
     */
    /**
     * Fetches a "home feed"-ish list of songs for the Stream tab.
     *
     * FIX (root cause #3, see 04-Deep-Fix-And-Optimization-Trace.md): this
     * used to swallow every failure with a bare `catch (_: Exception) {}` at
     * both the per-query and per-item level, with zero logging. If YT Music's
     * `search` response shape changed (or a request just failed), the tab
     * would silently render empty with no way to tell "genuinely no results"
     * apart from "every request actually failed". This version:
     *  - logs every per-query failure (and *why* a query yielded zero items:
     *    no body, non-JSON, missing `contents`, or fully filtered out) via
     *    Timber instead of discarding the exception;
     *  - tracks whether at least one query got a structurally valid response
     *    ("contents" present) — if every single query failed to even reach
     *    that point, that's a real fetch/parsing failure and we now return
     *    [StreamSearchResult.ParsingFailed] instead of pretending it's just
     *    an empty result, so callers/UI can eventually tell the two apart
     *    (e.g. show a "couldn't load, tap to retry" state instead of a
     *    permanently blank tab).
     *
     * Note: this still queries the `search` endpoint with hardcoded chart
     * queries + an opaque base64 `params` filter blob — that's a separate,
     * more structural issue (see §3 of the trace doc: prefer a real
     * `browse`-based home-feed endpoint long term). This fix only addresses
     * the silent-failure behavior, not the endpoint choice itself.
     */
    suspend fun fetchHomeSongs(onPartial: (List<Song>) -> Unit, countryCode: String = "US"): StreamSearchResult =
        withContext(Dispatchers.IO) {
            // Chart-anchored queries: return official chart songs rather than local/random
            // content, unlike the old "official music video trending" style queries.
            val queries = listOf(
                "Billboard Hot 100",
                "UK Official Singles Chart Top 40",
                "US Top 40 Songs",
                "Global Top 50"
            )
            val seenIds = mutableSetOf<Long>()
            val allResults = mutableListOf<Song>()
            var anyQueryReachedValidResponse = false

            for (query in queries) {
                try {
                    val body = JSONObject().apply {
                        put("context", clientContext(countryCode, "en"))
                        put("query", query)
                        put("params", "EgWKAQIIAWoKEAMQBBAFEAoQCQ==")
                    }
                    val raw = post("search", body)
                    if (raw == null) {
                        Timber.w("fetchHomeSongs: query=\"$query\" got null response body (network/post failure)")
                        continue
                    }
                    val response = JSONObject(raw)
                    updateVisitorData(response)
                    if (!response.has("contents")) {
                        Timber.w("fetchHomeSongs: query=\"$query\" response has no \"contents\" key — response shape may have changed")
                        continue
                    }
                    anyQueryReachedValidResponse = true

                    val items = walkMusicContents(response)
                    if (items.length() == 0) {
                        Timber.w("fetchHomeSongs: query=\"$query\" contents present but walkMusicContents() found 0 items")
                    }
                    val limit = minOf(items.length(), 15)
                    var addedFromThisQuery = 0
                    for (i in 0 until limit) {
                        try {
                            val item = items.getJSONObject(i)
                            val videoId = parseVideoId(item) ?: continue
                            val id = videoId.hashCode().toLong()
                            if (id in seenIds) continue

                            val title = parseTitle(item)
                            val artist = parseArtist(item)
                            val duration = parseDuration(item)

                            if (duration > 20 * 60 * 1000L || isCompilation(title, artist) ||
                                title.contains("nonstop", ignoreCase = true) ||
                                title.contains("full album", ignoreCase = true)) continue

                            seenIds.add(id)
                            val song = Song(
                                id = id,
                                title = title,
                                artist = parseArtist(item),
                                album = parseAlbum(item),
                                duration = duration,
                                uri = StreamSources.placeholderUri(videoId),
                                source = StreamSources.YOUTUBE,
                                isStream = true,
                                remoteArtUrl = parseThumbnail(item),
                                remoteId = videoId
                            )
                            allResults.add(song)
                            addedFromThisQuery++
                            onPartial(listOf(song))
                        } catch (e: Exception) {
                            Timber.w(e, "fetchHomeSongs: query=\"$query\" failed to parse item at index $i")
                        }
                    }
                    if (addedFromThisQuery == 0 && items.length() > 0) {
                        Timber.w("fetchHomeSongs: query=\"$query\" had ${items.length()} raw items but all were filtered out (duration/compilation/nonstop/full-album rules)")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "fetchHomeSongs: query=\"$query\" threw an exception")
                }
            }

            when {
                allResults.isNotEmpty() -> StreamSearchResult.Success(allResults)
                !anyQueryReachedValidResponse -> {
                    // Every single query failed before we even got a usable
                    // response body — this is a fetch/parsing failure, not a
                    // legitimate "no results" state.
                    Timber.e("fetchHomeSongs: all ${queries.size} queries failed to reach a valid response — treating as ParsingFailed, not Empty")
                    StreamSearchResult.ParsingFailed
                }
                else -> StreamSearchResult.Empty
            }
        }

    suspend fun cleanupExpiredCache() = withContext(Dispatchers.IO) {
        streamCacheDao.deleteExpired(System.currentTimeMillis())
    }
}