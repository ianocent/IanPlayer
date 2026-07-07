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

    // Public Invidious instances yang masih aktif & stabil (di-update 2026)
    // Invidious lebih jarang kena rate-limit karena terdistribusi.
    private val invidiousInstances = listOf(
        "https://iv.datura.network",
        "https://iv.nboeck.de",
        "https://iv.nboeck.de",
        "https://yt.artemislena.eu",
        "https://iv.datura.network",
        "https://y.com.sb",
        "https://iv.nboeck.de",
        "https://iv.datura.network"
    ).distinct() // hapus duplikat kalau ada

    private val userAgent = "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

    suspend fun searchSongs(query: String): List<Song> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val encQuery = URLEncoder.encode(query, "UTF-8")
        var finalSongs = emptyList<Song>()

        for (baseUrl in invidiousInstances) {
            try {
                Log.d("StreamRepo", "Mencoba Invidious: $baseUrl")

                // type=video + fields minimal biar response ringan
                val searchUrl = URL("$baseUrl/api/v1/search?q=$encQuery&type=video&fields=videoId,title,author,lengthSeconds,authorId,videoThumbnails")
                val conn = openJsonConnection(searchUrl)

                if (conn.responseCode != 200) {
                    Log.e("StreamRepo", "Ditolak (Code: ${conn.responseCode})")
                    continue
                }

                val response = conn.inputStream.bufferedReader().readText()
                if (!response.trim().startsWith("[")) {
                    Log.e("StreamRepo", "Bukan JSON array, skip")
                    continue
                }

                val items = org.json.JSONArray(response)
                val songs = mutableListOf<Song>()

                for (i in 0 until items.length()) {
                    try {
                        val item = items.getJSONObject(i)
                        val videoId = item.optString("videoId", "")
                        if (videoId.isBlank()) continue

                        val title = item.optString("title", "Unknown Title").trim()
                        val artist = item.optString("author", "Unknown Artist").trim()
                        val durationMs = item.optLong("lengthSeconds", 0L) * 1000L

                        // Ambil thumbnail kualitas medium
                        val artUrl = extractThumbnail(item, videoId, baseUrl)

                        // Ambil direct audio stream URL dari endpoint /api/v1/videos
                        val audioUrl = fetchAudioUrl(baseUrl, videoId)
                            ?: continue

                        songs.add(
                            Song(
                                id = videoId.hashCode().toLong(),
                                title = title,
                                artist = artist,
                                duration = durationMs,
                                uri = Uri.parse(audioUrl),
                                isStream = true,
                                remoteArtUrl = artUrl
                            )
                        )
                    } catch (e: Exception) {
                        Log.w("StreamRepo", "Gagal parse item: ${e.message}")
                    }
                }

                if (songs.isNotEmpty()) {
                    finalSongs = songs
                    Log.d("StreamRepo", "BERHASIL pakai server: $baseUrl, ${songs.size} lagu 🔥")
                    break
                }

            } catch (e: Exception) {
                Log.e("StreamRepo", "Server $baseUrl mati: ${e.javaClass.simpleName} - ${e.message}")
            }
        }

        finalSongs
    }

    private fun fetchAudioUrl(baseUrl: String, videoId: String): String? {
        return try {
            val url = URL("$baseUrl/api/v1/videos/$videoId?fields=adaptiveFormats")
            val conn = openJsonConnection(url)
            if (conn.responseCode != 200) return null

            val response = conn.inputStream.bufferedReader().readText()
            if (!response.trim().startsWith("{")) return null

            val json = JSONObject(response)
            val formats = json.optJSONArray("adaptiveFormats") ?: return null

            var bestAudioUrl: String? = null
            var bestBitrate = 0

            for (j in 0 until formats.length()) {
                val fmt = formats.getJSONObject(j)
                val type = fmt.optString("type", "")
                val urlStr = fmt.optString("url", "")
                val bitrate = fmt.optInt("bitrate", 0)

                if (urlStr.isBlank()) continue
                if (!type.startsWith("audio/")) continue

                // Prioritaskan audio/mp4 (m4a) karena paling stabil di ExoPlayer
                val isPreferred = type.contains("audio/mp4") || type.contains("audio/m4a")
                if (isPreferred && bitrate > bestBitrate) {
                    bestAudioUrl = urlStr
                    bestBitrate = bitrate
                } else if (bestAudioUrl == null && bitrate > bestBitrate) {
                    bestAudioUrl = urlStr
                    bestBitrate = bitrate
                }
            }

            bestAudioUrl
        } catch (e: Exception) {
            Log.w("StreamRepo", "Gagal fetch audio URL: ${e.message}")
            null
        }
    }

    private fun extractThumbnail(item: JSONObject, videoId: String, baseUrl: String): String {
        val thumbs = item.optJSONArray("videoThumbnails")
        if (thumbs != null && thumbs.length() > 0) {
            // Cari thumbnail "medium" quality
            for (i in 0 until thumbs.length()) {
                val thumb = thumbs.getJSONObject(i)
                if (thumb.optString("quality") == "medium") {
                    return thumb.optString("url", "")
                }
            }
            // fallback ke thumbnail pertama
            return thumbs.getJSONObject(0).optString("url", "")
        }
        // fallback default youtube thumbnail
        return "https://img.youtube.com/vi/$videoId/mqdefault.jpg"
    }

    private fun openJsonConnection(url: URL): HttpURLConnection {
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 6000
        conn.readTimeout = 8000
        conn.setRequestProperty("User-Agent", userAgent)
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
        conn.setRequestProperty("Referer", "https://www.youtube.com/")
        conn.useCaches = false
        return conn
    }
}