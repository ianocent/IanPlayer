package com.ianocent.musicplayer.viewmodel

import com.ianocent.musicplayer.data.Song
import com.ianocent.musicplayer.data.StreamSearchResult
import com.ianocent.musicplayer.data.TidalRepository
import com.ianocent.musicplayer.data.YTMusicRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The stream-search module: owns debounce, the dual-provider search fan-out
 * (YouTube partial stream + Tidal), result merging, and visible-page slicing.
 *
 * Callers see three StateFlows plus [query]/[loadMore]; how results are merged,
 * throttled, and paged is implementation. Every song seen — including partials —
 * is handed to [onSongsSeen] (the SongStore cache); when a search settles,
 * [onResultsSettled] fires so eager resolution can start.
 */
class StreamSearchController(
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val ytMusicRepository: YTMusicRepository,
    private val tidalRepository: TidalRepository,
    private val onSongsSeen: (List<Song>) -> Unit,
    private val onResultsSettled: () -> Unit,
) {

    private val _allResults = MutableStateFlow<List<Song>>(emptyList())
    val allResults: StateFlow<List<Song>> = _allResults

    private val _visibleResults = MutableStateFlow<List<Song>>(emptyList())
    val visibleResults: StateFlow<List<Song>> = _visibleResults

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching

    private val _parsingFailed = MutableStateFlow(false)
    val parsingFailed: StateFlow<Boolean> = _parsingFailed

    private var job: Job? = null

    /** Debounced search across both providers; safe to call on every keystroke. */
    fun query(rawQuery: String) {
        job?.cancel()
        _parsingFailed.value = false

        if (rawQuery.isBlank()) {
            clear()
            return
        }

        _isSearching.value = true
        _allResults.value = emptyList()
        _visibleResults.value = emptyList()

        job = scope.launch {
            delay(DEBOUNCE_MS)
            try {
                // Search both YT Music and Tidal
                val tidalResult = async { tidalRepository.searchSongs(rawQuery) }

                val ytResult = ytMusicRepository.searchSongs(rawQuery, onPartial = { newSongs ->
                    onSongsSeen(newSongs)
                    publishMerged(newSongs)
                })

                val tidalSongs = tidalResult.await()
                if (tidalSongs.isNotEmpty()) {
                    onSongsSeen(tidalSongs)
                    publishMerged(tidalSongs)
                }

                if (ytResult is StreamSearchResult.ParsingFailed && tidalSongs.isEmpty()) {
                    _parsingFailed.value = true
                } else {
                    if (ytResult is StreamSearchResult.Success) onSongsSeen(ytResult.songs)
                    onResultsSettled()
                }
            } catch (_: CancellationException) {
                return@launch
            } finally {
                _isSearching.value = false
            }
        }
    }

    /** Reveals one more page of already-fetched results. */
    fun loadMore() {
        val all = _allResults.value
        val current = _visibleResults.value
        if (current.size >= all.size) return
        _visibleResults.value = all.take(current.size + PAGE_SIZE)
    }

    /**
     * Swaps in an updated copy of a visible song (e.g. after background
     * resolution replaced its placeholder URI with a real URL).
     */
    fun replaceVisibleSong(resolved: Song) {
        _visibleResults.value = _visibleResults.value.map {
            if (it.id == resolved.id) resolved else it
        }
    }

    private fun clear() {
        _allResults.value = emptyList()
        _visibleResults.value = emptyList()
        _isSearching.value = false
    }

    private fun publishMerged(incoming: List<Song>) {
        val merged = mergeById(_allResults.value, incoming)
        _allResults.value = merged
        _visibleResults.value = merged.take(
            maxOf(_visibleResults.value.size + incoming.size, PAGE_SIZE)
        )
    }

    companion object {
        private const val PAGE_SIZE = 50
        private const val DEBOUNCE_MS = 400L

        fun mergeById(current: List<Song>, incoming: List<Song>): List<Song> =
            (current + incoming).distinctBy { it.id }
    }
}
