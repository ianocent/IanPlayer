package com.ianocent.musicplayer.data

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class SoundCloudRepository {

    companion object {
        private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"

        private val fallbackClientIds = listOf(
            "2t9loNQH90kzJcsFCODdigxfp325aq4z",
            "iZIs9mchVcX5lhVRyQGGAYlNPVldzAoX",
            "a3e059563d7fd3372b49b37f00a00bcf"
        )
    }

    private var cachedClientId: String? = null
    private val mutex = Mutex()

    private suspend fun fetchUrl(url: URL, accept: String = "application/json"): String? {
        try {
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Accept", accept)
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.instanceFollowRedirects = true
            val code = conn.responseCode
            if (code != 200) {
                Log.w("SoundCloudRepo", "HTTP $code for ${url.host}${url.path}")
                return null
            }
            return conn.inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            Log.w("SoundCloudRepo", "Fetch failed ${url.host}: ${e.message}")
            return null
        }
    }

    private suspend fun getClientId(): String? = mutex.withLock {
        if (cachedClientId != null) return@withLock cachedClientId

        Log.d("SoundCloudRepo", "Trying to get client_id...")

        val id = extractFromHydration()
            ?: extractFromScripts()
            ?: tryFallbacks()
            ?: return@withLock null

        Log.d("SoundCloudRepo", "Using client_id: ${id.take(8)}...")
        cachedClientId = id
        id
    }

    private suspend fun extractFromHydration(): String? {
        val html = fetchUrl(URL("https://soundcloud.com"), "text/html") ?: return null
        Log.d("SoundCloudRepo", "Homepage HTML len=${html.length}")

        val patterns = listOf(
            Regex(""""client_id"\s*:\s*"([a-zA-Z0-9]{20,40})""""),
            Regex(""""client_id"\s*:\s*"([^"]{20,40})""""),
            Regex("""client_id\s*[=:]\s*["\']([a-zA-Z0-9]{20,40})["\']"""),
            Regex("""\\u0022client_id\\u0022\\u003A\\u0022([a-zA-Z0-9]{20,40})\\u0022"""),
            Regex("""\{["\']Hydratable["\']:\{["\']name["\']:["\']client_id["\'],["\']value["\']:["\']([a-zA-Z0-9]{20,40})["\']"""),
            Regex("""client_id["\s:=]+([a-zA-Z0-9]{32})""")
        )
        for (pattern in patterns) {
            val match = pattern.find(html)
            if (match != null) {
                val id = match.groupValues[1]
                if (id.length in 20..40) {
                    Log.d("SoundCloudRepo", "Extracted client_id with pattern: ${pattern.pattern.take(40)}")
                    return id
                }
            }
        }

        val idx = html.indexOf("client_id")
        if (idx >= 0) {
            val context = html.substring(maxOf(0, idx - 20), minOf(html.length, idx + 100))
            Log.d("SoundCloudRepo", "client_id context: $context")
        } else {
            Log.d("SoundCloudRepo", "No 'client_id' substring found in HTML")
        }

        Log.w("SoundCloudRepo", "No client_id regex matched")
        return null
    }

    private suspend fun extractFromScripts(): String? {
        val html = fetchUrl(URL("https://soundcloud.com"), "text/html") ?: return null

        val scriptPattern = Regex("""<script[^>]*src=["']([^"']+\.js[^"']*)["']""")
        val matches = scriptPattern.findAll(html).toList().take(5)

        if (matches.isEmpty()) {
            Log.w("SoundCloudRepo", "No script tags found in HTML")
            return null
        }

        for (match in matches) {
            val scriptUrl = match.groupValues[1]
            val fullUrl = if (scriptUrl.startsWith("http")) scriptUrl
                else if (scriptUrl.startsWith("//")) "https:$scriptUrl"
                else "https://soundcloud.com$scriptUrl"

            val js = fetchUrl(URL(fullUrl)) ?: continue

            val idPattern = Regex("""client_id["\s:=]+([a-zA-Z0-9]{20,40})""")
            val idMatch = idPattern.find(js)
            if (idMatch != null) {
                val id = idMatch.groupValues[1].trim()
                Log.d("SoundCloudRepo", "Extracted client_id from JS: ${fullUrl.take(60)}")
                return id
            }
        }
        Log.w("SoundCloudRepo", "No client_id in any script")
        return null
    }

    private suspend fun tryFallbacks(): String? {
        for (id in fallbackClientIds) {
            val testUrl = "https://api-v2.soundcloud.com/tracks/13158665?client_id=$id"
            val resp = fetchUrl(URL(testUrl))
            if (resp != null) {
                Log.d("SoundCloudRepo", "Fallback client_id works: ${id.take(8)}...")
                return id
            }
            Log.w("SoundCloudRepo", "Fallback ${id.take(8)}... rejected")
        }
        Log.w("SoundCloudRepo", "All fallback client_ids failed")
        return null
    }

    private suspend fun getApi(path: String): String? {
        val id = getClientId() ?: return null
        try {
            val separator = if (path.contains("?")) "&" else "?"
            val url = URL("https://api-v2.soundcloud.com$path${separator}client_id=$id")
            return fetchUrl(url)
        } catch (e: Exception) {
            return null
        }
    }

    suspend fun searchSongs(query: String): List<Song> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        Log.d("SoundCloudRepo", "Searching: $query")
        try {
            val encQuery = URLEncoder.encode(query, "UTF-8")
            val raw = getApi("/search/tracks?q=$encQuery&limit=15&filter=public")
            if (raw == null) {
                Log.e("SoundCloudRepo", "Search returned null for: $query")
                return@withContext emptyList()
            }

            val json = JSONObject(raw)
            val collection = json.optJSONArray("collection")
            if (collection == null || collection.length() == 0) {
                Log.w("SoundCloudRepo", "No tracks in search response")
                return@withContext emptyList()
            }
            Log.d("SoundCloudRepo", "Found ${collection.length()} tracks")

            val songs = mutableListOf<Song>()
            for (i in 0 until minOf(collection.length(), 10)) {
                try {
                    val track = collection.getJSONObject(i)
                    val title = track.optString("title", "Unknown Title")
                    val user = track.optJSONObject("user")
                    val artist = user?.optString("username", "Unknown Artist") ?: "Unknown Artist"
                    val duration = track.optLong("duration", 0L)
                    val artwork = track.optString("artwork_url", "")
                    val artUrl = if (artwork.isNotBlank()) {
                        artwork.replace("large", "t500x500")
                    } else ""

                    val streamable = track.optBoolean("streamable", false)
                    val streamUrl = if (streamable) {
                        val trackId = track.optLong("id", 0L)
                        if (trackId > 0) fetchStreamUrl(trackId) else null
                    } else null

                    if (streamUrl != null) {
                        Log.d("SoundCloudRepo", "Got stream: $title - $artist")
                        songs.add(
                            Song(
                                id = track.optLong("id", title.hashCode().toLong()),
                                title = title,
                                artist = artist,
                                duration = duration,
                                uri = Uri.parse(streamUrl),
                                isStream = true,
                                remoteArtUrl = artUrl
                            )
                        )
                    } else {
                        Log.w("SoundCloudRepo", "Not streamable: $title")
                    }
                } catch (e: Exception) {
                    Log.w("SoundCloudRepo", "Parse track failed: ${e.message}")
                }
            }
            Log.d("SoundCloudRepo", "Final results: ${songs.size} tracks")
            songs
        } catch (e: Exception) {
            Log.e("SoundCloudRepo", "Search failed: ${e.message}", e)
            emptyList()
        }
    }

    private suspend fun fetchStreamUrl(trackId: Long): String? {
        try {
            val raw = getApi("/tracks/$trackId/streams")
            if (raw == null) return null

            val json = JSONObject(raw)
            val progressive = json.optString("http_mp3_128_url", "")
            if (progressive.isNotBlank()) return progressive

            val hls = json.optString("hls_mp3_128_url", "")
            if (hls.isNotBlank()) return hls

            return null
        } catch (e: Exception) {
            Log.w("SoundCloudRepo", "Stream fetch failed: ${e.message}")
            return null
        }
    }
}
