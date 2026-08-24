package com.ianocent.musicplayer.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Provider tags carried by [Song]. The set of stream providers is open; add a
 * constant plus a [StreamResolver] adapter here when a new provider lands.
 */
object StreamSources {
    const val YOUTUBE = "youtube"
    const val TIDAL = "tidal"

    /**
     * URI scheme emitted for YouTube songs whose stream URL has not been
     * resolved yet. Private convention of the resolution path: no code outside
     * this file should interpret it directly — use [needsResolution].
     */
    const val PLACEHOLDER_PREFIX = "ytmusic://placeholder/"

    /**
     * True when the song must go through stream resolution before playback or
     * download. Legacy persisted songs carry no [Song.source]; their placeholder
     * URI identifies them.
     */
    fun needsResolution(song: Song): Boolean =
        song.isStream && song.uri.toString().startsWith(PLACEHOLDER_PREFIX)
}

/**
 * Port for turning an unresolved Song into a playable/downloadable URL.
 * Two production adapters exist (YouTube, Tidal), which makes the seam real.
 */
interface StreamResolver {
    /** The [StreamSources] constant this resolver handles, or null for any/legacy. */
    val sourceId: String?

    suspend fun resolveStreamUrl(song: Song): String?
}

class YouTubeStreamResolver(
    private val repository: YTMusicRepository
) : StreamResolver {
    override val sourceId: String? = StreamSources.YOUTUBE

    override suspend fun resolveStreamUrl(song: Song): String? =
        repository.resolveStreamUrl(song)
}

/**
 * Tidal tracks need auth for direct stream URLs; without it the adapter falls
 * back to searching and resolving the same track on YouTube.
 */
class TidalStreamResolver(
    private val tidalRepository: TidalRepository,
    private val youtubeFallback: StreamResolver,
    private val youtubeSearch: suspend (String) -> List<Song>
) : StreamResolver {
    override val sourceId: String? = StreamSources.TIDAL

    override suspend fun resolveStreamUrl(song: Song): String? {
        val query = "${song.artist} - ${song.title}"
        val matches = youtubeSearch(query)
        val bestMatch = matches.firstOrNull() ?: return null
        return youtubeFallback.resolveStreamUrl(bestMatch)
    }
}

/** Routes each Song to the resolver matching its provider tag. */
class StreamResolvers(adapters: List<StreamResolver>) {
    private val bySource: Map<String, StreamResolver> =
        adapters.filter { it.sourceId != null }.associateBy { it.sourceId!! }

    private val default: StreamResolver? = adapters.firstOrNull { it.sourceId == null }
        ?: bySource[StreamSources.YOUTUBE]

    fun resolverFor(song: Song): StreamResolver? =
        song.source?.let { bySource[it] } ?: default

    suspend fun resolve(song: Song): String? = withContext(Dispatchers.IO) {
        resolverFor(song)?.resolveStreamUrl(song)
    }
}
