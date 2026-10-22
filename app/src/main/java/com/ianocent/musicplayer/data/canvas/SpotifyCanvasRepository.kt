package com.ianocent.musicplayer.data.canvas

import android.content.Context
import com.ianocent.musicplayer.data.AppDatabase
import com.ianocent.musicplayer.data.SettingsStore
import com.ianocent.musicplayer.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max


/**
 * Orchestrator per the canvas spec (spotify_canvas_api_details.md §6.3–6.4):
 * cache-first resolve keyed by songKey, then text match (searchTracks) and
 * canvas lookup, with negative caching so canvas-less songs don't re-hit the
 * network. Everything is best-effort: any failure yields "no canvas" and the
 * existing Now Playing look stays untouched.
 */
object SpotifyCanvasRepository {
    /** Positive mappings live for a month; negatives a week; URLs 12h. */
    private const val POSITIVE_TTL_MS = 30L * 24L * 3600_000L
    private const val NEGATIVE_TTL_MS = 7L * 24L * 3600_000L
    private const val URL_TTL_MS = 12L * 3600_000L

    /** Duration tolerance for candidates (doc §6.3.3: ±5s requirement). */
    private const val DURATION_TOLERANCE_MS = 5000L

    /** Rejects mismatches below this confidence (remix/live/cover protection). */
    private const val MIN_CONFIDENCE = 0.60f
    private const val TITLE_SIM_THRESHOLD = 0.72f
    private const val ARTIST_SIM_THRESHOLD = 0.55f

    /**
     * Resolve the Spotify canvas for a song. Suspends until a verdict (which
     * may be "no canvas"); always returns a [CanvasMatch] — never throws.
     */
    suspend fun resolve(song: Song?, context: Context): CanvasMatch {
        if (song == null) {
            CanvasLog.d("RESOLVE: no song, skip")
            return CanvasMatch(ok = false, error = "no_song")
        }
        val songKey = songKeyFor(song)
        CanvasLog.d("RESOLVE: start songKey=$songKey title='${song.title}' artist='${song.artist}' duration=${song.duration}")
        return try {
            val spDc = SettingsStore.from(context).spotifySpDc
            withContext(Dispatchers.IO) {
                val dao = AppDatabase.getInstance(context).canvasCacheDao()
                val cached = dao.get(songKey)
                if (cached != null && !cacheRowExpired(cached)) {
                    CanvasLog.d(
                        "RESOLVE: CACHEHIT songKey=$songKey matched=${cached.matched} " +
                            "url=${cached.canvasUrl ?: "-"} source=${cached.source ?: "cache"}"
                    )
                    CanvasMatch(
                        ok = true,
                        matched = cached.matched,
                        confidence = cached.confidence,
                        spotifyId = cached.spotifyId,
                        canvasUrl = cached.canvasUrl,
                        canvasType = cached.canvasType,
                        artistName = cached.artistName,
                        urlExpiresAt = cached.urlExpiresAt,
                        source = cached.source ?: "cache"
                    )
                } else {
                    if (cached != null) {
                        CanvasLog.d("RESOLVE: cache expired songKey=$songKey, resolving fresh")
                    }
                    val match = resolveInternal(song, spDc)
                    val now = System.currentTimeMillis()
                    val expiresAt = when {
                        match.ok && match.matched -> now + POSITIVE_TTL_MS
                        match.ok -> now + NEGATIVE_TTL_MS
                        // no_sp_dc is a settings matter: don't re-probe every song flip
                        match.error == "no_sp_dc" -> now + 3600_000L
                        else -> now + 60_000L // soft error: retry soon
                    }
                    CanvasLog.d(
                        "RESOLVE: verdict songKey=$songKey matched=${match.matched} " +
                            "confidence=${"%.2f".format(match.confidence)} spotifyId=${match.spotifyId} " +
                            "url=${match.canvasUrl ?: "-"} source=${match.source} error=${match.error ?: "-"}"
                    )
                    dao.insert(
                        CanvasCacheEntity(
                            songKey = songKey,
                            matched = match.matched,
                            confidence = match.confidence,
                            spotifyId = match.spotifyId,
                            canvasUrl = match.canvasUrl,
                            canvasType = match.canvasType,
                            artistName = match.artistName,
                            urlExpiresAt = if (match.canvasUrl != null) {
                                match.urlExpiresAt ?: (now + URL_TTL_MS)
                            } else expiresAt,
                            source = match.source
                        )
                    )
                    match
                }
            }
        } catch (e: Exception) {
            if (e is kotlin.coroutines.cancellation.CancellationException) throw e
            CanvasLog.e("RESOLVE: error songKey=$songKey", e)
            CanvasMatch(ok = false, error = e.message)
        }
    }

