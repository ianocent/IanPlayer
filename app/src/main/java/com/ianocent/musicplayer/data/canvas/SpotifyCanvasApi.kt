package com.ianocent.musicplayer.data.canvas

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Spotify canvas data access.
 *
 * Two upstreams:
 *  1. [searchTrack] — internal web-player GraphQL (`api-partner.spotify.com`)
 *     with an anonymous token. Resolves a text query to Spotify track
 *     candidates (uri/title/artists/duration) so both stream and local songs
 *     can find their Spotify counterpart. The persisted-query hash mirrors the
 *     current public web-player bundle; if Spotify rotates it the request
 *     degrades to "no candidates" and the canvas simply never shows.
 *  2. [fetchCanvas] — the canvas itself. Public canvaz REST proxy by track id.
 *     The old `canvas.canvasapi.workers.dev` worker is dead (404), verified
 *     alive in 2026: `spotify-canva.vercel.app` (open-source deploy:
 *     github.com/punisher-303/Spotify-Canva). Swap [CANVAS_PROXY_BASE] for a
 *     self-hosted copy if this instance goes away.
 */
object SpotifyCanvasApi {
    private const val PATHFINDER_URL = "https://api-partner.spotify.com/pathfinder/v1/query"
    private const val SEARCH_TRACKS_SHA =
        "b02683192a98dde7966b5e6655a79eeb62713eab703eda9902c932818dd52751"
    /** Spotify's own canvaz endpoint; needs a token minted with the user's sp_dc cookie. */
    private const val CANVAZ_URL = "https://spclient.wg.spotify.com/canvaz-cache/v0/canvases"
    private const val CANVAS_PROXY_BASE = "https://spotify-canva.vercel.app/api/canvas"

    private val BROWSER_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36"

    /** Mobile Spotify UA — spclient canvaz accepts this per reference impl. */
    private const val SPOTIFY_MOBILE_UA = "Spotify/9.0.34.593 iOS/18.4 (iPhone15,3)"

    data class SpotifyTrackCandidate(
        val spotifyId: String,
        val title: String,
        val artist: String,
        val durationMs: Long,
    )

    /**
     * GraphQL `searchTracks` (web-player persisted query). Best-effort: returns
     * an empty list on any failure so callers degrade to "no canvas".
     */
    suspend fun searchTrack(query: String, spDc: String?): List<SpotifyTrackCandidate> =
        withContext(Dispatchers.IO) {
            try {
                val token = SpotifyTokenManager.getAccessToken(spDc)
                if (token == null) {
                    CanvasLog.e("SEARCH: no token available, search aborted")
                    return@withContext emptyList()
                }
                val variables = JSONObject()
                    .put("searchTerm", query)
                    .put("offset", 0)
                    .put("limit", 5)
                    .put("numberOfTopResults", 5)
                    .put("includeAudiobooks", false)
                val body = JSONObject()
                    .put("operationName", "searchTracks")
                    .put("variables", variables)
                    .put(
                        "extensions",
                        JSONObject().put(
                            "persistedQuery",
                            JSONObject().put("version", 1).put("sha256Hash", SEARCH_TRACKS_SHA)
                        )
                    )
                    .toString()

                val conn = URL(PATHFINDER_URL).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.setRequestProperty("Accept", "application/json")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("User-Agent", BROWSER_UA)
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val respBody = readBody(conn)
                CanvasLog.d("SEARCH: http=${conn.responseCode} bodyLen=${respBody?.length ?: -1}")
                if (conn.responseCode !in 200..299 || respBody.isNullOrBlank()) {
                    CanvasLog.e("SEARCH: fail http=${conn.responseCode} body=${respBody?.take(300)}")
                    SpotifyTokenManager.invalidate()
                    return@withContext emptyList()
                }
                val parsed = parseSearchResponse(respBody)
                CanvasLog.d("SEARCH: parsed ${parsed.size} candidates -> " +
                    parsed.joinToString(" | ") { "${it.spotifyId} '${it.title}'/${it.artist}/${it.durationMs}ms" })
                return@withContext parsed
            } catch (e: Exception) {
                if (e is kotlin.coroutines.cancellation.CancellationException) throw e
                CanvasLog.e("SEARCH: error", e)
                emptyList()
            }
        }

