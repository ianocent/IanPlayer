package com.ianocent.musicplayer.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import timber.log.Timber
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class LyricLine(val timeMs: Long, val text: String)

/** Result of a lyric lookup. The sync→plain fallback policy lives here, in the module. */
sealed class LyricResult {
    data class Synced(val lines: List<LyricLine>, val source: LyricSource = LyricSource.UNKNOWN) : LyricResult()
    data class Plain(val text: String, val source: LyricSource = LyricSource.UNKNOWN) : LyricResult()
    object None : LyricResult()
}

enum class LyricSource {
    LRCLIB_GET,
    LRCLIB_SEARCH,
    LRCMUX,
    GENIUS,
    MUSIXMATCH,
    LRCLIB_PLAIN,
    SOME_RANDOM_API,
    LYRICS_OVH,
    UNKNOWN
}

@Entity(tableName = "lyric_cache")
data class LyricCacheEntry(
    @PrimaryKey val key: String,
    val syncedJson: String?,
    val plainText: String?,
    val cachedAtMs: Long
)

@Dao
interface LyricCacheDao {
    @Query("SELECT * FROM lyric_cache WHERE key = :key LIMIT 1")
    suspend fun getByKey(key: String): LyricCacheEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: LyricCacheEntry)
}

class LyricRepository(
    private val lyricCache: LyricCacheDao? = null,
    private val nowMs: () -> Long = System::currentTimeMillis
) {

    /** Cache entries older than this are re-fetched. */
    private val cacheTtlMs = 7L * 24 * 60 * 60 * 1000

    /**
     * Fetches the best available lyric for a song: synced lines when any source
     * provides them, otherwise plain text, otherwise None.
     */
    suspend fun fetchLyric(song: Song): LyricResult {
        val synced = fetchSyncedLyric(song)
        if (synced != null) return LyricResult.Synced(synced.first, synced.second)
        val plain = fetchPlainLyric(song)
        return if (plain == null || plain.first.isNullOrBlank()) LyricResult.None else LyricResult.Plain(plain.first, plain.second)
    }

    suspend fun fetchSyncedLyric(song: Song): Pair<List<LyricLine>, LyricSource>? {
        val title = song.title
        val artist = song.artist
        val key = cacheKey(title, artist)
        
        val cached = lyricCache?.getByKey(key)
        cached?.syncedJson?.let { json ->
            val isExpired = nowMs() - cached.cachedAtMs > cacheTtlMs
            if (json == "NONE") {
                if (!isExpired) return null
            } else {
                parseSyncedJson(json)?.let { return it to LyricSource.UNKNOWN }
            }
        }

        val primary = getPrimaryArtist(artist)

        // 1. Precise GET from LRCLIB (Precise)
        fetchFromLrcLibGet(song)?.let {
            saveSynced(key, it)
            return it to LyricSource.LRCLIB_GET
        }

        // 2. Fallback to Search LRCLIB (Full & Primary)
        val lrcLibResult = fetchFromLrcLibSynced(title, artist) ?: if (primary != artist) fetchFromLrcLibSynced(title, primary) else null
        if (!lrcLibResult.isNullOrEmpty()) {
            saveSynced(key, lrcLibResult)
            return lrcLibResult!! to LyricSource.LRCLIB_SEARCH
        }

        // 3. Fallback to LrcMux (Full & Primary)
        val lrcMuxResult = fetchFromLrcMuxSynced(title, artist) ?: if (primary != artist) fetchFromLrcMuxSynced(title, primary) else null
        if (!lrcMuxResult.isNullOrEmpty()) {
            saveSynced(key, lrcMuxResult)
            return lrcMuxResult!! to LyricSource.LRCMUX
        }

        // 4. Genius synced (via jina.ai text extraction)
        val geniusSynced = fetchFromGeniusSynced(title, artist) ?: if (primary != artist) fetchFromGeniusSynced(title, primary) else null
        if (!geniusSynced.isNullOrEmpty()) {
            saveSynced(key, geniusSynced)
            return geniusSynced!! to LyricSource.GENIUS
        }

        // 5. Musixmatch synced (via jina.ai text extraction)
        val musixmatchSynced = fetchFromMusixmatchSynced(title, artist) ?: if (primary != artist) fetchFromMusixmatchSynced(title, primary) else null
        if (!musixmatchSynced.isNullOrEmpty()) {
            saveSynced(key, musixmatchSynced)
            return musixmatchSynced!! to LyricSource.MUSIXMATCH
        }

        // 6. Check if plain sources actually have LRC tags
        val plainFallback = fetchFromLrcLibPlain(title, artist) ?: fetchFromSomeRandomApi(title, artist)
        if (!plainFallback.isNullOrBlank() && isLrcFormat(plainFallback)) {
            val lines = parseLrc(plainFallback)
            if (lines.isNotEmpty()) {
                saveSynced(key, lines)
                return lines to LyricSource.LRCLIB_PLAIN
            }
        }

        saveSynced(key, null) // Mark as NONE in cache
        return null
    }

    suspend fun fetchPlainLyric(song: Song): Pair<String, LyricSource>? {
        val title = song.title
        val artist = song.artist
        val key = cacheKey(title, artist)
        
        val cached = lyricCache?.getByKey(key)
        cached?.plainText?.let {
            val isExpired = nowMs() - cached.cachedAtMs > cacheTtlMs
            if (it == "NONE") {
                if (!isExpired) return null
            } else if (it.isNotBlank()) {
                return it to LyricSource.UNKNOWN
            }
        }

        val primary = getPrimaryArtist(artist)
        val sources = listOf(
            Pair({ fetchFromLrcLibPlain(title, artist) }, LyricSource.LRCLIB_PLAIN),
            Pair({ if (primary != artist) fetchFromLrcLibPlain(title, primary) else null }, LyricSource.LRCLIB_PLAIN),
            Pair({ fetchFromLrcMuxPlain(title, artist) }, LyricSource.LRCMUX),
            Pair({ fetchFromSomeRandomApi(title, artist) }, LyricSource.SOME_RANDOM_API),
            Pair({ fetchFromLyricsOvh(title, artist) }, LyricSource.LYRICS_OVH),
            Pair({ fetchFromGenius(title, artist) }, LyricSource.GENIUS),
            Pair({ fetchFromMusixmatch(title, artist) }, LyricSource.MUSIXMATCH)
        )

        for ((source, src) in sources) {
            val res = try { source() } catch (_: Exception) { null }
            if (!res.isNullOrBlank()) {
                // If it's LRC, clean it for plain view
                val cleanText = if (isLrcFormat(res)) stripLrcTags(res) else res
                savePlain(key, cleanText)
                return cleanText to src
            }
        }

        savePlain(key, null)
        return null
    }

    private fun isLrcFormat(text: String): Boolean =
        text.contains(Regex("""\[\d{2}:\d{2}"""))

    /**
     * Single HTTP transport for every lyric source. Returns the body when the
     * server answers 200, otherwise null.
     */
    private fun httpGet(
        url: String,
        connectTimeoutMs: Int = 5000,
        readTimeoutMs: Int = 5000,
        userAgent: String = "IanPlayer/1.0"
    ): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", userAgent)
            conn.connectTimeout = connectTimeoutMs
            conn.readTimeout = readTimeoutMs
            if (conn.responseCode != HttpURLConnection.HTTP_OK) null
            else conn.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    private fun stripLrcTags(lrc: String): String =
        lrc.replace(Regex("""\[\d{2}:\d{2}(?:\.\d{2,3})?]"""), "").trim()

    private fun getPrimaryArtist(artist: String): String =
        artist.split(',', ';', '&', '/').first().trim()

    private fun cacheKey(title: String, artist: String) =
        "${title.lowercase().trim()}|${artist.lowercase().trim()}"

    private suspend fun saveSynced(key: String, lines: List<LyricLine>?) {
        lyricCache ?: return
        val existing = lyricCache.getByKey(key)
        val json = if (lines == null) "NONE" else {
            JSONArray().apply {
                lines.forEach { line -> put(JSONObject().put("t", line.timeMs).put("s", line.text)) }
            }.toString()
        }
        lyricCache.upsert(LyricCacheEntry(key, json, existing?.plainText, nowMs()))
    }

    private suspend fun savePlain(key: String, text: String?) {
        lyricCache ?: return
        val existing = lyricCache.getByKey(key)
        val plain = text ?: "NONE"
        lyricCache.upsert(LyricCacheEntry(key, existing?.syncedJson, plain, nowMs()))
    }

    private fun parseSyncedJson(json: String): List<LyricLine>? {
        if (json == "NONE") return null
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.getJSONObject(i)
                LyricLine(obj.optLong("t"), obj.optString("s"))
            }.ifEmpty { null }
        } catch (e: Exception) {
            Timber.e(e, "Error parsing cached synced lyric")
            null
        }
    }

    // Public API for cycling lyric sources
    suspend fun cycleLyricSource(song: Song, currentSource: LyricSource): Pair<LyricResult, LyricSource>? {
        val sources = listOf(
            LyricSource.LRCLIB_GET,
            LyricSource.LRCLIB_SEARCH,
            LyricSource.LRCMUX,
            LyricSource.GENIUS,
            LyricSource.MUSIXMATCH,
            LyricSource.LRCLIB_PLAIN,
            LyricSource.SOME_RANDOM_API,
            LyricSource.LYRICS_OVH
        )
        val currentIdx = sources.indexOf(currentSource)
        val nextIdx = (currentIdx + 1) % sources.size
        val nextSource = sources[nextIdx]
        
        return try {
            when (nextSource) {
                LyricSource.LRCLIB_GET -> {
                    fetchFromLrcLibGet(song)?.let { LyricResult.Synced(it, LyricSource.LRCLIB_GET) to LyricSource.LRCLIB_GET }
                }
                LyricSource.LRCLIB_SEARCH -> {
                    val primary = getPrimaryArtist(song.artist)
                    val result = fetchFromLrcLibSynced(song.title, song.artist) 
                        ?: if (primary != song.artist) fetchFromLrcLibSynced(song.title, primary) else null
                    result?.let { LyricResult.Synced(it, LyricSource.LRCLIB_SEARCH) to LyricSource.LRCLIB_SEARCH }
                }
                LyricSource.LRCMUX -> {
                    val primary = getPrimaryArtist(song.artist)
                    val result = fetchFromLrcMuxSynced(song.title, song.artist) 
                        ?: if (primary != song.artist) fetchFromLrcMuxSynced(song.title, primary) else null
                    result?.let { LyricResult.Synced(it, LyricSource.LRCMUX) to LyricSource.LRCMUX }
                }
                LyricSource.GENIUS -> {
                    val primary = getPrimaryArtist(song.artist)
                    val result = fetchFromGeniusSynced(song.title, song.artist) 
                        ?: if (primary != song.artist) fetchFromGeniusSynced(song.title, primary) else null
                    result?.let { LyricResult.Synced(it, LyricSource.GENIUS) to LyricSource.GENIUS }
                }
                LyricSource.MUSIXMATCH -> {
                    val primary = getPrimaryArtist(song.artist)
                    val result = fetchFromMusixmatchSynced(song.title, song.artist) 
                        ?: if (primary != song.artist) fetchFromMusixmatchSynced(song.title, primary) else null
                    result?.let { LyricResult.Synced(it, LyricSource.MUSIXMATCH) to LyricSource.MUSIXMATCH }
                }
                LyricSource.LRCLIB_PLAIN -> {
                    val plain = fetchFromLrcLibPlain(song.title, song.artist) 
                        ?: fetchFromSomeRandomApi(song.title, song.artist)
                    if (plain != null && isLrcFormat(plain)) {
                        parseLrc(plain)?.let { LyricResult.Synced(it, LyricSource.LRCLIB_PLAIN) to LyricSource.LRCLIB_PLAIN }
                    } else null
                }
                LyricSource.SOME_RANDOM_API -> {
                    val plain = fetchFromSomeRandomApi(song.title, song.artist)
                    if (plain != null && isLrcFormat(plain)) {
                        parseLrc(plain)?.let { LyricResult.Synced(it, LyricSource.SOME_RANDOM_API) to LyricSource.SOME_RANDOM_API }
                    } else null
                }
                LyricSource.LYRICS_OVH -> {
                    val plain = fetchFromLyricsOvh(song.title, song.artist)
                    if (plain != null && isLrcFormat(plain)) {
                        parseLrc(plain)?.let { LyricResult.Synced(it, LyricSource.LYRICS_OVH) to LyricSource.LYRICS_OVH }
                    } else null
                }
                else -> null
            }
        } catch (e: Exception) {
            Timber.e(e, "Error cycling lyric source")
            null
        }
    }

    // ==========================================
    // SOURCE 1: LRCLIB
    // ==========================================
    private fun fetchFromLrcLibGet(song: Song): List<LyricLine>? {
        return try {
            val title = URLEncoder.encode(song.title, "UTF-8")
            val artist = URLEncoder.encode(song.artist, "UTF-8")
            val duration = (song.duration / 1000).toInt()
            
            // 1. Full artist + title
            var url = URL("https://lrclib.net/api/get?artist=$artist&track_name=$title&duration=$duration")
            var res = fetchLrcFromUrl(url)
            if (res != null) return res

            // 2. Primary artist + title
            val primary = URLEncoder.encode(getPrimaryArtist(song.artist), "UTF-8")
            if (primary != artist) {
                url = URL("https://lrclib.net/api/get?artist=$primary&track_name=$title&duration=$duration")
                res = fetchLrcFromUrl(url)
                if (res != null) return res
            }
            null
        } catch (_: Exception) { null }
    }

    private fun fetchLrcFromUrl(url: URL): List<LyricLine>? {
        val resp = httpGet(url.toString(), connectTimeoutMs = 2000, readTimeoutMs = 2000) ?: return null
        return try {
            val synced = JSONObject(resp).optString("syncedLyrics")
            if (synced.isNotBlank()) parseLrc(synced) else null
        } catch (_: Exception) { null }
    }

    private fun fetchFromLrcLibSynced(title: String, artist: String): List<LyricLine>? {
        return try {
            val query = URLEncoder.encode("$artist $title", "UTF-8")
            val response = httpGet("https://lrclib.net/api/search?q=$query", readTimeoutMs = 3000)
                ?: return null
            val jsonArray = JSONArray(response)
            if (jsonArray.length() == 0) return null

            val firstResult = jsonArray.getJSONObject(0)
            val synced = firstResult.optString("syncedLyrics")
            if (synced.isBlank()) return null

            parseLrc(synced)
        } catch (e: Exception) {
            Timber.e(e, "Error fetching synced lyric from LRCLIB")
            null
        }
    }

    private fun fetchFromLrcLibPlain(title: String, artist: String): String? {
        return try {
            val query = URLEncoder.encode("$artist $title", "UTF-8")
            val response = httpGet("https://lrclib.net/api/search?q=$query") ?: return null
            val jsonArray = JSONArray(response)
            if (jsonArray.length() == 0) return null

            jsonArray.getJSONObject(0).optString("plainLyrics").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching plain lyric from LRCLIB")
            null
        }
    }

    // ==========================================
    // SOURCE 2: LRCMUX (Aggregator, cakep banget)
    // ==========================================
    private fun fetchFromLrcMuxSynced(title: String, artist: String): List<LyricLine>? {
        return try {
            val encArtist = URLEncoder.encode(artist, "UTF-8")
            val encTitle = URLEncoder.encode(title, "UTF-8")
            val response = httpGet(
                "https://api.lrcmux.dev/get?artist=$encArtist&title=$encTitle&format=json",
                userAgent = "IanPlayer/1.0 (https://github.com/ianocent/IanPlayer)"
            ) ?: return null

            val json = JSONObject(response)
            val lines = json.optJSONArray("lines") ?: return null
            if (lines.length() == 0) return null

            val result = mutableListOf<LyricLine>()
            for (i in 0 until lines.length()) {
                val line = lines.getJSONObject(i)
                val text = line.optString("text", "").trim()
                if (text.isBlank()) continue
                val startMs = line.optLong("start", -1)
                val endMs = line.optLong("end", -1)
                result.add(LyricLine(if (startMs >= 0) startMs else endMs, text))
            }
            result.ifEmpty { null }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching synced lyric from lrcmux")
            null
        }
    }

    private fun fetchFromLrcMuxPlain(title: String, artist: String): String? {
        return try {
            val encArtist = URLEncoder.encode(artist, "UTF-8")
            val encTitle = URLEncoder.encode(title, "UTF-8")
            httpGet(
                "https://api.lrcmux.dev/get?artist=$encArtist&title=$encTitle&format=txt",
                userAgent = "IanPlayer/1.0 (https://github.com/ianocent/IanPlayer)"
            )?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching plain lyric from lrcmux")
            null
        }
    }

    // ==========================================
    // SOURCE 3: SOME RANDOM API (Mantap buat fallback)
    // ==========================================
    private fun fetchFromSomeRandomApi(title: String, artist: String): String? {
        return try {
            val query = URLEncoder.encode("$title $artist", "UTF-8")
            httpGet(
                "https://some-random-api.com/lyrics?title=$query",
                userAgent = "Mozilla/5.0"
            )?.let { JSONObject(it).optString("lyrics").takeIf { t -> t.isNotBlank() } }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching from SomeRandomAPI")
            null
        }
    }

    // ==========================================
    // SOURCE 3: LYRICS.OVH
    // ==========================================
    private fun fetchFromLyricsOvh(title: String, artist: String): String? {
        return try {
            val encArtist = URLEncoder.encode(artist, "UTF-8")
            val encTitle = URLEncoder.encode(title, "UTF-8")
            httpGet("https://api.lyrics.ovh/v1/$encArtist/$encTitle")
                ?.let { JSONObject(it).optString("lyrics").takeIf { t -> t.isNotBlank() } }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching from lyrics.ovh")
            null
        }
    }

    // ==========================================
    // SOURCE 4: GENIUS (via textise dot iitty)
    // ==========================================
    private fun fetchFromGenius(title: String, artist: String): String? {
        return try {
            val query = URLEncoder.encode("$artist $title", "UTF-8")
            // Using textise dot iitty for Genius lyrics (no API key needed)
            httpGet(
                "https://r.jina.ai/http://genius.com/${URLEncoder.encode(artist, "UTF-8")}-${URLEncoder.encode(title, "UTF-8")}-lyrics",
                userAgent = "Mozilla/5.0 (compatible; IanPlayer/1.0)"
            )?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching from Genius")
            null
        }
    }

    // ==========================================
    // SOURCE 5: MUSIXMATCH (public search)
    // ==========================================
    private fun fetchFromMusixmatch(title: String, artist: String): String? {
        return try {
            val query = URLEncoder.encode("$artist $title", "UTF-8")
            // Musixmatch public API via textise
            httpGet(
                "https://r.jina.ai/http://www.musixmatch.com/search/${URLEncoder.encode(query, "UTF-8")}",
                userAgent = "Mozilla/5.0 (compatible; IanPlayer/1.0)"
            )?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching from Musixmatch")
            null
        }
}

