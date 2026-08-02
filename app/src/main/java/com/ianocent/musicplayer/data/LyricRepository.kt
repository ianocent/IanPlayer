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
    private val lyricCache: LyricCacheDao? = null
) {

    suspend fun fetchSyncedLyric(song: Song): List<LyricLine>? {
        val title = song.title
        val artist = song.artist
        val key = cacheKey(title, artist)
        
        val cached = lyricCache?.getByKey(key)
        cached?.syncedJson?.let { json ->
            val isExpired = System.currentTimeMillis() - cached.cachedAtMs > 7L * 24 * 60 * 60 * 1000
            if (json == "NONE") {
                if (!isExpired) return null
            } else {
                parseSyncedJson(json)?.let { return it }
            }
        }

        val primary = getPrimaryArtist(artist)

        // 1. Precise GET from LRCLIB (Precise)
        fetchFromLrcLibGet(song)?.let {
            saveSynced(key, it)
            return it
        }

        // 2. Fallback to Search LRCLIB (Full & Primary)
        val lrcLibResult = fetchFromLrcLibSynced(title, artist) ?: if (primary != artist) fetchFromLrcLibSynced(title, primary) else null
        if (!lrcLibResult.isNullOrEmpty()) {
            saveSynced(key, lrcLibResult)
            return lrcLibResult
        }

        // 3. Fallback to LrcMux (Full & Primary)
        val lrcMuxResult = fetchFromLrcMuxSynced(title, artist) ?: if (primary != artist) fetchFromLrcMuxSynced(title, primary) else null
        if (!lrcMuxResult.isNullOrEmpty()) {
            saveSynced(key, lrcMuxResult)
            return lrcMuxResult
        }

        // 4. Check if plain sources actually have LRC tags
        val plainFallback = fetchFromLrcLibPlain(title, artist) ?: fetchFromSomeRandomApi(title, artist)
        if (!plainFallback.isNullOrBlank() && isLrcFormat(plainFallback)) {
            val lines = parseLrc(plainFallback)
            if (lines.isNotEmpty()) {
                saveSynced(key, lines)
                return lines
            }
        }

        saveSynced(key, null) // Mark as NONE in cache
        return null
    }

    suspend fun fetchPlainLyric(song: Song): String? {
        val title = song.title
        val artist = song.artist
        val key = cacheKey(title, artist)
        
        val cached = lyricCache?.getByKey(key)
        cached?.plainText?.let { 
            val isExpired = System.currentTimeMillis() - cached.cachedAtMs > 7L * 24 * 60 * 60 * 1000
            if (it == "NONE") {
                if (!isExpired) return null
            } else if (it.isNotBlank()) {
                return it 
            }
        }

        val primary = getPrimaryArtist(artist)
        val sources = listOf(
            { fetchFromLrcLibPlain(title, artist) },
            { if (primary != artist) fetchFromLrcLibPlain(title, primary) else null },
            { fetchFromLrcMuxPlain(title, artist) },
            { fetchFromSomeRandomApi(title, artist) },
            { fetchFromLyricsOvh(title, artist) }
        )

        for (source in sources) {
            val res = try { source() } catch (_: Exception) { null }
            if (!res.isNullOrBlank()) {
                // If it's LRC, clean it for plain view
                val cleanText = if (isLrcFormat(res)) stripLrcTags(res) else res
                savePlain(key, cleanText)
                return cleanText
            }
        }

        savePlain(key, null)
        return null
    }

    private fun isLrcFormat(text: String): Boolean = 
        text.contains(Regex("""\[\d{2}:\d{2}"""))

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
        lyricCache.upsert(LyricCacheEntry(key, json, existing?.plainText, System.currentTimeMillis()))
    }

    private suspend fun savePlain(key: String, text: String?) {
        lyricCache ?: return
        val existing = lyricCache.getByKey(key)
        val plain = text ?: "NONE"
        lyricCache.upsert(LyricCacheEntry(key, existing?.syncedJson, plain, System.currentTimeMillis()))
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

    // ==========================================
    // SOURCE 4: GENIUS API (Search Fallback)
    // ==========================================
    private fun fetchFromGenius(title: String, artist: String): String? {
        return try {
            // NOTE: Genius API needs an Access Token. Using a search query to find the song.
            // Genius doesn't provide lyrics text via API (only URL), so we'd need to scrape.
            // For now, we'll try to get the description or use it to verify metadata.
            val accessToken = "VpEQHyTkUM4FXXKo5VEltGQmT_bgflqKnqKhp6bbG12zhu5j2Cm0Gc9ezYf4oa-x" // User should provide this
            val query = URLEncoder.encode("$title $artist", "UTF-8")
            val url = URL("https://api.genius.com/search?q=$query")
            val connection = url.openConnection() as HttpURLConnection
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode != 200) return null
            
            val response = connection.inputStream.bufferedReader().readText()
            val json = JSONObject(response)
            val hits = json.getJSONObject("response").getJSONArray("hits")
            if (hits.length() == 0) return null
            
            // Getting the first hit URL - In a real scenario, you'd scrape this URL.
            // Since we can't scrape easily without Jsoup, we'll just log it.
            val songPath = hits.getJSONObject(0).getJSONObject("result").getString("url")
            Timber.d("Genius URL found: $songPath")
            
            null // Fallback to other sources since we can't scrape without extra libs
        } catch (e: Exception) {
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
        return try {
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "IanPlayer/1.0")
            conn.connectTimeout = 2000
            conn.readTimeout = 2000
            if (conn.responseCode != 200) return null
            val resp = conn.inputStream.bufferedReader().readText()
            val synced = JSONObject(resp).optString("syncedLyrics")
            if (synced.isNotBlank()) parseLrc(synced) else null
        } catch (_: Exception) { null }
    }

    private fun fetchFromLrcLibSynced(title: String, artist: String): List<LyricLine>? {
        return try {
            val query = URLEncoder.encode("$artist $title", "UTF-8")
            val url = URL("https://lrclib.net/api/search?q=$query")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "IanPlayer/1.0")
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            if (connection.responseCode != 200) return null

            val response = connection.inputStream.bufferedReader().readText()
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
            val url = URL("https://lrclib.net/api/search?q=$query")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "IanPlayer/1.0")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            if (connection.responseCode != 200) return null

            val response = connection.inputStream.bufferedReader().readText()
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
            val url = URL("https://api.lrcmux.dev/get?artist=$encArtist&title=$encTitle&format=json")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "IanPlayer/1.0 (https://github.com/ianocent/IanPlayer)")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode != 200) return null

            val response = connection.inputStream.bufferedReader().readText()
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
            val url = URL("https://api.lrcmux.dev/get?artist=$encArtist&title=$encTitle&format=txt")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "IanPlayer/1.0 (https://github.com/ianocent/IanPlayer)")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode != 200) return null

            connection.inputStream.bufferedReader().readText().takeIf { it.isNotBlank() }
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
            val url = URL("https://some-random-api.com/lyrics?title=$query")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode != 200) return null

            val response = connection.inputStream.bufferedReader().readText()
            JSONObject(response).optString("lyrics").takeIf { it.isNotBlank() }
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
            val url = URL("https://api.lyrics.ovh/v1/$encArtist/$encTitle")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null

            val response = connection.inputStream.bufferedReader().readText()
            JSONObject(response).optString("lyrics").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching from lyrics.ovh")
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