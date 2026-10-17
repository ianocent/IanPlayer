package com.ianocent.musicplayer.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Collections
import java.util.LinkedHashMap

/**
 * Best-effort catalog metadata for a downloaded track. Downloads start from
 * stream-provider rows whose only art is a letterboxed YouTube video thumb and
 * whose album/genre/year are usually "Unknown" — this fills those in from a
 * real music catalog (iTunes primary, Deezer fallback) so the embedded tags
 * and cover art match the actual release.
 */
data class TrackMetadata(
    val album: String? = null,
    val albumArtist: String? = null,
    val genre: String? = null,
    val year: String? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val totalTracks: Int? = null,
    val artUrl: String? = null,
    val isrc: String? = null,
    val source: String = ""
) {
    fun isEmpty(): Boolean = album == null && genre == null && year == null &&
        trackNumber == null && artUrl == null && albumArtist == null
}

/**
 * Title/artist → catalog metadata lookup. Pure network + JSON, no Android
 * dependency, so it stays testable and callable from the download receiver.
 */
object MusicMetadataService {

    private const val UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"

    // Small bounded cache so repeated downloads of the same track (or retries)
    // don't re-hit the network. Values may be null (negative cache).
    private val cache: MutableMap<String, TrackMetadata?> = Collections.synchronizedMap(
        object : LinkedHashMap<String, TrackMetadata?>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TrackMetadata?>?): Boolean =
                size > 64
        }
    )

    suspend fun enrich(song: Song): TrackMetadata? = withContext(Dispatchers.IO) {
        val key = "${normalize(song.artist)}|${normalize(song.title)}"
        if (key == "|") return@withContext null
        synchronized(cache) { cache[key] }?.let { return@withContext it }

        val itunes = lookupItunes(song.title, song.artist)
        val deezer = if (itunes == null || itunes.artUrl == null) lookupDeezer(song.title, song.artist) else null

        val merged = merge(itunes, deezer)
        synchronized(cache) { cache[key] = merged }
        merged
    }

    private fun merge(a: TrackMetadata?, b: TrackMetadata?): TrackMetadata? {
        if (a == null && b == null) return null
        val combined = TrackMetadata(
            album = a?.album ?: b?.album,
            albumArtist = a?.albumArtist ?: b?.albumArtist,
            genre = a?.genre ?: b?.genre,
            year = a?.year ?: b?.year,
            trackNumber = a?.trackNumber ?: b?.trackNumber,
            discNumber = a?.discNumber ?: b?.discNumber,
            totalTracks = a?.totalTracks ?: b?.totalTracks,
            artUrl = a?.artUrl ?: b?.artUrl,
            isrc = a?.isrc ?: b?.isrc,
            source = a?.source ?: b?.source ?: ""
        )
        return if (combined.isEmpty()) null else combined
    }

    private fun lookupItunes(title: String, artist: String): TrackMetadata? {
        val term = listOf(artist, title).filter { it.isNotBlank() }.joinToString(" ")
        if (term.isBlank()) return null
        val url = "https://itunes.apple.com/search?term=${enc(term)}&entity=song&limit=8&media=music"
        val json = getJson(url) ?: return null
        val results = json.optJSONArray("results") ?: return null

        val targetTitle = normalize(title)
        val targetArtist = normalize(artist)

        var best: JSONObject? = null
        var bestScore = 0
        for (i in 0 until results.length()) {
            val r = results.optJSONObject(i) ?: continue
            val rTitle = normalize(r.optString("trackName"))
            val rArtist = normalize(r.optString("artistName"))
            if (rTitle.isEmpty()) continue

            var score = 0
            if (targetTitle.isNotEmpty()) {
                if (rTitle == targetTitle) score += 100
                else if (rTitle.contains(targetTitle) || targetTitle.contains(rTitle)) score += 55
            }
            if (targetArtist.isNotEmpty()) {
                if (rArtist == targetArtist) score += 70
                else if (rArtist.contains(targetArtist) || targetArtist.contains(rArtist)) score += 40
            }
            val blob = "$rTitle $rArtist"
            if (blob.contains("karaoke") || blob.contains("made famous") || blob.contains("cover version")) {
                score -= 200
            }
            if (score > bestScore) {
                bestScore = score
                best = r
            }
        }

        if (best == null || bestScore < 40) return null
        return parseItunes(best)
    }

    private fun parseItunes(r: JSONObject): TrackMetadata {
        val art = r.optString("artworkUrl100").takeIf { it.isNotBlank() }
            ?.replace("100x100bb", "3000x3000bb")
        val albumArtist = r.optString("collectionArtistName").takeIf { it.isNotBlank() }
        val trackCount = r.optInt("trackCount", 0).takeIf { it > 0 }
        return TrackMetadata(
            album = r.optString("collectionName").takeIf { it.isNotBlank() },
            albumArtist = albumArtist ?: r.optString("artistName").takeIf { it.isNotBlank() },
            genre = r.optString("primaryGenreName").takeIf { it.isNotBlank() },
            year = r.optString("releaseDate").take(4).takeIf { it.length == 4 && it.all(Char::isDigit) },
            trackNumber = r.optInt("trackNumber", 0).takeIf { it > 0 },
            discNumber = r.optInt("discNumber", 0).takeIf { it > 0 },
            totalTracks = trackCount,
            artUrl = art,
            isrc = null,
            source = "iTunes"
        )
    }

    private fun lookupDeezer(title: String, artist: String): TrackMetadata? {
        if (title.isBlank()) return null
        val q = buildString {
            if (artist.isNotBlank()) append("artist:\"$artist\" ")
            append("track:\"$title\"")
        }
        val url = "https://api.deezer.com/search?q=${enc(q)}&limit=5"
        val json = getJson(url) ?: return null
        val data = json.optJSONArray("data") ?: return null

        val targetTitle = normalize(title)
        val targetArtist = normalize(artist)

        var best: JSONObject? = null
        var bestScore = 0
        for (i in 0 until data.length()) {
            val d = data.optJSONObject(i) ?: continue
            val dTitle = normalize(d.optString("title"))
            if (dTitle.isEmpty()) continue
            val dArtist = normalize(d.optJSONObject("artist")?.optString("name") ?: "")

            var score = 0
            if (dTitle == targetTitle) score += 100
            else if (dTitle.contains(targetTitle) || targetTitle.contains(dTitle)) score += 55
            if (targetArtist.isNotEmpty()) {
                if (dArtist == targetArtist) score += 70
                else if (dArtist.contains(targetArtist) || targetArtist.contains(dArtist)) score += 40
            }
            if (score > bestScore) {
                bestScore = score
                best = d
            }
        }
        if (best == null || bestScore < 40) return null

        val album = best.optJSONObject("album")
        val art = album?.optString("cover_xl")?.takeIf { it.isNotBlank() }
            ?: album?.optString("cover_big")?.takeIf { it.isNotBlank() }

        return TrackMetadata(
            album = album?.optString("title")?.takeIf { it.isNotBlank() },
            albumArtist = best.optJSONObject("artist")?.optString("name")?.takeIf { it.isNotBlank() },
            genre = null,
            year = null,
            trackNumber = null,
            discNumber = null,
            totalTracks = null,
            artUrl = art,
            isrc = best.optString("isrc").takeIf { it.isNotBlank() },
            source = "Deezer"
        )
    }

    private fun getJson(url: String): JSONObject? = try {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", UA)
        conn.setRequestProperty("Accept", "application/json")
        conn.connectTimeout = 6000
        conn.readTimeout = 6000
        if (conn.responseCode != 200) {
            Timber.d("MusicMetadataService HTTP ${conn.responseCode} for $url")
            null
        } else {
            JSONObject(conn.inputStream.bufferedReader().readText())
        }
    } catch (e: Exception) {
        Timber.d("MusicMetadataService lookup failed: ${e.message}")
        null
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    /** Lowercases and keeps only letters/digits so "Song (Remastered)" ≈ "songremastered". */
    private fun normalize(s: String?): String =
        s?.lowercase()?.filter { it.isLetterOrDigit() } ?: ""
}
