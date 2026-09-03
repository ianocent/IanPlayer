package com.ianocent.musicplayer.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import timber.log.Timber
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.ianocent.musicplayer.data.AlbumArtLoader
import com.ianocent.musicplayer.data.AppDatabase
import com.ianocent.musicplayer.data.ArtLoader
import com.ianocent.musicplayer.data.AudioFormat
import com.ianocent.musicplayer.data.LyricLine
import com.ianocent.musicplayer.data.LyricRepository
import com.ianocent.musicplayer.data.LyricResult
import com.ianocent.musicplayer.data.LyricSource
import com.ianocent.musicplayer.data.MonthlyRecap
import com.ianocent.musicplayer.data.MusicRepository
import com.ianocent.musicplayer.data.Song
import com.ianocent.musicplayer.data.SongStore
import com.ianocent.musicplayer.data.SocialSignalListener
import com.ianocent.musicplayer.player.IanVoiceAssistantService
import com.ianocent.musicplayer.player.PlaybackService
import com.ianocent.musicplayer.player.PlayerGateway
import com.ianocent.musicplayer.player.AutoFillContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Color
import com.ianocent.musicplayer.data.Playlist
import com.ianocent.musicplayer.data.RecapBuilder
import com.ianocent.musicplayer.data.SettingsStore
import com.ianocent.musicplayer.data.UpdateInfo
import com.ianocent.musicplayer.data.YTMusicRepository
import com.ianocent.musicplayer.data.SongDownloader
import com.ianocent.musicplayer.data.StreamSearchResult
import com.ianocent.musicplayer.data.StreamResolvers
import com.ianocent.musicplayer.data.StreamSources
import com.ianocent.musicplayer.data.YouTubeStreamResolver
import com.ianocent.musicplayer.data.TidalStreamResolver
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
import com.ianocent.musicplayer.data.TidalRepository
import com.ianocent.musicplayer.data.LocationTracker
import com.ianocent.musicplayer.data.LocationData
import com.ianocent.musicplayer.data.FirebaseLocationSync
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.io.InputStream
import java.io.File

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val prefs = application.getSharedPreferences(SongStore.PREFS_NAME, 0)
    private val songStore = SongStore(prefs, viewModelScope)
    private val settingsStore = SettingsStore(prefs)

    private val ytMusicRepository = YTMusicRepository(application.applicationContext)
    private val tidalRepository = TidalRepository()
    private val songDownloader = SongDownloader(appContext)
    private val locationTracker = LocationTracker(application.applicationContext)
    private val firebaseLocationSync = FirebaseLocationSync()
    private val streamResolvers = StreamResolvers(listOf(
        YouTubeStreamResolver(ytMusicRepository),
        TidalStreamResolver(YouTubeStreamResolver(ytMusicRepository)) { query ->
            (ytMusicRepository.searchSongs(query, onPartial = {}) as? StreamSearchResult.Success)?.songs ?: emptyList()
        }
    ))

    // Stream search: debounce + dual-provider fan-out + paging live in the module.
    val streamSearch = StreamSearchController(
        scope = viewModelScope,
        ytMusicRepository = ytMusicRepository,
        tidalRepository = tidalRepository,
        onSongsSeen = { songStore.addToStreamSongsCache(it) },
        onResultsSettled = { preResolveSearchResults() }
    )
    
    private val _deleteIntentSender = MutableSharedFlow<IntentSender>(extraBufferCapacity = 1)
    val deleteIntentSender: SharedFlow<IntentSender> = _deleteIntentSender
    
    private val _editIntentSender = MutableSharedFlow<IntentSender>(extraBufferCapacity = 1)
    val editIntentSender: SharedFlow<IntentSender> = _editIntentSender

    data class PendingUpdate(val id: Long, val title: String, val artist: String, val uri: Uri?)
    var pendingUpdateInfo: PendingUpdate? = null

    var pendingDeleteSong: Song? = null

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable !is CancellationException) {
            throwable.printStackTrace()
        }
    }

    // ---- Genre & Mood browsing ----
    data class Category(val name: String, val query: String, val isMood: Boolean = false)

    // Genre & mood browsing. Queries anchor on US Billboard / UK charts and are sent with
    // gl=US (see selectGenre/loadGenreArtworks) so YT Music returns Western hits, not
    // device-locale (ID) local content or random non-chart songs.
    private val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)

    private val _genres = listOf(
        Category("Pop", "popular pop songs $currentYear hits"),
        Category("Rock", "rock music hits modern classic"),
        Category("Hip Hop", "hip hop rap songs hits"),
        Category("R&B", "r&b soul music songs"),
        Category("Electronic", "edm dance electronic music"),
        Category("Jazz", "jazz music classics"),
        Category("Indie", "indie alternative rock songs"),
        Category("Metal", "heavy metal hard rock songs"),
        Category("K-Pop", "kpop korean pop songs hits"),
        Category("Reggae", "reggae music chill vibes"),
        Category("Country", "country music hits popular"),
        Category("Lofi", "lofi hip hop beats relaxing")
    )

    private val _moods = listOf(
        Category("Sad", "sad emotional pop rock songs hits", true),
        Category("Energetic", "upbeat energy workout songs", true),
        Category("Chill", "chill lofi relaxing music", true),
        Category("Happy", "happy feel good songs", true),
        Category("Romantic", "romantic love songs hits", true),
        Category("Dark", "dark moody aesthetic songs", true),
        Category("Calm", "calm acoustic peaceful music", true),
        Category("Focus", "focus study instrumental music", true),
        Category("Party", "party dance hits banger songs", true),
        Category("Nostalgia", "nostalgic throwback classic hits", true),
        Category("Heartbreak", "heartbreak breakup sad songs", true),
        Category("Confident", "confident boss energy songs", true)
    )

    val genres: List<Category> = _genres.shuffled()
    val moods: List<Category> = _moods.shuffled()

    // Contextual personalization: Time-based query
    fun getContextualQuery(): String {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..10 -> "morning upbeat coffee"
            in 11..16 -> "productive afternoon focus"
            in 17..20 -> "sunset evening chill"
            else -> "night drive aesthetic"
        }
    }

    fun getContextualTitle(): String {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..10 -> "Morning Coffee"
            in 11..16 -> "Afternoon Flow"
            in 17..20 -> "Evening Sunset"
            else -> "Late Night Vibes"
        }
    }

    private val _selectedGenre = MutableStateFlow<String?>(null)
    val selectedGenre: StateFlow<String?> = _selectedGenre

    private val _genreSongs = MutableStateFlow<Map<String, List<Song>>>(emptyMap())
    val genreSongs: StateFlow<Map<String, List<Song>>> = _genreSongs

    private val _isGenreLoading = MutableStateFlow(false)
    val isGenreLoading: StateFlow<Boolean> = _isGenreLoading

    private val _genreFirstSong = MutableStateFlow<Map<String, Song?>>(emptyMap())
    val genreFirstSong: StateFlow<Map<String, Song?>> = _genreFirstSong
    private var genreArtLoaded = false
    private var genreArtLoadJob: Job? = null

    fun loadGenreArtworks() {
        if (genreArtLoaded) return
        if (genreArtLoadJob?.isActive == true) return
        genreArtLoadJob = viewModelScope.launch {
            genreArtLoaded = true
            val allCats = genres + moods
            for (cat in allCats) {
                if (!isActive) break
                try {
                    val result = ytMusicRepository.searchSongs(cat.query, {}, gl = "US")
                    if (result is StreamSearchResult.Success && result.songs.isNotEmpty()) {
                        val song = result.songs.first()
                        _genreFirstSong.value = _genreFirstSong.value + (cat.name to song)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private val genreFetchJobs = mutableMapOf<String, Job>()

    fun selectGenre(categoryName: String, forceRefresh: Boolean = false) {
        _selectedGenre.value = categoryName
        if (!forceRefresh && _genreSongs.value.containsKey(categoryName) && _genreSongs.value[categoryName]?.isNotEmpty() == true) return

        val cat = (genres + moods).find { it.name == categoryName } ?: return
        _isGenreLoading.value = true
        genreFetchJobs[categoryName]?.cancel()
        genreFetchJobs[categoryName] = viewModelScope.launch {
            try {
                val result = ytMusicRepository.searchSongs(cat.query, { newSongs ->
                    val current = _genreSongs.value.toMutableMap()
                    val merged = (current[categoryName] ?: emptyList()) + newSongs
                    current[categoryName] = merged.distinctBy { it.id }
                    _genreSongs.value = current
                }, gl = "US")
                if (result is StreamSearchResult.Success) {
                    addToStreamSongsCache(result.songs)
                    val current = _genreSongs.value.toMutableMap()
                    current[categoryName] = result.songs
                    _genreSongs.value = current
                    saveListToPrefs("cached_genre_$categoryName", result.songs)
                }
            } catch (e: Exception) {
                Timber.e(e, "MusicViewModel fetch category ${categoryName} failed")
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
    private val preResolvedIds = ConcurrentHashMap.newKeySet<Long>()

    // ---- Location-based features ----
    val locationData: StateFlow<LocationData?> = locationTracker.location
    private val _locationCity = MutableStateFlow<String?>(null)
    val locationCity: StateFlow<String?> = _locationCity

    // Pending location sync — holds song info while waiting for location fix
    private data class PendingLocationSync(val songTitle: String, val artist: String)
    private val pendingLocationSync = MutableStateFlow<PendingLocationSync?>(null)

    // == Stream songs cache (persist stream song metadata for playlists) ==
    // Owned by SongStore; loaded synchronously at its construction.
    val streamSongsCache: StateFlow<Map<Long, Song>> = songStore.streamSongsCache

    private fun mergeById(current: List<Song>, incoming: List<Song>): List<Song> =
        StreamSearchController.mergeById(current, incoming)

    private fun addToStreamSongsCache(songs: List<Song>) {
        songStore.addToStreamSongsCache(songs)
    }

    fun fetchTrending(force: Boolean = false) {
        if (!force && trendingLoadAttempted) return
        trendingLoadAttempted = true
        _isTrendingLoading.value = true
        viewModelScope.launch {
            try {
                // Mix trending songs with contextual personalized songs
                val contextualQuery = getContextualQuery()
                val countryCode = locationData.value?.countryCode ?: "US"
                _locationCity.value = locationData.value?.city
                
                val result = ytMusicRepository.fetchHomeSongs({ newSongs ->
                    addToStreamSongsCache(newSongs)
                    val merged = mergeById(_trendingSongs.value, newSongs)
                    _trendingSongs.value = merged
                }, countryCode = countryCode)

                // Add contextual personalization songs (time-based, using location region)
                ytMusicRepository.searchSongs(contextualQuery, { contextualSongs ->
                    addToStreamSongsCache(contextualSongs)
                    val merged = mergeById(_trendingSongs.value, contextualSongs)
                    _trendingSongs.value = merged
                }, gl = countryCode)

                if (result is StreamSearchResult.Success) {
                    addToStreamSongsCache(result.songs)
                    saveListToPrefs("cached_trending", _trendingSongs.value)
                }
            } catch (e: Exception) {
                Timber.e(e, "MusicViewModel fetchTrending failed")
            } finally {
                _isTrendingLoading.value = false
            }
            preResolveTrending()
        }
    }

    fun refreshTrending() {
        trendingLoadAttempted = false
        fetchTrending(force = true)
    }

    // ---- Location tracking ----
    fun startLocationTracking() {
        locationTracker.startTracking()
    }

    fun stopLocationTracking() {
        locationTracker.stopTracking()
    }

    fun syncLocationToFirebase(songTitle: String, artist: String) {
        val loc = locationData.value
        if (loc == null) {
            Timber.d("syncLocationToFirebase: locationData null, queuing sync for: $songTitle")
            pendingLocationSync.value = PendingLocationSync(songTitle, artist)
            return
        }
        firebaseLocationSync.syncLocation(loc, songTitle, artist, _userName.value)
    }

    // ---- For You: personalization from social-media feed signals ----
    // Signals collected on-device by SocialSignalListener (system notification access:
    // no mic, no camera, no screen recording, no foreground service). Stored only in
    // SharedPreferences on this device, pruned after 72h. Opt-in, default off.
    private val _socialSignalsEnabled = MutableStateFlow(
        settingsStore.isSocialSignalsEnabled
    )
    val socialSignalsEnabled: StateFlow<Boolean> = _socialSignalsEnabled

    private val _socialAccessGranted = MutableStateFlow(false)
    val socialAccessGranted: StateFlow<Boolean> = _socialAccessGranted

    private val _forYouSignals = MutableStateFlow<List<Pair<String, Long>>>(emptyList())
    val forYouSignals: StateFlow<List<Pair<String, Long>>> = _forYouSignals

    private val _forYouSongs = MutableStateFlow<List<Song>>(emptyList())
    val forYouSongs: StateFlow<List<Song>> = _forYouSongs

    private val _isForYouLoading = MutableStateFlow(false)
    val isForYouLoading: StateFlow<Boolean> = _isForYouLoading

    private var forYouJob: Job? = null
    private var forYouLoadAttempted = false

    fun setSocialSignalsEnabled(enabled: Boolean) {
        _socialSignalsEnabled.value = enabled
        settingsStore.isSocialSignalsEnabled = enabled
        if (enabled) {
            refreshSocialAccess()
            refreshForYou(force = true)
        } else {
            forYouJob?.cancel()
            _forYouSongs.value = emptyList()
            _forYouSignals.value = emptyList()
        }
    }

    fun refreshSocialAccess() {
        _socialAccessGranted.value = SocialSignalListener.isAccessGranted(appContext)
    }

    fun clearSocialSignals() {
        songStore.clearSocialSignals()
        _forYouSignals.value = emptyList()
        _forYouSongs.value = emptyList()
        saveListToPrefs("cached_foryou", emptyList())
    }

    private fun loadSocialSignals(): List<Pair<String, Long>> = songStore.loadSocialSignals()

    fun refreshForYou(force: Boolean = false) {
        if (!_socialSignalsEnabled.value) return
        if (!force && forYouLoadAttempted) return
        forYouLoadAttempted = true

        forYouJob?.cancel()
        _isForYouLoading.value = true
        val stored = loadSocialSignals()
        _forYouSignals.value = stored
        
        forYouJob = viewModelScope.launch {
            // Wait for local songs to load if we're falling back to play counts
            if (stored.isEmpty() && _songs.value.isEmpty()) {
                withTimeoutOrNull(2000) {
                    while (_songs.value.isEmpty() && isActive) delay(100)
                }
            }

            val signals = if (stored.isNotEmpty()) stored.take(8) else {
                _playCounts.value.entries
                    .sortedByDescending { it.value }
                    .take(5)
                    .mapNotNull { (id, _) -> _songs.value.find { it.id == id }?.artist }
                    .filter { it.isNotBlank() && it != "Unknown Artist" }
                    .distinct()
                    .map { it to System.currentTimeMillis() }
            }
            
            if (signals.isEmpty() && _forYouSongs.value.isNotEmpty()) {
                // Don't overwrite existing cache with empty results if we have no signals
                _isForYouLoading.value = false
                return@launch
            }

            val results = mutableListOf<Song>()
            val seen = mutableSetOf<Long>()
            for ((signal, _) in signals) {
                if (!isActive) break
                try {
                    val res = ytMusicRepository.searchSongs(signal, {}, gl = "US")
                    if (res is StreamSearchResult.Success) {
                        for (s in res.songs) {
                            if (seen.add(s.id)) results.add(s)
                            if (results.size >= 24) break
                        }
                    }
                } catch (_: Exception) {}
                if (results.size >= 24) break
            }
            
            if (results.isNotEmpty() || !force) {
                _forYouSongs.value = results
                saveListToPrefs("cached_foryou", results)
            }
            _isForYouLoading.value = false
        }
    }

    private var preResolveJob: Job? = null

    private fun preResolveTrending() {
        val trending = _trendingSongs.value
        if (trending.isEmpty()) return
        preResolveJob?.cancel()
        preResolveJob = viewModelScope.launch {
            // Pre-resolve 20 item trending agar transisi mulus
            streamResolvers.preResolve(
                songs = trending,
                limit = 20,
                staggerMs = 200,
                attempted = preResolvedIds
            ) { resolved ->
                _trendingSongs.value = _trendingSongs.value.map {
                    if (it.id == resolved.id) resolved else it
                }
                streamSearch.replaceVisibleSong(resolved)
            }
        }
    }

    private fun preResolveSearchResults() {
        val searchResults = streamSearch.visibleResults.value
        if (searchResults.isEmpty()) return
        preResolveJob?.cancel()
        preResolveJob = viewModelScope.launch {
            // Pre-resolve hingga 40 item hasil pencarian (Satu halaman penuh)
            streamResolvers.preResolve(
                songs = searchResults,
                limit = 40,
                staggerMs = 300,
                attempted = preResolvedIds
            ) { resolved ->
                streamSearch.replaceVisibleSong(resolved)
            }
        }
    }

    // ---- Stream Search ----
    // State and behaviour live in StreamSearchController; the VM only exposes it.
    val streamSongs: StateFlow<List<Song>> get() = streamSearch.visibleResults
    val isSearchingRemote: StateFlow<Boolean> get() = streamSearch.isSearching
    val streamParsingFailed: StateFlow<Boolean> get() = streamSearch.parsingFailed

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
        songStore.saveFavoriteIds(updated)
    }

    fun isFavorite(songId: Long): Boolean = _favoriteIds.value.contains(songId)

    private fun loadFavoriteIds() {
        _favoriteIds.value = songStore.loadFavoriteIds()
    }

    fun setSortMode(mode: Int) {
        _sortMode.value = mode
        settingsStore.sortMode = mode
    }

    private fun loadPlayCounts(): Map<Long, Int> = songStore.loadPlayCounts()

    private fun savePlayCounts(counts: Map<Long, Int>) {
        songStore.savePlayCounts(counts)
    }

    fun incrementPlayCount(songId: Long) {
        val counts = _playCounts.value.toMutableMap()
        counts[songId] = (counts[songId] ?: 0) + 1
        _playCounts.value = counts
        savePlayCounts(counts)
        recordPlayHistory(songId)
    }

    private fun recordPlayHistory(songId: Long) {
        songStore.recordPlayed(songId)
    }

    fun checkMonthlyRecap() {
        val now = System.currentTimeMillis()
        val lastCheck = settingsStore.lastRecapCheckTs
        val ONE_MONTH = 30L * 24 * 60 * 60 * 1000
        if (now - lastCheck < ONE_MONTH) return
        settingsStore.lastRecapCheckTs = now
        computeMonthlyRecap()
    }

    private fun computeMonthlyRecap() {
        if (_songs.value.isEmpty()) return
        viewModelScope.launch {
            val recap = withContext(Dispatchers.IO) {
                try {
                    RecapBuilder.build(
                        history = songStore.loadPlayHistory(),
                        library = _songs.value,
                        nowMs = System.currentTimeMillis()
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
        val history = songStore.loadPlayHistory().filter { it.second >= RecapBuilder.historyWindowStart(now) }

        if (history.isEmpty() || history.size < 5) {
            // Generate mock recap so the card is testable
            val mockSongs = _songs.value.take(5).ifEmpty {
                listOf(Song(0, "Sample Song", "Sample Artist", 240000, Uri.EMPTY))
            }
            _monthlyRecap.value = MonthlyRecap(
                monthLabel = RecapBuilder.monthLabel(now),
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
    fun searchRemoteSongs(query: String) = streamSearch.query(query)

    fun loadMoreStreamSongs() = streamSearch.loadMore()

    private val _playlists = MutableStateFlow(songStore.loadPlaylists())
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
        val localMap = _songs.value.associateBy { it.id }
        val cacheMap = songStore.cachedSongs
        return playlist.songIds.mapNotNull { id -> localMap[id] ?: cacheMap[id] }
    }

    fun deletePlaylist(playlist: Playlist) {
        _playlists.value = _playlists.value.filter { it.id != playlist.id }
        savePlaylistsToPrefs()
    }

    fun exportPlaylistM3u(playlist: Playlist, targetUri: Uri, onDone: (Int) -> Unit) {
        val songs = getSongsInPlaylist(playlist)
        viewModelScope.launch(Dispatchers.IO) {
            val m3u = repository.buildM3u(playlist.name, songs)
            val wrote = try {
                appContext.contentResolver.openOutputStream(targetUri)?.use { out ->
                    out.write(m3u.toByteArray(Charsets.UTF_8))
                } != null
            } catch (e: Exception) {
                false
            }
            withContext(Dispatchers.Main) { onDone(if (wrote) songs.size else -1) }
        }
    }

    fun importPlaylistFromM3u(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val text = try {
                appContext.contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            } catch (e: Exception) {
                ""
            }
            val (suggestedName, entries) = repository.parseM3u(text)
            val allLibrarySongs = _songs.value + songStore.cachedSongs.values
            val matched = repository.matchM3uEntries(entries, allLibrarySongs)
            withContext(Dispatchers.Main) {
                if (matched.isEmpty()) {
                    android.widget.Toast.makeText(
                        appContext, "No songs matched in M3U file", android.widget.Toast.LENGTH_SHORT
                    ).show()
                    return@withContext
                }
                val baseName = suggestedName ?: uri.lastPathSegment
                    ?.substringAfterLast(':')
                    ?.substringAfterLast('/')
                    ?.substringBeforeLast('.')
                    ?.takeIf { it.isNotBlank() }
                    ?: "Imported Playlist"

                createPlaylist("$baseName (${matched.size})", matched.map { it.id })
                android.widget.Toast.makeText(
                    appContext, "Imported ${matched.size} songs to \"$baseName\"", android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun savePlaylistsToPrefs() {
        songStore.savePlaylists(_playlists.value)
    }
    private val _isDarkMode = MutableStateFlow(settingsStore.isDarkMode)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode

    private val _isVoiceAssistantEnabled = MutableStateFlow(settingsStore.isVoiceAssistantEnabled)
    val isVoiceAssistantEnabled: StateFlow<Boolean> = _isVoiceAssistantEnabled

    fun toggleVoiceAssistant() {
        val newValue = !_isVoiceAssistantEnabled.value
        _isVoiceAssistantEnabled.value = newValue
        settingsStore.isVoiceAssistantEnabled = newValue

        if (newValue) {
            IanVoiceAssistantService.start(appContext)
        } else {
            IanVoiceAssistantService.stop(appContext)
        }
    }

    fun toggleDarkMode() {
        _isDarkMode.value = !_isDarkMode.value
        settingsStore.isDarkMode = _isDarkMode.value
    }

    private val _isPillAtBottom = MutableStateFlow(settingsStore.isPillAtBottom)
    val isPillAtBottom: StateFlow<Boolean> = _isPillAtBottom

    fun setPillAtBottom(atBottom: Boolean) {
        _isPillAtBottom.value = atBottom
        settingsStore.isPillAtBottom = atBottom
    }

    private val _miniLayoutIndex = MutableStateFlow(settingsStore.miniLayoutIndex)
    val miniLayoutIndex: StateFlow<Int> = _miniLayoutIndex

    fun setMiniLayoutIndex(index: Int) {
        _miniLayoutIndex.value = index
        settingsStore.miniLayoutIndex = index
    }

    private val _userName = MutableStateFlow(settingsStore.userName)
    val userName: StateFlow<String> = _userName

    fun setUserName(name: String) {
        _userName.value = name
        settingsStore.userName = name
    }

    private val _avatarUri = MutableStateFlow(settingsStore.avatarUri)
    val avatarUri: StateFlow<String?> = _avatarUri

    fun setAvatarUri(uri: String?) {
        _avatarUri.value = uri
        settingsStore.avatarUri = uri
    }

    private val _tabOrder = MutableStateFlow(settingsStore.tabOrder)
    val tabOrder: StateFlow<String> = _tabOrder

    fun setTabOrder(order: String) {
        _tabOrder.value = order
        settingsStore.tabOrder = order
    }

    private val _showListeningPill = MutableStateFlow(false)
    val showListeningPill: StateFlow<Boolean> = _showListeningPill

    private var hidePillJob: Job? = null

    private fun startHidePillTimer() {
        hidePillJob?.cancel()
        hidePillJob = viewModelScope.launch {
            delay(60_000) // 1 minute
            if (!playbackGateway.isPlaying.value) {
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
    fun reorderUpNext(fromIndexInUpNext: Int, toIndexInUpNext: Int) =
        playbackGateway.reorderUpNext(fromIndexInUpNext, toIndexInUpNext)

    private val repository = MusicRepository(application)

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs

    // -- Smart playlists (derived from play counts + dateAdded) --
    val mostPlayedSongs: StateFlow<List<Song>> = combine(_songs, _playCounts) { songs, counts ->
        songs.filter { it.id in counts }
            .sortedByDescending { counts[it.id] ?: 0 }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val recentlyAddedSongs: StateFlow<List<Song>> = _songs.map { songs ->
        songs.sortedByDescending { it.dateAdded }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val neverPlayedSongs: StateFlow<List<Song>> = combine(_songs, _playCounts) { songs, counts ->
        songs.filter { it.id !in counts }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _isLoadingSongs = MutableStateFlow(false)
    val isLoadingSongs: StateFlow<Boolean> = _isLoadingSongs

    private val _albumArt = MutableStateFlow<android.graphics.Bitmap?>(null)
    val albumArt: StateFlow<android.graphics.Bitmap?> = _albumArt

    private val lyricRepository = LyricRepository(
        AppDatabase.getInstance(application.applicationContext).lyricCacheDao()
    )

    private val _lyric = MutableStateFlow<String?>(null)
    val lyric: StateFlow<String?> = _lyric

    private val _isLyricLoading = MutableStateFlow(false)
    val isLyricLoading: StateFlow<Boolean> = _isLyricLoading

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo

    private val _isUpdateAvailable = MutableStateFlow(false)
    val isUpdateAvailable: StateFlow<Boolean> = _isUpdateAvailable

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading

    private val _isDownloaded = MutableStateFlow(false)
    val isDownloaded: StateFlow<Boolean> = _isDownloaded

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

    // Art pipeline lives in ArtLoader (Coil-backed): memory + disk cache,
    // request dedup and high-res URL rules are the module's implementation detail.
    private val artLoader = ArtLoader(appContext)

    fun getCachedArt(song: Song, onLoaded: (Bitmap?) -> Unit) {
        viewModelScope.launch {
            onLoaded(artLoader.load(song, highRes = true))
        }
    }

    fun getHighResArt(song: Song, onLoaded: (Bitmap?) -> Unit) {
        viewModelScope.launch {
            onLoaded(artLoader.load(song, highRes = true))
        }
    }
    private var downloadReceiver: BroadcastReceiver? = null

    lateinit var playbackGateway: PlayerGateway

    val queue: StateFlow<List<Song>> get() = playbackGateway.queue
    val currentSong: StateFlow<Song?> get() = playbackGateway.currentSong
    val isPlaying: StateFlow<Boolean> get() = playbackGateway.isPlaying
    val currentPosition: StateFlow<Long> get() = playbackGateway.currentPosition
    val duration: StateFlow<Long> get() = playbackGateway.duration
    val isShuffleOn: StateFlow<Boolean> get() = playbackGateway.isShuffleOn
    val repeatMode: StateFlow<Int> get() = playbackGateway.repeatMode
    val isBuffering: StateFlow<Boolean> get() = playbackGateway.isBuffering
    val audioSessionId: StateFlow<Int> get() = playbackGateway.audioSessionId

    private fun saveListToPrefs(key: String, songs: List<Song>) {
        songStore.saveCachedSongIds(key, songs)
    }

    private fun loadListFromPrefs(key: String): List<Song> {
        return songStore.loadCachedSongs(key)
    }

    init {
        _playCounts.value = loadPlayCounts()
        _sortMode.value = settingsStore.sortMode
        loadFavoriteIds()
        
        // Load cached stream content
        _trendingSongs.value = loadListFromPrefs("cached_trending")
        _forYouSongs.value = loadListFromPrefs("cached_foryou")
        val cachedGenres = mutableMapOf<String, List<Song>>()
        (genres + moods).forEach { cat ->
            val songs = loadListFromPrefs("cached_genre_${cat.name}")
            if (songs.isNotEmpty()) {
                cachedGenres[cat.name] = songs
                _genreFirstSong.value = _genreFirstSong.value + (cat.name to songs.first())
            }
        }
        _genreSongs.value = cachedGenres

        checkForUpdate()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ytMusicRepository.cleanupExpiredCache()
            } catch (e: Exception) {
                Timber.w(e, "MusicViewModel cleanupExpiredCache failed")
            }
        }
        fetchTrending()
        refreshSocialAccess()

        playbackGateway = PlayerGateway(
            context = appContext,
            prefs = prefs,
            songStore = songStore,
            scope = viewModelScope,
            ytMusicRepository = ytMusicRepository,
            streamResolvers = streamResolvers,
            getSongs = { _songs.value },
            getAllStreamSongs = { streamSearch.allResults.value },
            onSongPlayed = { song ->
                if (song.isStream) addToStreamSongsCache(listOf(song))
                playbackGateway.markAsPlayed(song.id)
                incrementPlayCount(song.id)
                loadArt(song)
                loadLyric(song)
                syncLocationToFirebase(song.title, song.artist)
            },
            onBufferingChange = { },
            onIsPlayingChange = { isPlaying ->
                if (isPlaying) {
                    _showListeningPill.value = true
                    hidePillJob?.cancel()
                } else {
                    startHidePillTimer()
                }
            }
        )

        playbackGateway.restorePlayerState()

        playbackGateway.initialize { hasRestoredSong, restoredIsPlaying ->
            if (hasRestoredSong) {
                if (restoredIsPlaying) {
                    _showListeningPill.value = true
                } else {
                    startHidePillTimer()
                }
            }
        }

        viewModelScope.launch {
            _songs.collect {
                playbackGateway.tryRestoreCurrentSongOnSongsChanged()
            }
        }

        playbackGateway.startPositionPolling()

        // Watch for location fix and sync any pending location data
        viewModelScope.launch {
            locationData.collect { loc ->
                if (loc != null) {
                    val pending = pendingLocationSync.value
                    if (pending != null) {
                        Timber.d("Location now available, syncing pending: ${pending.songTitle}")
                        firebaseLocationSync.syncLocation(loc, pending.songTitle, pending.artist, _userName.value)
                        pendingLocationSync.value = null
                    }
                }
            }
        }
    }


    fun loadSongs() {
        _isLoadingSongs.value = true
        viewModelScope.launch {
            val rawList = withContext(Dispatchers.IO) { repository.getAllSongs() }
            val list = rawList.filter { it.duration >= 60_000L }
            _songs.value = list
            if (playbackGateway.queue.value.isEmpty()) {
                playbackGateway.setDefaultQueueIfEmpty(list)
            }
            _isLoadingSongs.value = false
            checkMonthlyRecap()
        }
    }
    fun toggleShuffle() = playbackGateway.toggleShuffle()

    fun setQueue(newQueue: List<Song>, startSong: Song? = null) {
        playbackGateway.setQueue(newQueue, startSong)
        val song = startSong ?: newQueue.firstOrNull()
        song?.let { syncLocationToFirebase(it.title, it.artist) }
    }
    fun toggleRepeat() = playbackGateway.toggleRepeat()

    fun playSong(song: Song) {
        Timber.d("MusicViewModel.playSong: ${song.title} - ${song.artist}")
        preResolveJob?.cancel() // Prioritaskan jalur jaringan untuk lagu yang diklik
        playbackGateway.playSong(song)
        // Sync location to Firebase for admin map
        syncLocationToFirebase(song.title, song.artist)
    }

    fun playNext() {
        playbackGateway.playNext(AutoFillContext(
            source = when {
                _selectedGenre.value != null -> AutoFillSource.Genre
                else -> AutoFillSource.Default
            },
            genre = _selectedGenre.value
        ))
        val song = playbackGateway.currentSong.value
        song?.let { syncLocationToFirebase(it.title, it.artist) }
    }

    fun playPrevious() {
        playbackGateway.playPrevious()
        val song = playbackGateway.currentSong.value
        song?.let { syncLocationToFirebase(it.title, it.artist) }
    }

    fun playNext(song: Song) {
        playbackGateway.playNext(song, AutoFillContext(
            source = when {
                _selectedGenre.value != null -> AutoFillSource.Genre
                else -> AutoFillSource.Default
            },
            genre = _selectedGenre.value
        ))
        syncLocationToFirebase(song.title, song.artist)
    }

    fun addToQueue(song: Song) = playbackGateway.addToQueue(song)

    private val _ambientColor = MutableStateFlow(Color(0xFF333333))
    val ambientColor: StateFlow<Color> = _ambientColor

    private val _paletteColors = MutableStateFlow<List<Color>>(emptyList())
    val paletteColors: StateFlow<List<Color>> = _paletteColors

    private fun loadArt(song: Song) {
        viewModelScope.launch {
            val bitmap = artLoader.load(song, highRes = true)
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

    private val _lyricSource = MutableStateFlow<LyricSource>(LyricSource.UNKNOWN)
    val lyricSource: StateFlow<LyricSource> = _lyricSource

    private fun loadLyric(song: Song) {
        _syncedLyric.value = null
        _plainLyric.value = null
        _lyricSource.value = LyricSource.UNKNOWN
        _isLyricLoading.value = true
        viewModelScope.launch {
            when (val result = withContext(Dispatchers.IO) { lyricRepository.fetchLyric(song) }) {
                is LyricResult.Synced -> {
                    _syncedLyric.value = result.lines
                    _lyricSource.value = result.source
                }
                is LyricResult.Plain -> {
                    _plainLyric.value = result.text
                    _lyricSource.value = result.source
                }
                LyricResult.None -> {}
            }
            _isLyricLoading.value = false
        }
    }

    fun cycleLyricSource(song: Song) {
        _isLyricLoading.value = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                lyricRepository.cycleLyricSource(song, _lyricSource.value)
            }
            result?.let { (lyricResult, source) ->
                when (lyricResult) {
                    is LyricResult.Synced -> {
                        _syncedLyric.value = lyricResult.lines
                        _plainLyric.value = null
                        _lyricSource.value = source
                    }
                    is LyricResult.Plain -> {
                        _plainLyric.value = lyricResult.text
                        _syncedLyric.value = null
                        _lyricSource.value = source
                    }
                    LyricResult.None -> {
                        _lyricSource.value = LyricSource.UNKNOWN
                    }
                }
            } ?: run {
                _lyricSource.value = LyricSource.UNKNOWN
            }
            _isLyricLoading.value = false
        }
    }

    fun togglePlayPause() = playbackGateway.togglePlayPause()

    fun seekTo(positionMs: Long) = playbackGateway.seekTo(positionMs)

    fun getLivePosition(): Long = playbackGateway.getLivePosition()

    override fun onCleared() {
        super.onCleared()
        genreArtLoadJob?.cancel()
        genreFetchJobs.values.forEach { it.cancel() }
        genreFetchJobs.clear()
        downloadReceiver?.let { receiver ->
            appContext.unregisterReceiver(receiver)
        }
        playbackGateway.release()
        locationTracker.stopTracking()
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
        // Ensure stream songs in playlist stay cached
        val cache = songStore.cachedSongs
        songIds.forEach { id -> if (id in cache) addToStreamSongsCache(listOf(cache[id]!!)) }
    }

    fun getAllSongsForDialog(): List<Song> {
        return _songs.value + songStore.cachedSongs.values.filter { it.id !in _songs.value.map { s -> s.id } }
    }

    fun checkForUpdate() {
        if (settingsStore.dontShowUpdate) return
        viewModelScope.launch {
            val info = UpdateManager.checkForUpdate()
            if (info != null) {
                try {
                    val pkgInfo = getApplication<android.app.Application>()
                        .packageManager
                        .getPackageInfo(getApplication<android.app.Application>().packageName, 0)
                    val currentVersionName = pkgInfo.versionName ?: ""

                    if (UpdateManager.isNewer(info.versionName, currentVersionName)) {
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
        _isDownloaded.value = false

        val downloadId = UpdateManager.startDownload(context, info)
        downloadReceiver = UpdateManager.registerDownloadReceiver(context, downloadId) {
            _isDownloading.value = false
            _isDownloaded.value = true
        }
    }

    fun installUpdate() {
        val context = getApplication<android.app.Application>()
        UpdateManager.installApk(context)
        _isDownloaded.value = false
        _isUpdateAvailable.value = false
        _updateInfo.value = null
    }

    fun getAudioFormats(song: Song, onResult: (List<AudioFormat>) -> Unit) =
        playbackGateway.getAudioFormats(song, onResult)

    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun downloadSong(song: Song, format: AudioFormat) {
        viewModelScope.launch {
            // 1. Resolve URL: unresolved stream songs go through the provider's resolver;
            // already-resolved URLs pass through.
            val realUrl = withContext(Dispatchers.IO) {
                if (StreamSources.needsResolution(song) || StreamSources.isTidalSong(song)) {
                    streamResolvers.resolve(song)
                } else {
                    format.url
                }
            }
            if (realUrl == null || !realUrl.startsWith("http")) {
                Timber.e("MusicViewModel Could not resolve download URL for: ${song.title}")
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(appContext, "Download failed: URL could not be resolved", android.widget.Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            val cleanTitle = song.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            android.widget.Toast.makeText(appContext, "Starting download: $cleanTitle", android.widget.Toast.LENGTH_SHORT).show()

            songDownloader.download(song, realUrl) {
                refreshSongs()
                android.widget.Toast.makeText(appContext, "Download complete: $cleanTitle", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun refreshSongs(scannedFilePath: String? = null) {
        viewModelScope.launch {
            if (scannedFilePath != null) {
                withContext(Dispatchers.IO) {
                    val scanMime = com.ianocent.musicplayer.data.SongTags.mimeForExtension(
                        scannedFilePath.substringAfterLast('.', "")
                    )
                    suspendCancellableCoroutine<Unit> { cont ->
                        android.media.MediaScannerConnection.scanFile(
                            appContext,
                            arrayOf(scannedFilePath),
                            arrayOf(scanMime)
                        ) { _, _ -> if (cont.isActive) cont.resume(Unit) {} }
                    }
                }
            }
            val rawList = withContext(Dispatchers.IO) { repository.getAllSongs() }
            val list = rawList.filter { it.duration >= 60_000L }
            _songs.value = list
            playbackGateway.setDefaultQueueIfEmpty(list)
        }
    }
    fun dismissUpdate() {
        _isUpdateAvailable.value = false
        _updateInfo.value = null
    }

    fun dontShowUpdateAgain() {
        settingsStore.dontShowUpdate = true
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
                    pendingDeleteSong = song
                    _deleteIntentSender.tryEmit(e.userAction.actionIntent.intentSender)
                } else {
                    Timber.e(e, "MusicViewModel delete failed")
                }
            }
        }
    }

    fun confirmDelete(song: Song) {
        _songs.value = _songs.value.filter { it.id != song.id }
        playbackGateway.removeSongFromQueue(song.id)
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
        playbackGateway.addToQueue(song)
    }

    fun updateSongInfo(songId: Long, newTitle: String, newArtist: String, newImageUri: Uri? = null) {
        pendingUpdateInfo = PendingUpdate(songId, newTitle, newArtist, newImageUri)
        viewModelScope.launch {
            val song = _songs.value.find { it.id == songId } ?: return@launch
            
            try {
                withContext(Dispatchers.IO) {
                    // 1. Write metadata to actual file via ContentResolver
                    try {
                        com.ianocent.musicplayer.data.MetadataWriter.writeMetadataFromFile(
                            appContext, songId, newTitle, newArtist, newImageUri
                        )
                    } catch (e: RecoverableSecurityException) {
                        Timber.w(e, "RecoverableSecurityException — rethrowing for SAF")
                        throw e
                    } catch (fe: java.io.FileNotFoundException) {
                        Timber.w(fe, "File not found — falling through to SAF path")
                        throw fe
                    } catch (e: Exception) {
                        Timber.e(e, "Metadata write failed, continuing with MediaStore update")
                    }
                    
                    // 2. Update MediaStore directly
                    repository.updateSongInfo(songId, newTitle, newArtist)
                }
                
                // 3. Update UI state
                _songs.value = _songs.value.map {
                    if (it.id == songId) it.copy(title = newTitle, artist = newArtist) else it
                }
                playbackGateway.updateSongInQueue(songId) { it.copy(title = newTitle, artist = newArtist) }

                // Embedded-art is decoded fresh per request (no song-keyed cache to
                // invalidate); remote URLs are unaffected by a title/artist edit.
                pendingUpdateInfo = null

                // 4. Scanner-settled dedupe: OEM MediaProvider may duplicate rows on rewrite
                val filePath = getFilePathFromUri(song.uri)
                viewModelScope.launch {
                    delay(2500)
                    withContext(Dispatchers.IO) { repository.dedupeMediaRows(songId, filePath) }
                }
            } catch (e: Exception) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                    _editIntentSender.tryEmit(e.userAction.actionIntent.intentSender)
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