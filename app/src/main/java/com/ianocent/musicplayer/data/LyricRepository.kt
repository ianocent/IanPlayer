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
        // 1. Coba dari LRCLIB (Utama)
        val lrcLibResult = fetchFromLrcLibSynced(title, artist)
        if (lrcLibResult != null) return lrcLibResult

        // 2. Slot untuk YouTube Music (Nanti logic dari YTMDesktop masuk sini)
        // return fetchFromYoutubeMusicSynced(title, artist)

        return null
    }

    fun fetchPlainLyric(title: String, artist: String): String? {
        // 1. Coba dari LRCLIB (Utama)
        val lrcLibResult = fetchFromLrcLibPlain(title, artist)
        if (lrcLibResult != null) return lrcLibResult

        // 2. Fallback 1: Coba dari api.lyrics.ovh kalau LRCLIB kosong
        val ovhResult = fetchFromLyricsOvh(title, artist)
        if (ovhResult != null) return ovhResult

        // 3. Slot untuk YouTube Music (Nanti logic dari YTMDesktop masuk sini)
        // return fetchFromYoutubeMusicPlain(title, artist)

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
    // SOURCE 2: LYRICS.OVH (Fallback Plain Lyric)
    // ==========================================
    private fun fetchFromLyricsOvh(title: String, artist: String): String? {
        return try {
            val encArtist = URLEncoder.encode(artist, "UTF-8")
            val encTitle = URLEncoder.encode(title, "UTF-8")
            val url = URL("https://api.lyrics.ovh/v1/$encArtist/$encTitle")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000

            // Pastikan response sukses (200 OK)
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null

            val response = connection.inputStream.bufferedReader().readText()
            val jsonObject = JSONObject(response)

            jsonObject.optString("lyrics").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.e("LyricRepo", "Error fetching lyric from lyrics.ovh", e)
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