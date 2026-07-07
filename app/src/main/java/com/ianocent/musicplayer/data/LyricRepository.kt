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
        val lrcLibResult = fetchFromLrcLibSynced(title, artist)
        if (!lrcLibResult.isNullOrEmpty()) return lrcLibResult

        val lrcMuxResult = fetchFromLrcMuxSynced(title, artist)
        if (!lrcMuxResult.isNullOrEmpty()) return lrcMuxResult

        return null
    }

    fun fetchPlainLyric(title: String, artist: String): String? {
        val lrcLibResult = fetchFromLrcLibPlain(title, artist)
        if (!lrcLibResult.isNullOrBlank()) return lrcLibResult

        val lrcMuxResult = fetchFromLrcMuxPlain(title, artist)
        if (!lrcMuxResult.isNullOrBlank()) return lrcMuxResult

        val sraResult = fetchFromSomeRandomApi(title, artist)
        if (!sraResult.isNullOrBlank()) return sraResult

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
            connection.connectTimeout = 8000

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
            Log.e("LyricRepo", "Error fetching synced lyric from lrcmux", e)
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
            connection.connectTimeout = 8000

            if (connection.responseCode != 200) return null

            connection.inputStream.bufferedReader().readText().takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.e("LyricRepo", "Error fetching plain lyric from lrcmux", e)
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