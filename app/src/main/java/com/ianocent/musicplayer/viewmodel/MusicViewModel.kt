package com.ianocent.musicplayer.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.net.Uri
import timber.log.Timber
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.ianocent.musicplayer.data.AlbumArtLoader
import com.ianocent.musicplayer.data.AudioFormat
import com.ianocent.musicplayer.data.LyricLine
import com.ianocent.musicplayer.data.LyricRepository
import com.ianocent.musicplayer.data.MonthlyRecap
import com.ianocent.musicplayer.data.MusicRepository
import com.ianocent.musicplayer.data.Song
import com.ianocent.musicplayer.player.IanVoiceAssistantService
import com.ianocent.musicplayer.player.PlaybackService
import com.ianocent.musicplayer.player.PlayerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Color
import com.ianocent.musicplayer.data.Playlist
import com.ianocent.musicplayer.data.UpdateInfo
import com.ianocent.musicplayer.data.YTMusicRepository
import com.ianocent.musicplayer.data.StreamSearchResult
import com.ianocent.musicplayer.UpdateManager
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import android.content.IntentSender
import android.app.RecoverableSecurityException

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val ytMusicRepository = YTMusicRepository(application.applicationContext)
    
    private val _deleteIntentSender = MutableSharedFlow<IntentSender>()
    val deleteIntentSender: SharedFlow<IntentSender> = _deleteIntentSender
    
    private val _editIntentSender = MutableSharedFlow<IntentSender>()
    val editIntentSender: SharedFlow<IntentSender> = _editIntentSender

    data class PendingUpdate(val id: Long, val title: String, val artist: String, val uri: Uri?)
    var pendingUpdateInfo: PendingUpdate? = null

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable !is CancellationException) {
            throwable.printStackTrace()
        }
    }

    // ---- Genre browsing ----
    data class Genre(val name: String, val query: String)

    val genres = listOf(
        Genre("Pop", "pop music"),
        Genre("Rock", "rock music"),
        Genre("Hip Hop", "hip hop music"),
        Genre("R&B", "rnb music"),
        Genre("Electronic", "electronic music"),
        Genre("Jazz", "jazz music"),
        Genre("Classical", "classical music"),
        Genre("Country", "country music"),
        Genre("Indie", "indie music"),
        Genre("Metal", "metal music")
    )

    private val _selectedGenre = MutableStateFlow<String?>(null)
    val selectedGenre: StateFlow<String?> = _selectedGenre

    private val _genreSongs = MutableStateFlow<Map<String, List<Song>>>(emptyMap())
    val genreSongs: StateFlow<Map<String, List<Song>>> = _genreSongs

    private val _isGenreLoading = MutableStateFlow(false)
    val isGenreLoading: StateFlow<Boolean> = _isGenreLoading

    private val _genreFirstSong = MutableStateFlow<Map<String, Song?>>(emptyMap())
    val genreFirstSong: StateFlow<Map<String, Song?>> = _genreFirstSong
    private var genreArtLoadJob: Job? = null

    fun loadGenreArtworks() {
        if (genreArtLoadJob?.isActive == true) return
        genreArtLoadJob = viewModelScope.launch {
            for (genre in genres) {
                if (!isActive) break
                try {
                    val result = ytMusicRepository.searchSongs(genre.query) {}
                    if (result is StreamSearchResult.Success && result.songs.isNotEmpty()) {
                        val song = result.songs.first()
                        _genreFirstSong.value = _genreFirstSong.value + (genre.name to song)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private val genreFetchJobs = mutableMapOf<String, Job>()

    fun selectGenre(genreName: String) {
        _selectedGenre.value = genreName
        if (_genreSongs.value.containsKey(genreName)) return
        val genre = genres.find { it.name == genreName } ?: return
        _isGenreLoading.value = true
        genreFetchJobs[genreName]?.cancel()
        genreFetchJobs[genreName] = viewModelScope.launch {
            try {
                val result = ytMusicRepository.searchSongs(genre.query) { newSongs ->
                    val current = _genreSongs.value.toMutableMap()
                    val merged = (current[genreName] ?: emptyList()) + newSongs
                    current[genreName] = merged.distinctBy { it.id }
                    _genreSongs.value = current
                }
                if (result is StreamSearchResult.Success) {
                    val current = _genreSongs.value.toMutableMap()
                    current[genreName] = result.songs
                    _genreSongs.value = current
                }
            } catch (e: Exception) {
                Timber.e(e, "MusicViewModel fetch genre ${genreName} failed")
            } finally {
                _isGenreLoading.value = false
            }
        }
    }

    fun clearGenre() {
        _selectedGenre.value = null
    }

    // ---- Trending / Home ----
    private val _trendingSongs = MutableStateFlow<List<Song>>(emptyList())
    val trendingSongs: StateFlow<List<Song>> = _trendingSongs
    private val _isTrendingLoading = MutableStateFlow(false)
    val isTrendingLoading: StateFlow<Boolean> = _isTrendingLoading
    private var trendingLoadAttempted = false
    private val preResolvedIds = mutableSetOf<Long>()

    fun fetchTrending(force: Boolean = false) {
        if (!force && trendingLoadAttempted) return
        trendingLoadAttempted = true
        _isTrendingLoading.value = true
        viewModelScope.launch {
            try {
                val result = ytMusicRepository.fetchHomeSongs { newSongs ->
                    val merged = (_trendingSongs.value + newSongs).distinctBy { it.id }
                    _trendingSongs.value = merged
                }
                if (result !is StreamSearchResult.Success) {
                    Timber.w("MusicViewModel Trending fetch: $result")
                }
            } catch (e: Exception) {
                Timber.e(e, "MusicViewModel fetchTrending failed")
            } finally {
                _isTrendingLoading.value = false
            }
            // Start pre-resolve after trending loaded
            preResolveTrending()
        }
    }

    fun refreshTrending() {
        trendingLoadAttempted = false
        fetchTrending(force = true)
    }

    private fun preResolveTrending() {
        val trending = _trendingSongs.value
        if (trending.isEmpty()) return
        viewModelScope.launch {
            for (song in trending) {
                if (!song.isStream || !song.uri.toString().startsWith("ytmusic://placeholder/")) continue
                if (!preResolvedIds.add(song.id)) continue
                launch {
                    try {
                        val url = withContext(Dispatchers.IO) {
                            ytMusicRepository.resolveStreamUrl(song)
                        }
                        if (url != null) {
                            val resolved = song.copy(uri = Uri.parse(url))
                            val list = _trendingSongs.value.toMutableList()
                            val idx = list.indexOfFirst { it.id == resolved.id }
                            if (idx >= 0) list[idx] = resolved
                            _trendingSongs.value = list
                            val streamList = _streamSongs.value.toMutableList()
                            val sidx = streamList.indexOfFirst { it.id == resolved.id }
                            if (sidx >= 0) streamList[sidx] = resolved
                            _streamSongs.value = streamList
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "MusicViewModel preResolveTrending failed for ${song.title}")
                    }
                }
            }
        }
    }

    private fun preResolveSearchResults() {
        val searchResults = _streamSongs.value
        if (searchResults.isEmpty()) return
        viewModelScope.launch {
            for (song in searchResults) {
                if (!song.isStream || !song.uri.toString().startsWith("ytmusic://placeholder/")) continue
                if (!preResolvedIds.add(song.id)) continue
                launch {
                    try {
                        val url = withContext(Dispatchers.IO) {
                            ytMusicRepository.resolveStreamUrl(song)
                        }
                        if (url != null) {
                            val resolved = song.copy(uri = Uri.parse(url))
                            val list = _streamSongs.value.toMutableList()
                            val idx = list.indexOfFirst { it.id == resolved.id }
                            if (idx >= 0) list[idx] = resolved
                            _streamSongs.value = list
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "MusicViewModel preResolveSearch failed for ${song.title}")
                    }
                }
            }
        }
    }

    // ---- Stream Search ----
    private val _allStreamSongs = MutableStateFlow<List<Song>>(emptyList())
    private val _streamSongs = MutableStateFlow<List<Song>>(emptyList())
    val streamSongs: StateFlow<List<Song>> = _streamSongs

    private val _isSearchingRemote = MutableStateFlow(false)
    val isSearchingRemote: StateFlow<Boolean> = _isSearchingRemote

    private val _streamParsingFailed = MutableStateFlow(false)
    val streamParsingFailed: StateFlow<Boolean> = _streamParsingFailed

    private var searchJob: Job? = null

    private val streamPageSize = 50

    private val _sortMode = MutableStateFlow(0)
    val sortMode: StateFlow<Int> = _sortMode

    private val _playCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val playCounts: StateFlow<Map<Long, Int>> = _playCounts

    private val _favoriteIds = MutableStateFlow<Set<Long>>(emptySet())
    val favoriteIds: StateFlow<Set<Long>> = _favoriteIds

    fun toggleFavorite(songId: Long) {
        val updated = _favoriteIds.value.toMutableSet()
        if (updated.contains(songId)) updated.remove(songId) else updated.add(songId)
        _favoriteIds.value = updated
        prefs.edit().putString("favorite_ids", updated.joinToString(",")).apply()
    }

    fun isFavorite(songId: Long): Boolean = _favoriteIds.value.contains(songId)

    private fun loadFavoriteIds() {
        val raw = prefs.getString("favorite_ids", null) ?: return
        _favoriteIds.value = raw.split(",").mapNotNull { it.toLongOrNull() }.toSet()
    }

    fun setSortMode(mode: Int) {
        _sortMode.value = mode
        prefs.edit().putInt("sort_mode", mode).apply()
    }

    private fun loadPlayCounts(): Map<Long, Int> {
        val json = prefs.getString("play_counts", null) ?: return emptyMap()
        return try {
            val obj = JSONObject(json)
            val map = mutableMapOf<Long, Int>()
            obj.keys().forEach { key -> map[key.toLong()] = obj.getInt(key) }
            map
        } catch (_: Exception) { emptyMap() }
    }

    private fun savePlayCounts(counts: Map<Long, Int>) {
        val obj = JSONObject()
        counts.forEach { (id, count) -> obj.put(id.toString(), count) }
        prefs.edit().putString("play_counts", obj.toString()).apply()
    }

    fun incrementPlayCount(songId: Long) {
        val counts = _playCounts.value.toMutableMap()
        counts[songId] = (counts[songId] ?: 0) + 1
        _playCounts.value = counts
        savePlayCounts(counts)
        recordPlayHistory(songId)
    }

    private fun recordPlayHistory(songId: Long) {
        val now = System.currentTimeMillis()
        val history = loadPlayHistory().toMutableList()
        history.add(Pair(songId, now))
        // Keep only last 90 days of history
        val cutoff = now - 90L * 24 * 60 * 60 * 1000
        val trimmed = history.filter { it.second > cutoff }
        savePlayHistory(trimmed)
    }

    private fun loadPlayHistory(): List<Pair<Long, Long>> {
        val json = prefs.getString("play_history", null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<Pair<Long, Long>>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(Pair(obj.getLong("id"), obj.getLong("ts")))
            }
            list
        } catch (_: Exception) { emptyList() }
    }

    private fun savePlayHistory(history: List<Pair<Long, Long>>) {
        val arr = JSONArray()
        history.forEach { (id, ts) ->
            val obj = JSONObject()
            obj.put("id", id)
            obj.put("ts", ts)
            arr.put(obj)
        }
        prefs.edit().putString("play_history", arr.toString()).apply()
    }

    fun checkMonthlyRecap() {
        val now = System.currentTimeMillis()
        val lastCheck = prefs.getLong("last_recap_check_ts", 0L)
        val ONE_MONTH = 30L * 24 * 60 * 60 * 1000
        if (now - lastCheck < ONE_MONTH) return
        prefs.edit().putLong("last_recap_check_ts", now).apply()
        computeMonthlyRecap()
    }

    private fun computeMonthlyRecap() {
        if (_songs.value.isEmpty()) return
        viewModelScope.launch {
            val recap = withContext(Dispatchers.IO) {
                try {
                    val now = System.currentTimeMillis()
                    val monthStart = now - 30L * 24 * 60 * 60 * 1000
                    val history = loadPlayHistory().filter { it.second >= monthStart }

                    if (history.isEmpty()) return@withContext null

                    val songPlayCounts = history.groupBy { it.first }.mapValues { it.value.size }
                    val totalPlays = history.size

                    val allSongs = _songs.value
                    val playedSongs = allSongs.filter { s -> songPlayCounts.containsKey(s.id) }
                    val monthSongs = playedSongs.map { song ->
                        song to (songPlayCounts[song.id] ?: 0)
                    }.sortedByDescending { it.second }

                    val topSongs = monthSongs.take(5).map { it.first }

                    val artistCounts = mutableMapOf<String, Int>()
                    monthSongs.forEach { (song, count) ->
                        val artistKey = song.artist.ifBlank { "Unknown Artist" }
                        artistCounts[artistKey] = (artistCounts[artistKey] ?: 0) + count
                    }
                    val topArtists = artistCounts.entries
                        .sortedByDescending { it.value }
                        .take(5)
                        .map { it.key to it.value }

                    val totalMinutes = (totalPlays * 3.5).toLong()

                    val tasteComment = generateTasteComment(topArtists, monthSongs)

                    val monthName = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(now))

                    MonthlyRecap(
                        monthLabel = monthName,
                        totalPlays = totalPlays,
                        totalMinutes = totalMinutes,
                        topSongs = topSongs,
                        topArtists = topArtists,
                        topGenres = emptyList(),
                        tasteComment = tasteComment
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }

            if (recap != null && recap.totalPlays >= 5) {
                _monthlyRecap.value = recap
                _showRecapBanner.value = true
            }
        }
    }

    fun dismissRecapBanner() {
        _showRecapBanner.value = false
    }
    fun debugTriggerRecap() {
        val now = System.currentTimeMillis()
        val history = loadPlayHistory().filter { it.second >= now - 30L * 24 * 60 * 60 * 1000 }

        if (history.isEmpty() || history.size < 5) {
            // Generate mock recap so the card is testable
            val mockSongs = _songs.value.take(5).ifEmpty {
                listOf(Song(0, "Sample Song", "Sample Artist", 240000, Uri.EMPTY))
            }
            _monthlyRecap.value = MonthlyRecap(
                monthLabel = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(now)),
                totalPlays = (history.size).coerceAtLeast(5),
                totalMinutes = 30L,
                topSongs = mockSongs,
                topArtists = listOf("Artist 1" to 5, "Artist 2" to 3, "Artist 3" to 2),
                topGenres = emptyList(),
                tasteComment = "Your music taste is shaping up! Keep exploring."
            )
            _showRecapBanner.value = true
        } else {
            computeMonthlyRecap()
        }
    }

    private fun generateTasteComment(
        topArtists: List<Pair<String, Int>>,
        monthSongs: List<Pair<Song, Int>>
    ): String {
        if (topArtists.isEmpty()) return "Start listening to discover your music taste!"

        val artistNames = topArtists.take(3).map { it.first }
        val totalSongs = monthSongs.distinctBy { it.first.id }.size
        val topArtistName = artistNames.firstOrNull() ?: "music"

        val comments = listOf(
            "You've been vibing with $topArtistName a lot this month. Your taste is getting refined!",
            "$topArtistName is clearly your go-to artist right now. Solid choice!",
            "Your playlist is showing great variety with ${artistNames.joinToString(", ")}. Keep exploring!",
            "You discovered $totalSongs different songs this month. Your music journey is on fire!",
            "The vibes are strong this month! $topArtistName + ${artistNames.drop(1).firstOrNull() ?: "others"} = perfect combo.",
            "Your music taste is uniquely you. Love the energy from $topArtistName!",
            "What a month! You've been on a musical adventure with $totalSongs tracks. Respect the grind.",
            "$topArtistName has been your soundtrack this month. Iconic taste!"
        )

        return comments[topArtists.hashCode().rem(comments.size).let { if (it < 0) it + comments.size else it }]
    }

    fun applySort(songs: List<Song>): List<Song> {
        return when (_sortMode.value) {
            0 -> songs.sortedBy { it.title.lowercase() }
            1 -> songs.sortedByDescending { it.dateAdded }
            2 -> {
                val counts = _playCounts.value
                songs.sortedByDescending { counts[it.id] ?: 0 }
            }
            else -> songs
        }
    }

    // Fungsi khusus buat search di Tab Stream dengan sistem Debounce (Anti-lag)
    fun searchRemoteSongs(query: String) {
        searchJob?.cancel()
        _streamParsingFailed.value = false

        if (query.isBlank()) {
            _allStreamSongs.value = emptyList()
            _streamSongs.value = emptyList()
            _isSearchingRemote.value = false
            return
        }

        _isSearchingRemote.value = true
        _allStreamSongs.value = emptyList()
        _streamSongs.value = emptyList()

        searchJob = viewModelScope.launch {
            delay(400)
            try {
                val result = ytMusicRepository.searchSongs(query) { newSongs ->
                    val merged = (_allStreamSongs.value + newSongs).distinctBy { it.id }
                    _allStreamSongs.value = merged
                    _streamSongs.value = merged.take(
                        maxOf(_streamSongs.value.size + newSongs.size, streamPageSize)
                    )
                }
                if (result is StreamSearchResult.ParsingFailed) {
                    _streamParsingFailed.value = true
                } else {
                    // Pre-resolve search results in background
                    preResolveSearchResults()
                }
            } catch (_: CancellationException) {
                return@launch
            } finally {
                _isSearchingRemote.value = false
            }
        }
    }

    fun loadMoreStreamSongs() {
        val all = _allStreamSongs.value
        val current = _streamSongs.value
        if (current.size >= all.size) return
        _streamSongs.value = all.take(current.size + streamPageSize)
    }

    private val prefs = application.getSharedPreferences("ian_player_prefs", 0)

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists

    fun createPlaylist(name: String, songIds: List<Long>) {
        val newPlaylist = Playlist(
            id = System.currentTimeMillis(),
            name = name,
            songIds = songIds.toMutableList()
        )
        _playlists.value = _playlists.value + newPlaylist
        savePlaylistsToPrefs()
    }

    fun updatePlaylist(playlistId: Long, newName: String? = null, newImageUri: String? = null) {
        _playlists.value = _playlists.value.map { playlist ->
            if (playlist.id == playlistId) {
                playlist.copy(
                    name = newName ?: playlist.name,
                    imageUri = newImageUri ?: playlist.imageUri
                )
            } else {
                playlist
            }
        }
        savePlaylistsToPrefs()
    }

    fun getSongsInPlaylist(playlist: Playlist): List<Song> {
        return _songs.value.filter { it.id in playlist.songIds }
    }

    fun deletePlaylist(playlist: Playlist) {
        _playlists.value = _playlists.value.filter { it.id != playlist.id }
        savePlaylistsToPrefs()
    }

    private fun savePlaylistsToPrefs() {
        val jsonArray = JSONArray()
        _playlists.value.forEach { playlist ->
            val obj = JSONObject()
            obj.put("id", playlist.id)
            obj.put("name", playlist.name)
            obj.put("songIds", JSONArray(playlist.songIds))
            playlist.imageUri?.let { obj.put("imageUri", it) }
            jsonArray.put(obj)
        }
        prefs.edit().putString("playlists", jsonArray.toString()).apply()
    }

    private fun loadPlaylistsFromPrefs() {
        val jsonString = prefs.getString("playlists", null) ?: return
        try {
            val jsonArray = JSONArray(jsonString)
            val loaded = mutableListOf<Playlist>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val idsArray = obj.getJSONArray("songIds")
                val ids = mutableListOf<Long>()
                for (j in 0 until idsArray.length()) ids.add(idsArray.getLong(j))
                val imageUri = if (obj.has("imageUri")) obj.getString("imageUri") else null
                loaded.add(Playlist(id = obj.getLong("id"), name = obj.getString("name"), songIds = ids, imageUri = imageUri))
            }
            _playlists.value = loaded
        } catch (e: Exception) {
            // data corrupt, abaikan
        }
    }
    private val _isDarkMode = MutableStateFlow(prefs.getBoolean("is_dark_mode", true))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode

    private val _isVoiceAssistantEnabled = MutableStateFlow(prefs.getBoolean("voice_assistant_enabled", false))
    val isVoiceAssistantEnabled: StateFlow<Boolean> = _isVoiceAssistantEnabled

    fun toggleVoiceAssistant() {
        val newValue = !_isVoiceAssistantEnabled.value
        _isVoiceAssistantEnabled.value = newValue
        prefs.edit().putBoolean("voice_assistant_enabled", newValue).apply()
        
        if (newValue) {
            IanVoiceAssistantService.start(appContext)
        } else {
            IanVoiceAssistantService.stop(appContext)
        }
    }

    fun toggleDarkMode() {
        _isDarkMode.value = !_isDarkMode.value
        prefs.edit().putBoolean("is_dark_mode", _isDarkMode.value).apply()
    }

    private val _isPillAtBottom = MutableStateFlow(prefs.getBoolean("is_pill_at_bottom", false))
    val isPillAtBottom: StateFlow<Boolean> = _isPillAtBottom

    fun setPillAtBottom(atBottom: Boolean) {
        _isPillAtBottom.value = atBottom
        prefs.edit().putBoolean("is_pill_at_bottom", atBottom).apply()
    }

    private val _miniLayoutIndex = MutableStateFlow(prefs.getInt("mini_layout_index", 0))
    val miniLayoutIndex: StateFlow<Int> = _miniLayoutIndex

    fun setMiniLayoutIndex(index: Int) {
        _miniLayoutIndex.value = index
        prefs.edit().putInt("mini_layout_index", index).apply()
    }

    private val _showListeningPill = MutableStateFlow(false)
    val showListeningPill: StateFlow<Boolean> = _showListeningPill

    private var hidePillJob: Job? = null

    private fun startHidePillTimer() {
        hidePillJob?.cancel()
        hidePillJob = viewModelScope.launch {
            delay(60_000) // 1 minute
            if (!_isPlaying.value) {
                _showListeningPill.value = false
            }
        }
    }

    fun savePlaylistOrder(playlist: Playlist, newSongIds: List<Long>) {
        val currentList = _playlists.value.toMutableList()
        val idx = currentList.indexOfFirst { it.id == playlist.id }
        if (idx != -1) {
            currentList[idx] = currentList[idx].copy(songIds = newSongIds.toMutableList())
            _playlists.value = currentList
            savePlaylistsToPrefs()
        }
    }
    fun reorderUpNext(fromIndexInUpNext: Int, toIndexInUpNext: Int) {
        val cur = _currentSong.value ?: return
        val list = _queue.value
        // Pake id, BUKAN equality objek penuh — lagu stream yang uri-nya udah keburu
        // di-resolve (placeholder -> URL asli lewat prefetch/resolveAndPlayStream)
        // bakal punya field 'uri' beda dari _currentSong.value, jadi indexOf() structural
        // equality gagal match dan reorder silently no-op.
        val curIdx = list.indexOfFirst { it.id == cur.id }
        if (curIdx == -1) return

        val actualFrom = curIdx + 1 + fromIndexInUpNext
        val actualTo = curIdx + 1 + toIndexInUpNext
        if (actualFrom !in list.indices || actualTo !in list.indices) return

        val newQueue = list.toMutableList()
        val movedItem = newQueue.removeAt(actualFrom)
        newQueue.add(actualTo, movedItem)
        _queue.value = newQueue
        _currentIndex.value = newQueue.indexOfFirst { it.id == cur.id }.coerceAtLeast(0)

        playerManager.moveQueuedItem(actualFrom, actualTo)
        savePlayerState()
    }

    private fun savePlayerState() {
        val current = _currentSong.value ?: return
        val arr = JSONArray()
        _queue.value.forEach { s ->
            val obj = JSONObject()
            obj.put("id", s.id)
            obj.put("title", s.title)
            obj.put("artist", s.artist)
            obj.put("album", s.album)
            obj.put("uri", s.uri.toString())
            obj.put("isStream", s.isStream)
            s.remoteArtUrl?.let { obj.put("remoteArtUrl", it) }
            s.remoteId?.let { obj.put("remoteId", it) }
            obj.put("duration", s.duration)
            arr.put(obj)
        }
        prefs.edit()
            .putString("last_queue", arr.toString())
            .putLong("last_song_id", current.id)
            .putInt("last_index", _currentIndex.value)
            .apply()
    }

    private fun restorePlayerState() {
        if (_queue.value.isNotEmpty()) return
        val queueJson = prefs.getString("last_queue", null) ?: return
        val savedSongId = prefs.getLong("last_song_id", -1L)
        val savedIndex = prefs.getInt("last_index", -1)
        if (savedSongId < 0 || savedIndex < 0) return
        try {
            val arr = JSONArray(queueJson)
            val restoredQueue = mutableListOf<Song>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                restoredQueue.add(
                    Song(
                        id = obj.getLong("id"),
                        title = obj.getString("title"),
                        artist = obj.getString("artist"),
                        duration = obj.optLong("duration", 0L),
                        uri = android.net.Uri.parse(obj.getString("uri")),
                        album = obj.optString("album", "Unknown Album"),
                        isStream = obj.optBoolean("isStream", false),
                        remoteArtUrl = if (obj.has("remoteArtUrl")) obj.getString("remoteArtUrl") else null,
                        remoteId = if (obj.has("remoteId")) obj.getString("remoteId") else null
                    )
                )
            }
            if (restoredQueue.isNotEmpty()) {
                _queue.value = restoredQueue
                _currentIndex.value = savedIndex.coerceIn(0, restoredQueue.size - 1)
                pendingMediaId = savedSongId
            }
        } catch (_: Exception) {}
    }

    private val repository = MusicRepository(application)
    val playerManager = PlayerManager(application)

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs

    private val _isLoadingSongs = MutableStateFlow(false)
    val isLoadingSongs: StateFlow<Boolean> = _isLoadingSongs

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue

    private val _currentIndex = MutableStateFlow(-1)

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration

    private val _isShuffleOn = MutableStateFlow(prefs.getBoolean("is_shuffle_on", false))

    val isShuffleOn: StateFlow<Boolean> = _isShuffleOn

    private val _repeatMode = MutableStateFlow(prefs.getInt("repeat_mode", Player.REPEAT_MODE_OFF))

    val repeatMode: StateFlow<Int> = _repeatMode

    private var originalOrder: List<Song> = emptyList()
    private val _albumArt = MutableStateFlow<android.graphics.Bitmap?>(null)
    val albumArt: StateFlow<android.graphics.Bitmap?> = _albumArt

    private val lyricRepository = LyricRepository()

    private val _lyric = MutableStateFlow<String?>(null)
    val lyric: StateFlow<String?> = _lyric

    private val _isLyricLoading = MutableStateFlow(false)
    val isLyricLoading: StateFlow<Boolean> = _isLyricLoading

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering

    private val _audioSessionId = MutableStateFlow(0)
    val audioSessionId: StateFlow<Int> = _audioSessionId

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo

    private val _isUpdateAvailable = MutableStateFlow(false)
    val isUpdateAvailable: StateFlow<Boolean> = _isUpdateAvailable

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading

    private val _monthlyRecap = MutableStateFlow<MonthlyRecap?>(null)
    val monthlyRecap: StateFlow<MonthlyRecap?> = _monthlyRecap

    private val _showRecapBanner = MutableStateFlow(false)
    val showRecapBanner: StateFlow<Boolean> = _showRecapBanner

    private val _showRecapCard = MutableStateFlow(false)
    val showRecapCard: StateFlow<Boolean> = _showRecapCard

    fun openRecapCard() {
        _showRecapCard.value = true
    }

    fun closeRecapCard() {
        _showRecapCard.value = false
    }

    private val artCache = mutableMapOf<Long, android.graphics.Bitmap?>()
    private val highResArtCache = mutableMapOf<Long, android.graphics.Bitmap?>()

    fun getCachedArt(song: Song, onLoaded: (android.graphics.Bitmap?) -> Unit) {
        if (artCache.containsKey(song.id)) {
            onLoaded(artCache[song.id])
            return
        }
        viewModelScope.launch {
            val art = withContext(Dispatchers.IO) {
                if (song.isStream && !song.remoteArtUrl.isNullOrEmpty()) {
                    try {
                        val url = java.net.URL(song.remoteArtUrl)
                        val conn = url.openConnection() as java.net.HttpURLConnection
                        conn.doInput = true
                        conn.connect()
                        android.graphics.BitmapFactory.decodeStream(conn.inputStream)
                    } catch (e: Exception) { null }
                } else {
                    AlbumArtLoader.getEmbeddedArt(appContext, song.uri)
                }
            }
            artCache[song.id] = art
            onLoaded(art)
        }
    }

    fun getHighResArt(song: Song, onLoaded: (android.graphics.Bitmap?) -> Unit) {
        val cacheKey = -song.id
        if (highResArtCache.containsKey(cacheKey)) {
            onLoaded(highResArtCache[cacheKey])
            return
        }
        viewModelScope.launch {
            val art = withContext(Dispatchers.IO) {
                if (song.isStream && !song.remoteArtUrl.isNullOrEmpty()) {
                    try {
                        val highResUrl = when {
                            song.remoteArtUrl.contains("=w") && song.remoteArtUrl.contains("-h") -> {
                                song.remoteArtUrl.replace(Regex("=w\\d+-h\\d+.*"), "=w1000-h1000-l90-rj")
                            }
                            song.remoteArtUrl.contains("=s") -> {
                                song.remoteArtUrl.replace(Regex("=s\\d+.*"), "=s1000-c-rj")
                            }
                            song.remoteArtUrl.contains("googleusercontent.com") && !song.remoteArtUrl.contains("=") -> {
                                "${song.remoteArtUrl}=w1000-h1000-l90-rj"
                            }
                            song.remoteArtUrl.contains("ytimg.com") -> {
                                song.remoteArtUrl.replace("default.jpg", "maxresdefault.jpg")
                                    .replace("mqdefault.jpg", "maxresdefault.jpg")
                                    .replace("hqdefault.jpg", "maxresdefault.jpg")
                            }
                            else -> song.remoteArtUrl
                        }
                        val url = java.net.URL(highResUrl)
                        val conn = url.openConnection() as java.net.HttpURLConnection
                        conn.doInput = true
                        conn.connect()
                        android.graphics.BitmapFactory.decodeStream(conn.inputStream)
                    } catch (e: Exception) { null }
                } else {
                    AlbumArtLoader.getEmbeddedArt(appContext, song.uri, targetSize = 800)
                }
            }
            highResArtCache[cacheKey] = art
            onLoaded(art)
        }
    }

    private val appContext = application.applicationContext
    private var pendingMediaId: Long? = null
    private var downloadReceiver: BroadcastReceiver? = null

    private val prefetchingIds = mutableSetOf<Long>()

    init {
        loadPlaylistsFromPrefs()
        _playCounts.value = loadPlayCounts()
        _sortMode.value = prefs.getInt("sort_mode", 0)
        loadFavoriteIds()
        restorePlayerState()
        checkForUpdate()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ytMusicRepository.cleanupExpiredCache()
            } catch (e: Exception) {
                Timber.w(e, "MusicViewModel cleanupExpiredCache failed")
            }
        }
        fetchTrending()
        playerManager.initialize {
            val player = playerManager.player

            _audioSessionId.value = playerManager.getAudioSessionId()

            player?.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                    if (isPlaying) {
                        _showListeningPill.value = true
                        hidePillJob?.cancel()
                    } else {
                        startHidePillTimer()
                    }
                }
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    _duration.value = playerManager.getDuration()
                    syncStateFromPlayer()
                    
                    val currentItem = player?.currentMediaItem
                    if (currentItem != null && currentItem.localConfiguration?.uri?.toString()?.startsWith("ytmusic://placeholder/") == true) {
                        _currentSong.value?.let { resolveAndPlayStream(it) }
                    }
                }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    _isBuffering.value = playbackState == Player.STATE_BUFFERING
                    if (playbackState == Player.STATE_ENDED) {
                        if (player?.repeatMode != Player.REPEAT_MODE_ONE) {
                            playNext()
                        }
                    }
                    if (playbackState == Player.STATE_IDLE && player?.playerError != null) {
                        _isPlaying.value = false
                        
                        val currentUri = player?.currentMediaItem?.localConfiguration?.uri?.toString()
                        if (currentUri?.startsWith("ytmusic://placeholder/") == true) {
                            return 
                        }
                        
                        try {
                            player?.prepare()
                            player?.play()
                        } catch (e: Exception) {
                            Timber.w(e, "MusicViewModel player retry failed, skip to next")
                            playNext()
                        }
                    }
                }
                override fun onPlayerError(error: PlaybackException) {
                    error.printStackTrace()
                    _isPlaying.value = false
                    
                    // Mencegah auto-skip jika error disebabkan oleh URI placeholder yang sedang di-resolve
                    val currentUri = player?.currentMediaItem?.localConfiguration?.uri?.toString()
                    if (currentUri?.startsWith("ytmusic://placeholder/") == true) {
                        return 
                    }

                    playNext()
                }
            })

            if (player?.currentMediaItem != null) {
                val mediaId = player.currentMediaItem?.mediaId?.toLongOrNull()
                if (mediaId != null) {
                    pendingMediaId = mediaId
                    tryRestoreCurrentSong()
                    if (player.isPlaying) {
                        _showListeningPill.value = true
                    } else {
                        startHidePillTimer()
                    }
                }
            }
        }

        // Setiap kali _songs berubah (misal abis loadSongs), coba restore lagi
        viewModelScope.launch {
            _songs.collect {
                tryRestoreCurrentSong()
            }
        }

        viewModelScope.launch(exceptionHandler) {
            while (isActive) {
                try {
                    _currentPosition.value = playerManager.getCurrentPosition()
                    _duration.value = playerManager.getDuration()
                    
                    // Capturing audio session ID more aggressively
                    if (_audioSessionId.value == 0 || _audioSessionId.value != playerManager.getAudioSessionId()) {
                        val sid = playerManager.getAudioSessionId()
                        if (sid != 0) _audioSessionId.value = sid
                    }
                    
                    // Fallback to static ID if needed
                    if (_audioSessionId.value == 0 && PlaybackService.audioSessionId != 0) {
                        _audioSessionId.value = PlaybackService.audioSessionId
                    }

                    val remaining = _duration.value - _currentPosition.value
                    if (_duration.value > 0 && remaining in 0..8000) {
                        prefetchNextIfNeeded()
                    }
                } catch (e: Exception) {
                    Timber.w(e, "MusicViewModel position polling error")
                }
                delay(100)
            }
        }
    }
    private fun prefetchNextIfNeeded() {
        val list = _queue.value
        val idx = _currentIndex.value
        if (idx < 0 || idx >= list.size - 1) return

        val next = list[idx + 1]
        if (!next.isStream || !next.uri.toString().startsWith("ytmusic://placeholder/")) return
        if (!prefetchingIds.add(next.id)) return // udah lagi diproses, skip

        viewModelScope.launch {
            val resolvedUrl = withContext(Dispatchers.IO) {
                try { ytMusicRepository.resolveStreamUrl(next) } catch (e: Exception) { Timber.w(e, "MusicViewModel prefetchNext failed"); null }
            }
            prefetchingIds.remove(next.id)
            if (resolvedUrl == null) return@launch

            val resolvedNext = next.copy(uri = Uri.parse(resolvedUrl))
            val currentList = _queue.value.toMutableList()
            val nextPos = currentList.indexOfFirst { it.id == next.id }
            if (nextPos == -1) return@launch
            currentList[nextPos] = resolvedNext
            _queue.value = currentList

            playerManager.replaceQueuedItem(nextPos, resolvedNext)
        }
    }
    private fun syncStateFromPlayer() {
        try {
            val p = playerManager.player ?: return
            val item = p.currentMediaItem ?: return
            val mediaId = item.mediaId.toLongOrNull() ?: return
            if (_currentSong.value?.id == mediaId) return

            var song = _queue.value.find { it.id == mediaId }
                ?: _songs.value.find { it.id == mediaId }
                ?: _allStreamSongs.value.find { it.id == mediaId }

            if (song == null) {
                val meta = item.mediaMetadata
                val songUri = item.localConfiguration?.uri ?: android.net.Uri.EMPTY
                song = com.ianocent.musicplayer.data.Song(
                    id = mediaId,
                    title = meta.title?.toString() ?: "Unknown",
                    artist = meta.artist?.toString() ?: "Unknown Artist",
                    duration = p.duration,
                    uri = songUri,
                    album = meta.albumTitle?.toString() ?: "Unknown Album",
                    isStream = songUri.scheme == "http" || songUri.scheme == "https",
                    remoteArtUrl = meta.artworkUri?.toString(),
                    remoteId = item.mediaId.takeIf { it.toLongOrNull() == null || it.toLongOrNull()!! < 0 }
                )
            }
            _currentSong.value = song
            _currentIndex.value = _queue.value.indexOf(song).coerceAtLeast(0)
            _audioSessionId.value = playerManager.getAudioSessionId()
            incrementPlayCount(song.id)
            savePlayerState()
            loadArt(song)
            loadLyric(song)
        } catch (e: Exception) {
            Timber.w(e, "MusicViewModel syncStateFromPlayer failed")
        }
    }

    private fun tryRestoreCurrentSong() {
        if (playerManager.player == null) return
        try {
            tryRestoreCurrentSongInternal()
        } catch (e: Exception) {
            Timber.w(e, "MusicViewModel tryRestoreCurrentSong failed")
        }
    }

    private fun tryRestoreCurrentSongInternal() {
        val mediaId = pendingMediaId ?: return
        var activeSong = _songs.value.find { it.id == mediaId }
        if (activeSong == null) {
            activeSong = _allStreamSongs.value.find { it.id == mediaId }
        }
        if (activeSong == null) {
            activeSong = _queue.value.find { it.id == mediaId }
        }
        if (activeSong == null) {
            val p = playerManager.player ?: return
            val item = p.currentMediaItem ?: return
            val meta = item.mediaMetadata
            val songUri = item.localConfiguration?.uri ?: android.net.Uri.EMPTY
            activeSong = com.ianocent.musicplayer.data.Song(
                id = item.mediaId.toLongOrNull() ?: mediaId,
                title = meta.title?.toString() ?: "Unknown",
                artist = meta.artist?.toString() ?: "Unknown Artist",
                duration = p.duration,
                uri = songUri,
                album = meta.albumTitle?.toString() ?: "Unknown Album",
                isStream = songUri.scheme == "http" || songUri.scheme == "https" ||
                    (item.mediaId.toLongOrNull()?.let { it < 0 } == true),
                remoteArtUrl = meta.artworkUri?.toString(),
                remoteId = item.mediaId.takeIf { it.toLongOrNull() == null || it.toLongOrNull()!! < 0 }
            )
        }
        if (activeSong != null) {
            _currentSong.value = activeSong
            _currentIndex.value = _queue.value.indexOf(activeSong).coerceAtLeast(0)
            _isPlaying.value = playerManager.player?.isPlaying ?: false
            loadArt(activeSong)
            loadLyric(activeSong)
            pendingMediaId = null
        }
    }

    private fun isUriPlayable(song: Song): Boolean {
        // Allow all songs in the queue to show Next/Prev buttons in notification
        return true 
    }

    fun loadSongs() {
        _isLoadingSongs.value = true
        viewModelScope.launch {
            val rawList = withContext(Dispatchers.IO) { repository.getAllSongs() }
            val list = rawList.filter { it.duration >= 60_000L }
            originalOrder = list
            _songs.value = list
            if (_queue.value.isEmpty()) {
                _queue.value = list
            }
            _isLoadingSongs.value = false
            checkMonthlyRecap()
        }
    }
    private var baseQueueBeforeShuffle: List<Song> = emptyList()

    fun toggleShuffle() {
        _isShuffleOn.value = !_isShuffleOn.value
        prefs.edit().putBoolean("is_shuffle_on", _isShuffleOn.value).apply()
        val current = _currentSong.value
        if (_isShuffleOn.value) {
            baseQueueBeforeShuffle = _queue.value
            // Pindahkan lagu yang sekarang ke index 0 dulu, lalu shuffle sisanya,
            // agar current lagu tetap di posisi pertama dan tidak restart.
            val currentSong = current
            val others = _queue.value.filter { it.id != currentSong?.id }.shuffled()
            _queue.value = if (currentSong != null) listOf(currentSong) + others else others
        } else {
            _queue.value = baseQueueBeforeShuffle.ifEmpty { _songs.value }
        }
        _currentIndex.value = _queue.value.indexOf(current).coerceAtLeast(0)
        savePlayerState()
    }
    fun setQueue(newQueue: List<Song>, startSong: Song? = null) {
        genreArtLoadJob?.cancel()
        genreFetchJobs.values.forEach { it.cancel() }
        val currentSong = _currentSong.value

        // Check if same underlying content as current queue (avoid double-shuffle)
        val compareBase = if (_isShuffleOn.value && baseQueueBeforeShuffle.isNotEmpty()) baseQueueBeforeShuffle else _queue.value
        val isSameContent = newQueue.size == compareBase.size &&
            newQueue.zip(compareBase).all { (a, b) -> a.id == b.id }

        if (_isShuffleOn.value) {
            if (!isSameContent) {
                // New playlist/queue — save original order and shuffle
                baseQueueBeforeShuffle = newQueue
                val shuffled = newQueue.toMutableList()
                val target = startSong ?: newQueue.firstOrNull()
                if (target != null) {
                    shuffled.removeAll { it.id == target.id }
                    shuffled.shuffle()
                    shuffled.add(0, target)
                } else {
                    shuffled.shuffle()
                }
                _queue.value = shuffled
            }
            // If same content, keep current shuffled order (user just tapped a song)
        } else {
            _queue.value = newQueue
        }

        val target = startSong ?: _queue.value.firstOrNull()
        target?.let { playSong(it) }
    }

    fun toggleRepeat() {
        _repeatMode.value = playerManager.toggleRepeat()
        prefs.edit().putInt("repeat_mode", _repeatMode.value).apply()
    }


    private suspend fun fadeVolume(from: Float, to: Float, durationMs: Long) {
        val player = playerManager.player ?: return
        val steps = 10
        val interval = durationMs / steps
        val delta = (to - from) / steps
        var currentVol = from
        for (i in 1..steps) {
            currentVol += delta
            player.volume = currentVol.coerceIn(0f, 1f)
            delay(interval)
        }
        player.volume = to.coerceIn(0f, 1f)
    }

    fun playSong(song: Song) {
        val snapshot = _queue.value.toMutableList()
        val index = snapshot.indexOf(song)
        if (index == -1) return

        viewModelScope.launch {
            _showListeningPill.value = true
            hidePillJob?.cancel()
            
            // Smooth Crossfade out
            if (playerManager.player?.isPlaying == true) {
                fadeVolume(1f, 0f, 150)
            } else {
                playerManager.player?.volume = 0f
            }

            _currentIndex.value = index
            _currentSong.value = song

            val resolvedSong = if (song.isStream && song.uri.toString().startsWith("ytmusic://placeholder/")) {
                _isBuffering.value = true
                withContext(Dispatchers.IO) {
                    try {
                        val streamUrl = ytMusicRepository.resolveStreamUrl(song)
                        _isBuffering.value = false
                        if (streamUrl != null) song.copy(uri = Uri.parse(streamUrl)) else song
                    } catch (e: Exception) {
                        _isBuffering.value = false
                        song
                    }
                }
            } else {
                song
            }

            if (index >= 0 && index < snapshot.size) {
                snapshot[index] = resolvedSong
                _queue.value = snapshot
            }
            playerManager.playSong(resolvedSong, snapshot, index)
            
            // Smooth Crossfade in
            fadeVolume(0f, 1f, 150)

            incrementPlayCount(song.id)
            savePlayerState()
            loadArt(song)
            loadLyric(song)
        }
    }

    private fun resolveAndPlayStream(song: Song) {
        val savedIndex = _queue.value.indexOf(song)
        viewModelScope.launch {
            _showListeningPill.value = true
            hidePillJob?.cancel()

            // Fade out if playing
            if (playerManager.player?.isPlaying == true) {
                fadeVolume(1f, 0f, 150)
            } else {
                playerManager.player?.volume = 0f
            }

            val resolvedSong = withContext(Dispatchers.IO) {
                try {
                    val streamUrl = ytMusicRepository.resolveStreamUrl(song)
                    if (streamUrl != null) song.copy(uri = Uri.parse(streamUrl)) else song
                } catch (e: Exception) {
                    song
                }
            }
            val useIndex = if (savedIndex >= 0) savedIndex else _queue.value.indexOf(resolvedSong).coerceAtLeast(0)
            if (resolvedSong.uri != song.uri) {
                val queue = _queue.value.toMutableList()
                if (useIndex >= 0 && useIndex < queue.size) {
                    queue[useIndex] = resolvedSong
                    _queue.value = queue
                }
            }
            playerManager.playSong(
                if (resolvedSong.uri != song.uri) resolvedSong else song,
                _queue.value,
                startIndex = useIndex
            )

            // Fade in
            fadeVolume(0f, 1f, 150)
        }
    }

    fun playNext() {
        val list = _queue.value
        if (list.isEmpty()) return
        val isLast = _currentIndex.value >= list.size - 1

        val nextIndex = when {
            isLast && _repeatMode.value == Player.REPEAT_MODE_ALL -> 0
            isLast -> {
                // Wrap to first song even without repeat — better UX than silent no-op
                0
            }
            else -> _currentIndex.value + 1
        }
        playSong(list[nextIndex])
    }

    fun playPrevious() {
        val list = _queue.value
        if (list.isEmpty()) return
        val curIdx = _currentIndex.value
        val isFirst = curIdx <= 0

        // If more than 3s in, restart current song (standard media player behavior)
        if (_currentPosition.value > 3000 && !isFirst) {
            seekTo(0)
            return
        }

        val prevIndex = when {
            isFirst && _repeatMode.value == Player.REPEAT_MODE_ALL -> list.size - 1
            isFirst -> 0
            else -> curIdx - 1
        }
        playSong(list[prevIndex])
    }

    private val _ambientColor = MutableStateFlow(Color(0xFF333333))
    val ambientColor: StateFlow<Color> = _ambientColor

    private val _paletteColors = MutableStateFlow<List<Color>>(emptyList())
    val paletteColors: StateFlow<List<Color>> = _paletteColors

    private fun loadArt(song: Song) {
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                if (song.isStream && !song.remoteArtUrl.isNullOrEmpty()) {
                    try {
                        val url = java.net.URL(song.remoteArtUrl)
                        val conn = url.openConnection() as java.net.HttpURLConnection
                        conn.doInput = true
                        conn.connect()
                        android.graphics.BitmapFactory.decodeStream(conn.inputStream)
                    } catch (e: Exception) { null }
                } else {
                    AlbumArtLoader.getEmbeddedArt(appContext, song.uri, targetSize = 400)
                }
            }
            _albumArt.value = bitmap
            _ambientColor.value = bitmap?.let {
                AlbumArtLoader.extractDominantColor(it)
            } ?: Color(0xFF333333)
            _paletteColors.value = bitmap?.let {
                AlbumArtLoader.extractPaletteColors(it)
            } ?: emptyList()
        }
    }
    private val _syncedLyric = MutableStateFlow<List<LyricLine>?>(null)
    val syncedLyric: StateFlow<List<LyricLine>?> = _syncedLyric

    private val _plainLyric = MutableStateFlow<String?>(null)
    val plainLyric: StateFlow<String?> = _plainLyric

    private fun loadLyric(song: Song) {
        _syncedLyric.value = null
        _plainLyric.value = null
        _isLyricLoading.value = true
        viewModelScope.launch {
            val synced = withContext(Dispatchers.IO) {
                lyricRepository.fetchSyncedLyric(song.title, song.artist)
            }
            if (synced != null) {
                _syncedLyric.value = synced
            } else {
                _plainLyric.value = withContext(Dispatchers.IO) {
                    lyricRepository.fetchPlainLyric(song.title, song.artist)
                }
            }
            _isLyricLoading.value = false
        }
    }

    fun togglePlayPause() {
        viewModelScope.launch {
            val player = playerManager.player ?: return@launch
            if (player.isPlaying) {
                fadeVolume(1f, 0f, 150)
                playerManager.togglePlayPause()
                player.volume = 1f // Reset volume for next play
            } else {
                player.volume = 0f
                playerManager.togglePlayPause()
                fadeVolume(0f, 1f, 150)
            }
        }
    }

    fun seekTo(positionMs: Long) {
        playerManager.seekTo(positionMs)
        _currentPosition.value = positionMs
    }

    fun getLivePosition(): Long = playerManager.getCurrentPosition()

    override fun onCleared() {
        super.onCleared()
        downloadReceiver?.let { receiver ->
            appContext.unregisterReceiver(receiver)
        }
        playerManager.release()
    }

    fun reorderPlaylistSongs(playlist: Playlist, fromIndex: Int, toIndex: Int) {
        val currentList = _playlists.value.toMutableList()
        val idx = currentList.indexOfFirst { it.id == playlist.id }
        if (idx == -1) return
        val mutableIds = currentList[idx].songIds.toMutableList()
        if (fromIndex !in mutableIds.indices || toIndex !in mutableIds.indices) return
        val item = mutableIds.removeAt(fromIndex)
        mutableIds.add(toIndex, item)
        currentList[idx] = currentList[idx].copy(songIds = mutableIds)
        _playlists.value = currentList
        savePlaylistsToPrefs()
    }

    fun addSongsToPlaylist(playlist: Playlist, songIds: List<Long>) {
        val currentList = _playlists.value.toMutableList()
        val idx = currentList.indexOfFirst { it.id == playlist.id }
        if (idx == -1) return
        val mutableIds = currentList[idx].songIds.toMutableList()
        songIds.forEach { if (it !in mutableIds) mutableIds.add(it) }
        currentList[idx] = currentList[idx].copy(songIds = mutableIds)
        _playlists.value = currentList
        savePlaylistsToPrefs()
    }

    fun checkForUpdate() {
        viewModelScope.launch {
            val info = UpdateManager.checkForUpdate()
            if (info != null) {
                try {
                    val pkgInfo = getApplication<android.app.Application>()
                        .packageManager
                        .getPackageInfo(getApplication<android.app.Application>().packageName, 0)
                    val currentVersionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        pkgInfo.longVersionCode.toInt()
                    } else {
                        @Suppress("DEPRECATION")
                        pkgInfo.versionCode
                    }

                    if (info.versionCode > currentVersionCode) {
                        _updateInfo.value = info
                        _isUpdateAvailable.value = true
                    }
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }

    fun downloadUpdate() {
        val info = _updateInfo.value ?: return
        val context = getApplication<android.app.Application>()
        _isDownloading.value = true

        val downloadId = UpdateManager.startDownload(context, info)
        downloadReceiver = UpdateManager.registerDownloadReceiver(context, downloadId) {
            _isDownloading.value = false
            UpdateManager.installApk(context)
        }
    }

    fun getAudioFormats(song: Song, onResult: (List<AudioFormat>) -> Unit) {
        val remoteId = song.remoteId ?: run {
            onResult(listOf(AudioFormat(
                url = song.uri.toString(),
                mimeType = "audio/mpeg",
                bitrate = 0,
                qualityLabel = "Download"
            )))
            return
        }
        viewModelScope.launch {
            var formats = ytMusicRepository.getAudioFormats(remoteId)

            if (formats.isEmpty()) {
                val resolvedUrl = if (song.uri.toString().startsWith("ytmusic://placeholder/")) {
                    withContext(Dispatchers.IO) { ytMusicRepository.resolveStreamUrl(song) }
                } else song.uri.toString()

                // Retry: resolveStreamUrl will have set visitorData via player request
                formats = ytMusicRepository.getAudioFormats(remoteId)

                if (formats.isEmpty()) {
                    onResult(listOf(AudioFormat(
                        url = resolvedUrl ?: song.uri.toString(),
                        mimeType = "audio/mpeg",
                        bitrate = 0,
                        qualityLabel = "Download"
                    )))
                } else {
                    onResult(formats.sortedByDescending { it.bitrate })
                }
            } else {
                onResult(formats.sortedByDescending { it.bitrate })
            }
        }
    }

    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun downloadSong(song: Song, format: AudioFormat) {
        viewModelScope.launch {
            // 1. Resolve URL if it's a placeholder
            val realUrl = if (format.url.startsWith("ytmusic://placeholder/")) {
                withContext(Dispatchers.IO) {
                    ytMusicRepository.resolveStreamUrl(song)
                }
            } else {
                format.url
            }

            if (realUrl == null || !realUrl.startsWith("http")) {
                Timber.e("MusicViewModel Could not resolve download URL for: ${song.title}")
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(appContext, "Download failed: URL could not be resolved", android.widget.Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            val context = getApplication<android.app.Application>()
            val downloadManager = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            
            // Clean filename: remove illegal characters
            val cleanTitle = song.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val cleanArtist = song.artist.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val fileName = "$cleanTitle - $cleanArtist.mp3"

            val request = android.app.DownloadManager.Request(android.net.Uri.parse(realUrl))
                .setTitle(song.title)
                .setDescription("Downloading $cleanArtist - ${format.qualityLabel}")
                .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_MUSIC, "IanPlayer/$fileName")
                .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val downloadId = downloadManager.enqueue(request)
            
            val completionReceiver = com.ianocent.musicplayer.data.DownloadCompletionReceiver(
                downloadId,
                song
            ) {
                refreshSongs()
            }
            
            val filter = android.content.IntentFilter(android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(completionReceiver, filter, android.content.Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(completionReceiver, filter)
            }
        }
    }

    private fun refreshSongs(scannedFilePath: String? = null) {
        viewModelScope.launch {
            if (scannedFilePath != null) {
                withContext(Dispatchers.IO) {
                    suspendCancellableCoroutine<Unit> { cont ->
                        android.media.MediaScannerConnection.scanFile(
                            appContext,
                            arrayOf(scannedFilePath),
                            arrayOf("audio/mpeg")
                        ) { _, _ -> if (cont.isActive) cont.resume(Unit) {} }
                    }
                }
            }
            val rawList = withContext(Dispatchers.IO) { repository.getAllSongs() }
            val list = rawList.filter { it.duration >= 60_000L }
            originalOrder = list
            _songs.value = list
            if (_currentSong.value == null) {
                _queue.value = list
            }
        }
    }    fun dismissUpdate() {
        _isUpdateAvailable.value = false
        _updateInfo.value = null
    }

    private val _lastDeletedSong = MutableStateFlow<Song?>(null)
    val lastDeletedSong: StateFlow<Song?> = _lastDeletedSong

    private var deleteTimerJob: Job? = null

    fun deleteSong(song: Song) {
        _lastDeletedSong.value = song
        viewModelScope.launch {
            try {
                val success = withContext(Dispatchers.IO) { repository.deleteSong(song) }
                if (success) {
                    confirmDelete(song)
                }
            } catch (e: Exception) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                    _deleteIntentSender.emit(e.userAction.actionIntent.intentSender)
                } else {
                    Timber.e(e, "MusicViewModel delete failed")
                }
            }
        }
    }

    fun confirmDelete(song: Song) {
        _songs.value = _songs.value.filter { it.id != song.id }
        _queue.value = _queue.value.filter { it.id != song.id }
        if (_currentSong.value?.id == song.id) {
            _currentSong.value = null
            playerManager.player?.stop()
        }
        _playlists.value = _playlists.value.map { playlist ->
            playlist.copy(songIds = playlist.songIds.filter { it != song.id }.toMutableList())
        }
        savePlaylistsToPrefs()
    }

    fun undoDelete() {
        val song = _lastDeletedSong.value ?: return
        deleteTimerJob?.cancel()
        _lastDeletedSong.value = null
        _songs.value = (_songs.value + song).sortedBy { it.title.lowercase() }
        _queue.value = _queue.value + song
    }

    fun updateSongInfo(songId: Long, newTitle: String, newArtist: String, newImageUri: Uri? = null) {
        pendingUpdateInfo = PendingUpdate(songId, newTitle, newArtist, newImageUri)
        viewModelScope.launch {
            val song = _songs.value.find { it.id == songId } ?: return@launch
            
            try {
                withContext(Dispatchers.IO) {
                    var newBitmap: android.graphics.Bitmap? = null
                    if (newImageUri != null) {
                        try {
                            val inputStream = appContext.contentResolver.openInputStream(newImageUri)
                            newBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                            inputStream?.close()
                        } catch (e: Exception) {
                            Timber.e(e, "MusicViewModel Failed to load picked image")
                        }
                    }

                    // 1. Update file metadata if possible
                    val filePath = getFilePathFromUri(song.uri)
                    if (filePath != null) {
                        val updatedSong = song.copy(title = newTitle, artist = newArtist)
                        com.ianocent.musicplayer.data.MetadataWriter.writeMetadata(appContext, filePath, updatedSong, newBitmap)
                        
                        // Trigger scan to update MediaStore from file tags
                        android.media.MediaScannerConnection.scanFile(
                            appContext,
                            arrayOf(filePath),
                            null,
                            null
                        )
                    }
                    
                    // 2. Update MediaStore directly
                    repository.updateSongInfo(songId, newTitle, newArtist)
                }
                
                // 3. Update UI state
                _songs.value = _songs.value.map {
                    if (it.id == songId) it.copy(title = newTitle, artist = newArtist) else it
                }
                _queue.value = _queue.value.map {
                    if (it.id == songId) it.copy(title = newTitle, artist = newArtist) else it
                }
                if (_currentSong.value?.id == songId) {
                    _currentSong.value = _currentSong.value?.copy(title = newTitle, artist = newArtist)
                }
                
                artCache.remove(songId)
                highResArtCache.remove(-songId)
                pendingUpdateInfo = null
            } catch (e: Exception) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                    _editIntentSender.emit(e.userAction.actionIntent.intentSender)
                } else {
                    Timber.e(e, "MusicViewModel update failed")
                }
            }
        }
    }

    private fun getFilePathFromUri(uri: Uri): String? {
        if (uri.scheme != "content") return uri.path
        val projection = arrayOf(android.provider.MediaStore.Audio.Media.DATA)
        return appContext.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DATA))
            } else null
        }
    }
}