package com.ianocent.musicplayer.data

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class LyricLine(val timeMs: Long, val text: String)

class LyricRepository {

    fun fetchSyncedLyric(title: String, artist: String): List<LyricLine>? {
        // Coba dari LRCLIB (Satu-satunya yang ngasih synced/karaoke style mantap)
        return fetchFromLrcLibSynced(title, artist)
    }

    fun fetchPlainLyric(title: String, artist: String): String? {
        // 1. Coba dari LRCLIB
        val lrcLibResult = fetchFromLrcLibPlain(title, artist)
        if (!lrcLibResult.isNullOrBlank()) return lrcLibResult

        // 2. Fallback 1: SomeRandomAPI (Sangat reliable buat plain lyric)
        val sraResult = fetchFromSomeRandomApi(title, artist)
        if (!sraResult.isNullOrBlank()) return sraResult

        // 3. Fallback 2: api.lyrics.ovh
        val ovhResult = fetchFromLyricsOvh(title, artist)
        if (!ovhResult.isNullOrBlank()) return ovhResult

        return null
    }

    // ==========================================
    // SOURCE 1: LRCLIB
    // ==========================================
    private fun fetchFromLrcLibSynced(title: String, artist: String): List<LyricLine>? {
        return try {
            val query = URLEncoder.encode("$artist $title", "UTF-8")
            val url = URL("https://lrclib.net/api/search?q=$query")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "IanPlayer/1.0")
            connection.connectTimeout = 5000

            val response = connection.inputStream.bufferedReader().readText()
            val jsonArray = JSONArray(response)
            if (jsonArray.length() == 0) return null

            val firstResult = jsonArray.getJSONObject(0)
            val synced = firstResult.optString("syncedLyrics")
            if (synced.isBlank()) return null

            parseLrc(synced)
        } catch (e: Exception) {
            Log.e("LyricRepo", "Error fetching synced lyric from LRCLIB", e)
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

            val response = connection.inputStream.bufferedReader().readText()
            val jsonArray = JSONArray(response)
            if (jsonArray.length() == 0) return null

            jsonArray.getJSONObject(0).optString("plainLyrics").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.e("LyricRepo", "Error fetching plain lyric from LRCLIB", e)
            null
        }
    }

    // ==========================================
    // SOURCE 2: SOME RANDOM API (Mantap buat fallback)
    // ==========================================
    private fun fetchFromSomeRandomApi(title: String, artist: String): String? {
        return try {
            val query = URLEncoder.encode("$title $artist", "UTF-8")
            val url = URL("https://some-random-api.com/lyrics?title=$query")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            connection.connectTimeout = 5000

            if (connection.responseCode != 200) return null

            val response = connection.inputStream.bufferedReader().readText()
            JSONObject(response).optString("lyrics").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.e("LyricRepo", "Error fetching from SomeRandomAPI", e)
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

            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null

            val response = connection.inputStream.bufferedReader().readText()
            JSONObject(response).optString("lyrics").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.e("LyricRepo", "Error fetching from lyrics.ovh", e)
            null
        }
    }

    // ==========================================
    // HELPER: Lrc Parser
    // ==========================================
    private fun parseLrc(lrc: String): List<LyricLine> {
        val regex = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})]\s*(.*)""")
        return lrc.lines().mapNotNull { line ->
            val match = regex.find(line) ?: return@mapNotNull null
            val (min, sec, ms, text) = match.destructured
            val timeMs = min.toLong() * 60000 + sec.toLong() * 1000 + ms.padEnd(3, '0').take(3).toLong()
            LyricLine(timeMs, text.trim())
        }.filter { it.text.isNotBlank() }
    }
}