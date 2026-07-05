package com.ianocent.musicplayer.data

import android.util.Log
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class LyricRepository {

    fun fetchLyric(title: String, artist: String): String? {
        return try {
            val query = URLEncoder.encode("$artist $title", "UTF-8")
            val url = URL("https://lrclib.net/api/search?q=$query")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000

            val response = connection.inputStream.bufferedReader().readText()
            Log.d("LyricRepo", "Response: $response")

            val jsonArray = JSONArray(response)
            if (jsonArray.length() == 0) {
                Log.d("LyricRepo", "No results for: $artist $title")
                return null
            }

            val firstResult = jsonArray.getJSONObject(0)
            firstResult.optString("plainLyrics").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.e("LyricRepo", "Error fetching lyric", e)
            null
        }
    }
}