    private fun parseSearchResponse(raw: String): List<SpotifyTrackCandidate> = try {
        val json = JSONObject(raw)
        val items = json
            .getJSONObject("data")
            .getJSONObject("searchV2")
            .getJSONObject("tracksV2")
            .getJSONArray("items")
        (0 until items.length()).mapNotNull { i ->
            val data = items.getJSONObject(i)
                ?.optJSONObject("item")
                ?.optJSONObject("data") ?: return@mapNotNull null
            val uri = data.optString("uri")
            if (!uri.startsWith("spotify:track:")) return@mapNotNull null
            val durationMs = data.optJSONObject("duration")?.optLong("totalMilliseconds", -1L) ?: -1L
            val artists = data.optJSONObject("artists")
                ?.optJSONArray("items")
                ?.let { arr ->
                    (0 until arr.length()).mapNotNull { k ->
                        arr.getJSONObject(k)?.optJSONObject("profile")?.optString("name")
                    }.filter { it.isNotBlank() }
                }.orEmpty()
            SpotifyTrackCandidate(
                spotifyId = uri.removePrefix("spotify:track:"),
                title = data.optString("name"),
                artist = artists.joinToString(", "),
                durationMs = durationMs,
            )
        }
    } catch (e: Exception) {
        CanvasLog.d("SEARCH: parse failed: ${e.message}")
        emptyList()
    }

    /**
     * Canvas URL lookup by Spotify track id via the canvaz REST proxy.
     * Response schema: {"canvasesList":[{"canvasUrl":...,"artist":{...}}]}.
     * Always returns a value; never throws.
     */
    /**
     * Canvas URL lookup by Spotify track id. Primary path: Spotify's own
     * `spclient.wg.spotify.com/canvaz-cache/v0/canvases` protobuf endpoint with
     * a token minted from the user's sp_dc cookie. Falls back to the public
     * REST proxy (may 500 — its server-side sp_dc is expired). Always returns
     * a value; never throws.
     */
    suspend fun fetchCanvas(trackId: String, spDc: String?): CanvasMatch = withContext(Dispatchers.IO) {
        val dc = spDc?.trim().orEmpty()
        if (dc.isEmpty()) {
            CanvasLog.d("CANVAS: no sp_dc -> skipped trackId=$trackId")
            CanvasMatch(ok = false, error = "no_sp_dc")
        } else try {
            val token = SpotifyTokenManager.getAccessToken(dc)
            if (token == null) {
                CanvasLog.d("CANVAS: no token for canvaz trackId=$trackId")
                CanvasMatch(ok = false, error = "token_failed")
            } else {
                val conn = URL(CANVAZ_URL).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.requestMethod = "POST"
                conn.doOutput = true
                // Exact header set from the working reference implementation
                // (Paxsenix0/Spotify-Canvas-API services/spotifyCanvasService.js)
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.setRequestProperty("Accept", "application/protobuf")
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.setRequestProperty("Accept-Language", "en")
                conn.setRequestProperty("User-Agent", SPOTIFY_MOBILE_UA)
                // NB: no manual Accept-Encoding — HttpURLConnection inserts gzip
                // and decompresses transparently only when WE don't set it.
                conn.outputStream.use { it.write(canvazRequest(trackId)) }
                val bytes = readProtoBody(conn)
                CanvasLog.d("CANVAS: spclient http=${conn.responseCode} trackId=$trackId bytes=${bytes?.size ?: -1}")
                val parsed = bytes?.let { parseCanvazResponse(it) }
                if (conn.responseCode in 200..299 && parsed != null) parsed
                else tryProxyCanvas(trackId)
            }
        } catch (e: Exception) {
            if (e is kotlin.coroutines.cancellation.CancellationException) throw e
            CanvasLog.e("CANVAS: error for $trackId", e)
            tryProxyCanvas(trackId)
        }
    }

    /** Canvas entity entry — mirrors Paxsenix0 proto: Track message has only
     *  field 1 (track_uri). The extra type field breaks this schema. */
    private fun canvazRequest(trackId: String): ByteArray {
        val uri = "spotify:track:$trackId".toByteArray(Charsets.UTF_8)
        val entry = byteArrayOf(0x0A) + varint(uri.size) + uri
        return byteArrayOf(0x0A) + varint(entry.size) + entry
    }