    /** Stable cache key covering both stream (yt:videoId) and local songs. */
    fun songKeyFor(song: Song): String =
        if (!song.remoteId.isNullOrBlank()) "yt:${song.remoteId}"
        else "local:${normalize(song.title)}|${normalize(song.artist)}|${song.duration}"

    private fun cacheRowExpired(row: CanvasCacheEntity): Boolean {
        val now = System.currentTimeMillis()
        // Mapping TTL governs whether we retry at all; URL expiry separately
        // decides whether the cached URL is still replayable.
        val mappingTtl = if (row.matched) POSITIVE_TTL_MS else NEGATIVE_TTL_MS
        if (now - row.updatedAt > mappingTtl) return true
        if (row.matched && row.canvasUrl != null && row.urlExpiresAt != null && now > row.urlExpiresAt) {
            return true
        }
        return false
    }

    private suspend fun resolveInternal(song: Song, spDc: String): CanvasMatch {
        val query = "${song.title} ${song.artist}".trim()
        val effectiveTitle = trackTitleOnly(song.title, song.artist)
        if (effectiveTitle.isBlank()) {
            CanvasLog.d("RESOLVE: empty title, skip")
            return CanvasMatch(ok = true, matched = false, confidence = 0f, source = "empty_query")
        }
        CanvasLog.d("RESOLVE: query='$query' effectiveTitle='$effectiveTitle' spDc=${if (spDc.isBlank()) "<none>" else "set(${spDc.length}ch)"}")
        val candidates = SpotifyCanvasApi.searchTrack(query, spDc)
        if (candidates.isEmpty()) {
            CanvasLog.d("RESOLVE: no candidates from search")
        }
        val best = bestCandidate(song.title, song.artist, song.duration, candidates)
        if (best == null) {
            CanvasLog.d("RESOLVE: no candidate passed thresholds (title>=0.72 artist>=0.55 conf>=0.60)")
            return CanvasMatch(ok = true, matched = false, confidence = 0f, source = "no_match")
        }
        CanvasLog.d(
            "RESOLVE: best candidate spotifyId=${best.candidate.spotifyId} " +
                "'${best.candidate.title}'/${best.candidate.artist}/${best.candidate.durationMs}ms " +
                "conf=${"%.2f".format(best.confidence)}"
        )

        val canvas = SpotifyCanvasApi.fetchCanvas(best.candidate.spotifyId, spDc)
        return if (canvas.matched) {
            canvas.copy(confidence = best.confidence, source = "search+canvaz")
        } else if (canvas.ok) {
            canvas.copy(confidence = best.confidence, spotifyId = best.candidate.spotifyId, source = "search")
        } else {
            canvas
        }
    }

    internal fun bestCandidate(
        songTitle: String,
        songArtist: String,
        songDurationMs: Long,
        candidates: List<SpotifyCanvasApi.SpotifyTrackCandidate>,
    ): ScoredCandidate? {
        val targetTitle = trackTitleOnly(songTitle, songArtist)
        val targetArtist = if (songArtist.isBlank() || isUnknownArtist(songArtist)) "" else normalize(songArtist)
        val knownDuration = songDurationMs > 0L
        val cleanTitleTokens = targetTitle.split(' ').filter { it.isNotBlank() }

        var best: ScoredCandidate? = null
        for (cand in candidates) {
            val candTitle = normalize(cand.title)
            // Primary artist must be split BEFORE normalization: normalize strips
            // commas, so "Rob $tone, J. Davi$..." would stay one blob and always
            // fail artist matching.
            val candArtistPrimary = normalize(cand.artist.substringBefore(','))
            if (candTitle.isBlank()) continue

            val titleSim = similarity(targetTitle, candTitle)
            val containsBonus = if (cleanTitleTokens.isNotEmpty() &&
                cleanTitleTokens.all { candTitle.contains(it) } &&
                candTitle.length <= targetTitle.length + 12
            ) 0.06f else 0f

            val artistSim = when {
                targetArtist.isBlank() -> 0.7f // unknown artist: don't punish
                candArtistPrimary.isBlank() -> 0.5f
                else -> maxOf(similarity(targetArtist, candArtistPrimary), jaccard(targetArtist, candArtistPrimary))
            }
            if (titleSim < TITLE_SIM_THRESHOLD) continue
            if (artistSim < ARTIST_SIM_THRESHOLD) continue

            val durationSim = if (!knownDuration || cand.durationMs <= 0) {
                0.5f // unknown duration: neutral
            } else {
                val diff = abs(cand.durationMs - songDurationMs)
                when {
                    diff <= DURATION_TOLERANCE_MS -> 1f
                    diff <= 15_000L -> 0.4f
                    else -> 0f // duration wildly different: remix/live — reject
                }
            }
            val confidence = 0.5f * minOf(1f, titleSim + containsBonus) +
                0.25f * artistSim + 0.25f * durationSim
            if (confidence < MIN_CONFIDENCE) continue

            val scored = ScoredCandidate(cand, confidence)
            if (best == null || scored.confidence > best.confidence) best = scored
        }
        return best
    }

