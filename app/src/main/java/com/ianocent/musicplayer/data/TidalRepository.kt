package com.ianocent.musicplayer.data

import android.net.Uri
import org.json.JSONObject
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TidalRepository {
    companion object {
        private const val API_BASE = "https://api.tidal.com/v1"
        // Publicly known Tidal client token often used in open-source projects
        private const val TOKEN = "uXSp7m8679Yn9W9o" 
    }

    suspend fun searchSongs(query: String): List<Song> = withContext(Dispatchers.IO) {
        try {
            val url = URL("$API_BASE/search/tracks?query=${java.net.URLEncoder.encode(query, "UTF-8")}&limit=20&countryCode=ID")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("x-tidal-token", TOKEN)
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            if (conn.responseCode != 200) return@withContext emptyList()
            
            val raw = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(raw)
            val items = json.optJSONArray("items") ?: return@withContext emptyList()
            
            val results = mutableListOf<Song>()
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val id = item.getLong("id")
                val title = item.getString("title")
                val artist = item.optJSONArray("artists")?.optJSONObject(0)?.optString("name") ?: "Unknown Artist"
                val album = item.optJSONObject("album")?.optString("title") ?: "Unknown Album"
                val duration = item.getLong("duration") * 1000L
                val cover = item.optJSONObject("album")?.optString("cover")
                
                val artUrl = if (cover != null) {
                    "https://resources.tidal.com/images/${cover.replace("-", "/")}/1280x1280.jpg"
                } else null

                results.add(Song(
                    id = id,
                    title = title,
                    artist = artist,
                    album = album,
                    duration = duration,
                    uri = Uri.parse(StreamSources.TIDAL_PLACEHOLDER_PREFIX + "track/$id"),
                    isStream = true,
                    remoteArtUrl = artUrl,
                    remoteId = id.toString(),
                    source = StreamSources.TIDAL
                ))
            }
            results
        } catch (e: Exception) {
            Timber.e(e, "Tidal search failed")
            emptyList()
        }
    }

    // Tidal tracks usually require auth for actual stream URLs.
    // Without auth there is no direct URL to return; the TidalStreamResolver
    // adapter falls back to searching the same song on YouTube.
}