    private fun varint(value: Int): ByteArray {
        var v = value
        val out = java.io.ByteArrayOutputStream()
        while (true) {
            if (v < 0x80) break
            out.write(((v and 0x7F) or 0x80))
            v = v.ushr(7)
        }
        out.write(v)
        return out.toByteArray()
    }

    private fun readProtoBody(conn: HttpURLConnection): ByteArray? = try {
        conn.inputStream.use { it.readBytes() }
    } catch (e: Exception) {
        null
    }

    /**
     * The protobuf response embeds the canvas URL as a plain ASCII string
     * (https://canvaz.scdn.co/upload/.../xxx.cnvs.mp4). A byte-level string
     * scan is far more robust than hand-decoding the nested message schema.
     */
    private fun parseCanvazResponse(bytes: ByteArray): CanvasMatch {
        val text = String(bytes, Charsets.ISO_8859_1)
        val urls = Regex("https://[!-~]+").findAll(text).map { it.value }
            .filter { it.contains(".mp4") || it.contains(".cnvs.") }
            .toList()
        if (urls.isEmpty()) {
            CanvasLog.d("CANVAS: spclient no canvas in response")
            return CanvasMatch(ok = true, matched = false, confidence = 0f, source = "canvaz-spclient")
        }
        val url = urls.first()
        CanvasLog.d("CANVAS: FOUND url=$url")
        return CanvasMatch(
            ok = true,
            matched = true,
            confidence = 1f,
            canvasUrl = url,
            canvasType = "VIDEO",
            urlExpiresAt = System.currentTimeMillis() + 12L * 3600_000L,
            source = "canvaz-spclient"
        )
    }

    /** Last-resort fallback to the public proxy (often 500 when its sp_dc dies). */
    private suspend fun tryProxyCanvas(trackId: String): CanvasMatch = withContext(Dispatchers.IO) {
        try {
            val url = URL("$CANVAS_PROXY_BASE?trackId=${URLEncoder.encode(trackId, "UTF-8")}")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", BROWSER_UA)
            val respBody = readBody(conn) ?: ""
            CanvasLog.d("CANVAS: proxy http=${conn.responseCode} trackId=$trackId preview=${respBody.take(120)}")
            if (conn.responseCode !in 200..299 || respBody.isBlank()) {
                CanvasMatch(ok = false, error = "canvas_http_${conn.responseCode}")
            } else {
                parseCanvasResponse(respBody, trackId)
            }
        } catch (e: Exception) {
            if (e is kotlin.coroutines.cancellation.CancellationException) throw e
            CanvasMatch(ok = false, error = e.message, source = "canvaz-proxy")
        }
    }

    /** Proxy reachable but the track has no canvas. */
    private fun noCanvasResult(trackId: String): CanvasMatch {
        CanvasLog.d("CANVAS: no canvas for $trackId")
        return CanvasMatch(ok = true, matched = false, confidence = 0f, spotifyId = trackId)
    }

    private fun parseCanvasResponse(raw: String, trackId: String): CanvasMatch {
        val json = try { JSONObject(raw) } catch (e: Exception) {
            CanvasLog.e("CANVAS: bad JSON: ${raw.take(200)}", e)
            return CanvasMatch(ok = false, error = "canvas_json", source = "canvaz-proxy")
        }
        val list = json.optJSONArray("canvasesList") ?: return noCanvasResult(trackId)
        if (list.length() == 0) return noCanvasResult(trackId)
        val first = list.getJSONObject(0)
        val canvasUrl = first.optString("canvasUrl")
        if (canvasUrl.isNullOrBlank()) return noCanvasResult(trackId)
        val artist = first.optJSONObject("artist")?.optString("artistName")
        CanvasLog.d("CANVAS: FOUND url=$canvasUrl artist=$artist")
        return CanvasMatch(
            ok = true,
            matched = true,
            confidence = 1f,
            spotifyId = trackId,
            canvasUrl = canvasUrl,
            canvasType = "VIDEO",
            artistName = artist,
            urlExpiresAt = System.currentTimeMillis() + 12L * 3600_000L,
            source = "canvaz-proxy"
        )
    }

    private fun readBody(conn: HttpURLConnection): String? = try {
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        stream?.bufferedReader()?.use { it.readText() }
    } catch (_: Exception) {
        null
    }
}