// ==========================================
    // SOURCE 4: GENIUS SYNCED (via jina.ai text extraction + LRC detection)
    // ==========================================
    private fun fetchFromGeniusSynced(title: String, artist: String): List<LyricLine>? {
        return try {
            val query = URLEncoder.encode("$artist $title", "UTF-8")
            httpGet(
                "https://r.jina.ai/http://genius.com/${URLEncoder.encode(artist, "UTF-8")}-${URLEncoder.encode(title, "UTF-8")}-lyrics",
                userAgent = "Mozilla/5.0 (compatible; IanPlayer/1.0)"
            )?.let { text ->
                if (text.isNotBlank() && isLrcFormat(text)) parseLrc(text) else null
            }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching synced from Genius")
            null
        }
    }

    // ==========================================
    // SOURCE 5: MUSIXMATCH SYNCED (via jina.ai text extraction + LRC detection)
    // ==========================================
    private fun fetchFromMusixmatchSynced(title: String, artist: String): List<LyricLine>? {
        return try {
            val query = URLEncoder.encode("$artist $title", "UTF-8")
            httpGet(
                "https://r.jina.ai/http://www.musixmatch.com/search/${URLEncoder.encode(query, "UTF-8")}",
                userAgent = "Mozilla/5.0 (compatible; IanPlayer/1.0)"
            )?.let { text ->
                if (text.isNotBlank() && isLrcFormat(text)) parseLrc(text) else null
            }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching synced from Musixmatch")
            null
        }
    }

    // ==========================================
    // HELPER: Lrc Parser
    // ==========================================
    private fun parseLrc(lrc: String): List<LyricLine> {
        val regex = Regex("""\[(\d{2}):(\d{2})(?:\.(\d{2,3}))?]\s*(.*)""")
        return lrc.lines().mapNotNull { line ->
            val match = regex.find(line) ?: return@mapNotNull null
            val min = match.groupValues[1].toLong()
            val sec = match.groupValues[2].toLong()
            val msRaw = match.groupValues[3]
            val ms = if (msRaw.isNotEmpty()) msRaw.padEnd(3, '0').take(3).toLong() else 0L
            val text = match.groupValues[4].trim()
            if (text.isBlank()) return@mapNotNull null
            LyricLine(min * 60000 + sec * 1000 + ms, text)
        }
    }
}