package com.ianocent.musicplayer.data

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class StreamRepository {

    private val saavnInstances = listOf(
        "https://saavn.dev",
        "https://saavn.me",
        "https://jiosaavn-api.vercel.app"
    )

    private suspend fun fetchApi(path: String): String? {
        for (baseUrl in saavnInstances) {
            try {
                val url = URL("$baseUrl$path")
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("Accept", "application/json")
                conn.requestMethod = "GET"
                conn.connectTimeout = 4000
                conn.readTimeout = 4000

                if (conn.responseCode != 200) continue

                val raw = conn.inputStream.bufferedReader().readText()
                if (raw.isBlank()) continue

                val trimmed = raw.trimStart()
                if (trimmed.startsWith("{") || trimmed.startsWith("[")) return raw

                Log.w("StreamRepo", "Instance $baseUrl returned non-JSON response")
            } catch (e: Exception) {
                Log.w("StreamRepo", "Instance $baseUrl failed: ${e.message}")
            }
        }
        return null
    }

    suspend fun searchSongs(query: String): List<Song> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        try {
            val encQuery = URLEncoder.encode(query, "UTF-8")
            val response = fetchApi("/api/search/songs?query=$encQuery&limit=15") ?: return@withContext emptyList()

            val json = JSONObject(response)
            val data = json.optJSONObject("data") ?: return@withContext emptyList()
            val results = data.optJSONArray("results") ?: return@withContext emptyList()

            val songs = mutableListOf<Song>()
            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)

                val id = item.optString("id", "")
                val title = item.optString("name", "Unknown Title")
                val artists = item.optJSONObject("artists")
                val primaryArtists = artists?.optJSONArray("primary")
                val artist = if (primaryArtists != null && primaryArtists.length() > 0) {
                    primaryArtists.getJSONObject(0).optString("name", "Unknown Artist")
                } else "Unknown Artist"

                val durationSec = item.optLong("duration", 0L)
                val durationMs = durationSec * 1000L

                val albumObj = item.optJSONObject("album")
                val album = albumObj?.optString("name", "Unknown Album") ?: "Unknown Album"

                val images = item.optJSONArray("image")
                val artUrl = if (images != null && images.length() > 0) {
                    images.getJSONObject(images.length() - 1).optString("url", "")
                } else ""

                val downloadUrls = item.optJSONArray("downloadUrl")
                val streamUrl = if (downloadUrls != null && downloadUrls.length() > 0) {
                    downloadUrls.getJSONObject(downloadUrls.length() - 1).optString("url", "")
                } else ""

                if (streamUrl.isNotBlank() && id.isNotBlank()) {
                    songs.add(
                        Song(
                            id = id.hashCode().toLong(),
                            title = title,
                            artist = artist,
                            album = album,
                            duration = durationMs,
                            uri = Uri.parse(streamUrl),
                            isStream = true,
                            remoteArtUrl = artUrl,
                            remoteId = id
                        )
                    )
                }
            }
            songs
        } catch (e: Exception) {
            Log.e("StreamRepo", "JioSaavn fetch gagal: ${e.message}")
            emptyList()
        }
    }
}