    internal data class ScoredCandidate(
        val candidate: SpotifyCanvasApi.SpotifyTrackCandidate,
        val confidence: Float,
    )

    // ---------- text hygiene ----------

    /** MediaStore's "<unknown>" and friends are treated as no artist at all. */
    private fun isUnknownArtist(artist: String): Boolean {
        val t = normalize(artist)
        return t == "unknown" || t == "unknown artist" || t.isEmpty()
    }

    /**
     * Titles like "SZA - Kill Bill" or "SZA: Kill Bill" embed the artist; strip
     * that prefix so the text match compares the pure song title.
     */
    internal fun trackTitleOnly(rawTitle: String, rawArtist: String): String {
        val artistNorm = normalize(rawArtist)
        if (artistNorm.isBlank()) return normalize(rawTitle)
        for (sep in listOf(" - ", " – ", " — ", " • ", ": ")) {
            val idx = rawTitle.indexOf(sep, ignoreCase = true)
            if (idx > 0 && similarity(normalize(rawTitle.take(idx)), artistNorm) >= 0.75f) {
                return normalize(rawTitle.substring(idx + sep.length))
            }
        }
        return normalize(rawTitle)
    }

    /** Lowercase, drop parenthesized noise (feat/official/live/remaster...), punctuation, spaces. */
    internal fun normalize(raw: String): String {
        var t = raw.lowercase()
        // remove content inside every bracket level until stable
        var prev: String
        do {
            prev = t
            t = t.replaceFirst(Regex("\\((.*?)\\)"), "")
            t = t.replaceFirst(Regex("\\[(.*?)]"), "")
        } while (t != prev)
        t = t.replace(Regex("\\b(feat\\.?|ft\\.?|official (video|audio|music|lyrics?)|lyrics?|video|audio|hq|hd|4k|mv|visualizer|remaster(ed)?( \\d{2,4}[^)]*)?|live|sub español|lyric video)\\b"), " ")
        t = t.replace(Regex("[\\u2018\\u2019']"), "") // don't -> dont
        t = t.replace(Regex("[^\\p{L}\\p{Nd} ]"), " ")
        t = t.replace(Regex("\\s+"), " ").trim()
        return t
    }

    internal fun similarity(a: String, b: String): Float {
        if (a.isEmpty() && b.isEmpty()) return 1f
        if (a.isEmpty() || b.isEmpty()) return 0f
        val dist = levenshtein(a, b)
        return 1f - dist.toFloat() / max(a.length, b.length).toFloat()
    }

    private fun jaccard(a: String, b: String): Float {
        val aset = a.split(' ').toSet()
        val bset = b.split(' ').toSet()
        if (aset.isEmpty() || bset.isEmpty()) return 0f
        val inter = aset.intersect(bset).size.toFloat()
        val union = aset.union(bset).size.toFloat()
        return inter / union
    }

    private fun levenshtein(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        var prevRow = IntArray(n + 1) { it }
        val row = IntArray(n + 1)
        for (i in 1..m) {
            row[0] = i
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                row[j] = minOf(prevRow[j] + 1, row[j - 1] + 1, prevRow[j - 1] + cost)
            }
            System.arraycopy(row, 0, prevRow, 0, n + 1)
        }
        return prevRow[n]
    }
}
