package com.ianocent.musicplayer.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Navigation state machine for the library screens.
 *
 * Interface is intent-level verbs ([selectTab], [openArtist], [back]); the
 * tab↔pager-page encoding and the back-ordering rules are implementation.
 * Detail state (album/artist/playlist) belongs to this module alone.
 */
class NavState(initialTab: Int = TAB_SONGS) {

    var tab by mutableIntStateOf(initialTab)
        private set

    /** Which pager page hosts the current tab: library pages or stream pages. */
    val tabPage: Int get() = if (tab >= TAB_STREAM) PAGE_STREAM else PAGE_LIBRARY

    var selectedAlbum by mutableStateOf<String?>(null)
        private set

    var selectedArtist by mutableStateOf<String?>(null)
        private set

    var selectedPlaylistId by mutableStateOf<Long?>(null)
        private set

    var showingArtists by mutableStateOf(false)
        private set

    val hasOpenDetail: Boolean
        get() = selectedAlbum != null || selectedArtist != null || selectedPlaylistId != null

    fun selectTab(tab: Int) {
        this.tab = tab
    }

    fun openStreamPage() {
        tab = TAB_STREAM
    }

    fun openLibraryPage() {
        tab = TAB_SONGS
    }

    fun openAlbum(name: String) {
        selectedAlbum = name
        selectedArtist = null
        showingArtists = false
    }

    fun openArtist(name: String) {
        selectedArtist = name
        selectedAlbum = null
        showingArtists = true
    }

    fun openPlaylist(id: Long?) {
        selectedPlaylistId = id
    }

    fun closeAlbum() {
        selectedAlbum = null
    }

    fun closeArtist() {
        selectedArtist = null
    }

    fun closePlaylist() {
        selectedPlaylistId = null
    }

    fun toggleAlbumsArtists() {
        showingArtists = !showingArtists
    }

    /**
     * Consumes one back step across the detail layers, highest priority first.
     * Returns true when this module handled the event; callers fall through to
     * their own layers (genre, search) otherwise.
     */
    fun back(): Boolean = when {
        selectedAlbum != null -> { closeAlbum(); true }
        selectedPlaylistId != null -> { closePlaylist(); true }
        selectedArtist != null -> { closeArtist(); true }
        else -> false
    }

    companion object {
        const val TAB_SONGS = 0
        const val TAB_ALBUMS = 1
        const val TAB_STREAM = 2
        const val TAB_PLAYLISTS = 3

        const val PAGE_LIBRARY = 0
        const val PAGE_STREAM = 1
    }
}
