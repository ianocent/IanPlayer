package com.ianocent.musicplayer.data

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class YTMusicRepository {

    // Opsi 1 (Paling stabil saat ini):
    private val baseUrl = "https://pipedapi.tokhmi.xyz"

    // Kalau Opsi 1 lagi ngambek suatu saat nanti, lu bisa ganti ke:
    // private val baseUrl = "https://pipedapi.smnz.de"
    // private val baseUrl = "https://api.piped.projectsegfau.lt"

    suspend fun searchSongs(query: String): List<Song> = withContext(Dispatchers.IO) {
        try {
            val encQuery = URLEncoder.encode(query, "UTF-8")

            // Endpoint 'filter=music_songs' ini rahasianya biar yang keluar murni lagu dari YT Music
            val searchUrl = URL("$baseUrl/search?q=$encQuery&filter=music_songs")
            val conn = searchUrl.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000

            if (conn.responseCode != 200) return@withContext emptyList()

            val response = conn.inputStream.bufferedReader().readText()
            val items = JSONObject(response).getJSONArray("items")

            val tasks = mutableListOf<Deferred<Song?>>()

            // Kita limit 10 lagu aja biar server Piped-nya ga nge-block (rate limit) IP lu
            val limit = if (items.length() > 10) 10 else items.length()

            for (i in 0 until limit) {
                val item = items.getJSONObject(i)
                tasks.add(async {
                    try {
                        val urlPath = item.optString("url", "")
                        val videoId = urlPath.replace("/watch?v=", "")
                        if (videoId.isBlank()) return@async null

                        // Ngambil Metadata text
                        val title = item.optString("title", "Unknown Title")
                        val artist = item.optString("uploaderName", "Unknown Artist")
                        val durationSec = item.optLong("duration", 0L)
                        val durationMs = durationSec * 1000L
                        val artUrl = item.optString("thumbnail", "")

                        // Hit API kedua buat dapetin Direct Audio Stream-nya
                        val streamUrlReq = URL("$baseUrl/streams/$videoId")
                        val streamConn = streamUrlReq.openConnection() as HttpURLConnection
                        streamConn.requestMethod = "GET"
                        streamConn.connectTimeout = 5000

                        if (streamConn.responseCode != 200) return@async null

                        val streamRes = streamConn.inputStream.bufferedReader().readText()
                        val audioStreams = JSONObject(streamRes).getJSONArray("audioStreams")

                        var bestAudioUrl: String? = null

                        // Cari stream format m4a/mp4 (Paling stabil di ExoPlayer). Kalau ga ada, ambil webm.
                        for (j in 0 until audioStreams.length()) {
                            val stream = audioStreams.getJSONObject(j)
                            val mimeType = stream.optString("mimeType", "")
                            if (mimeType.contains("audio/mp4") || mimeType.contains("audio/m4a")) {
                                bestAudioUrl = stream.optString("url")
                                break // Langsung ambil yang pertama ketemu karena udah paling pas
                            } else if (mimeType.contains("audio/webm") && bestAudioUrl == null) {
                                bestAudioUrl = stream.optString("url")
                            }
                        }

                        if (bestAudioUrl != null) {
                            Song(
                                id = videoId.hashCode().toLong(),
                                title = title,
                                artist = artist,
                                duration = durationMs,
                                uri = Uri.parse(bestAudioUrl),
                                isStream = true,
                                remoteArtUrl = artUrl
                            )
                        } else null
                    } catch (e: Exception) { null }
                })
            }

            // Tunggu semua lagu selesai diekstrak link-nya
            tasks.awaitAll().filterNotNull()
        } catch (e: Exception) {
            Log.e("YTMusicRepo", "Error fetching from Piped API", e)
            emptyList()
        }
    }
}