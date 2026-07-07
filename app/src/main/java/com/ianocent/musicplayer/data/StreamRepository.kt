package com.ianocent.musicplayer.data

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class StreamRepository {
    // Host bawaan Audius (Stabil dan tahan banting)
    private val audiusHost = "https://discoveryprovider.audius.co"
    private val appName = "IanPlayer" // Audius cuma minta nama app aja sebagai 'syarat'

    suspend fun searchSongs(query: String): List<Song> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        try {
            val encQuery = URLEncoder.encode(query, "UTF-8")
            val url = URL("$audiusHost/v1/tracks/search?query=$encQuery&app_name=$appName")

            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000

            if (conn.responseCode != 200) return@withContext emptyList()

            val response = conn.inputStream.bufferedReader().readText()
            val data = JSONObject(response).getJSONArray("data")

            val songs = mutableListOf<Song>()
            for (i in 0 until data.length()) {
                val item = data.getJSONObject(i)

                // Pastikan lagu bisa di-stream
                if (!item.optBoolean("is_streamable", true)) continue

                val id = item.optString("id")
                val title = item.optString("title", "Unknown Title")
                val artist = item.optJSONObject("user")?.optString("name", "Unknown Artist") ?: "Unknown Artist"

                // Audius nyimpen durasi dalam detik, kita ubah ke millisecond
                val durationMs = item.optLong("duration", 0L) * 1000L

                // Ambil artwork (Pilih yang 480x480 biar HD)
                val artwork = item.optJSONObject("artwork")
                val artUrl = artwork?.optString("480x480") ?: artwork?.optString("150x150")

                // URL Streaming langsung (Tinggal dimakan sama ExoPlayer)
                val streamUrl = "$audiusHost/v1/tracks/$id/stream?app_name=$appName"

                songs.add(
                    Song(
                        id = id.hashCode().toLong(), // Convert string ID ke Long
                        title = title,
                        artist = artist,
                        duration = durationMs,
                        uri = Uri.parse(streamUrl),
                        isStream = true,
                        remoteArtUrl = artUrl
                    )
                )
            }
            songs
        } catch (e: Exception) {
            Log.e("StreamRepo", "Audius fetch gagal: ${e.message}")
            emptyList()
        }
    }
}