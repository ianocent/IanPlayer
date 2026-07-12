package com.ianocent.musicplayer.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.ianocent.musicplayer.data.LyricRepository
import com.ianocent.musicplayer.data.MusicRepository
import com.ianocent.musicplayer.data.Song
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
import com.ianocent.musicplayer.data.StreamRepository
import com.ianocent.musicplayer.data.SoundCloudRepository
import com.ianocent.musicplayer.UpdateManager
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val streamRepository = StreamRepository()
    private val ytMusicRepository = YTMusicRepository()
    private val soundCloudRepository = SoundCloudRepository()

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        if (throwable !is CancellationException) {
            throwable.printStackTrace()
        }
    }

    private val _allStreamSongs = MutableStateFlow<List<Song>>(emptyList())
    private val _streamSongs = MutableStateFlow<List<Song>>(emptyList())
    val streamSongs: StateFlow<List<Song>> = _streamSongs

    private val _isSearchingRemote = MutableStateFlow(false)
    val isSearchingRemote: StateFlow<Boolean> = _isSearchingRemote

    private var searchJob: Job? = null

    private val streamPageSize = 50

    private val _sortMode = MutableStateFlow(0)
    val sortMode: StateFlow<Int> = _sortMode

    private val _playCounts = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val playCounts: StateFlow<Map<Long, Int>> = _playCounts

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

                    com.ianocent.musicplayer.data.MonthlyRecap(
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
            val mutex = kotlinx.coroutines.sync.Mutex()

            try {
                coroutineScope {
                    // fun ini non-suspend, tapi punya akses ke `this` (CoroutineScope dari coroutineScope di atas)
                    // biar bisa launch coroutine baru tiap kali dipanggil dari callback non-suspend.
                    fun appendPartial(newSongs: List<Song>) {
                        launch {
                            mutex.withLock {
                                val merged = (_allStreamSongs.value + newSongs).distinctBy { it.id }
                                _allStreamSongs.value = merged
                                _streamSongs.value = merged.take(
                                    maxOf(_streamSongs.value.size + newSongs.size, streamPageSize)
                                )
                            }
                        }
                    }

                    val jobs = listOf(
                        launch { streamRepository.searchSongs(query) { appendPartial(it) } },
                        launch { ytMusicRepository.searchSongs(query) { appendPartial(it) } },
                        launch { soundCloudRepository.searchSongs(query) { appendPartial(it) } }
                    )
                    jobs.joinAll()
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
    private val _isDarkMode = MutableStateFlow(true)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode

    fun toggleDarkMode() {
        _isDarkMode.value = !_isDarkMode.value
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

    private val repository = MusicRepository(application)
    val playerManager = PlayerManager(application)

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs

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

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)

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

    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo

    private val _isUpdateAvailable = MutableStateFlow(false)
    val isUpdateAvailable: StateFlow<Boolean> = _isUpdateAvailable

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading

    private val _monthlyRecap = MutableStateFlow<com.ianocent.musicplayer.data.MonthlyRecap?>(null)
    val monthlyRecap: StateFlow<com.ianocent.musicplayer.data.MonthlyRecap?> = _monthlyRecap

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
                    com.ianocent.musicplayer.data.AlbumArtLoader.getEmbeddedArt(appContext, song.uri)
                }
            }
            artCache[song.id] = art
            onLoaded(art)
        }
    }

    fun getHighResArt(song: Song, onLoaded: (android.graphics.Bitmap?) -> Unit) {
        viewModelScope.launch {
            val art = withContext(Dispatchers.IO) {
                if (song.isStream && !song.remoteArtUrl.isNullOrEmpty()) {
                    try {
                        // High-res fix for YT Music & common CDN thumbnails
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
                    com.ianocent.musicplayer.data.AlbumArtLoader.getEmbeddedArt(appContext, song.uri, targetSize = 800)
                }
            }
            onLoaded(art)
        }
    }

    private val appContext = application.applicationContext
    private var pendingMediaId: Long? = null
    private var downloadReceiver: BroadcastReceiver? = null

    init {
        loadPlaylistsFromPrefs()
        _playCounts.value = loadPlayCounts()
        _sortMode.value = prefs.getInt("sort_mode", 0)
        checkForUpdate()
        playerManager.initialize {
            val player = playerManager.player

            player?.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                }
                override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                    _duration.value = playerManager.getDuration()
                }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    _isBuffering.value = playbackState == Player.STATE_BUFFERING
                    if (playbackState == Player.STATE_ENDED) {
                        playNext()
                    }
                    if (playbackState == Player.STATE_IDLE && player?.playerError != null) {
                        _isPlaying.value = false
                        try {
                            player?.prepare()
                            player?.play()
                        } catch (_: Exception) {
                            playNext()
                        }
                    }
                }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    error.printStackTrace()
                    _isPlaying.value = false
                    player?.stop()
                }
            })

            if (player?.currentMediaItem != null) {
                val mediaId = player.currentMediaItem?.mediaId?.toLongOrNull()
                if (mediaId != null) {
                    pendingMediaId = mediaId
                    tryRestoreCurrentSong()
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
                } catch (_: Exception) {}
                delay(500)
            }
        }
    }
    private fun tryRestoreCurrentSong() {
        val mediaId = pendingMediaId ?: return
        val activeSong = _songs.value.find { it.id == mediaId }
        if (activeSong != null) {
            _currentSong.value = activeSong
            _currentIndex.value = _queue.value.indexOf(activeSong)
            _isPlaying.value = playerManager.player?.isPlaying ?: false
            loadArt(activeSong)
            loadLyric(activeSong)
            pendingMediaId = null
        }
    }

    fun loadSongs() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { repository.getAllSongs() }
            originalOrder = list
            _songs.value = list
            _queue.value = list
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
            _queue.value = _queue.value.shuffled()
        } else {
            _queue.value = baseQueueBeforeShuffle.ifEmpty { _songs.value }
        }
        _currentIndex.value = _queue.value.indexOf(current)
    }
    fun setQueue(newQueue: List<Song>, startSong: Song? = null) {
        _queue.value = newQueue
        if (_isShuffleOn.value) {
            baseQueueBeforeShuffle = newQueue
            _queue.value = newQueue.shuffled()
        }
        val target = startSong ?: newQueue.firstOrNull()
        target?.let { playSong(it) }
    }

    fun toggleRepeat() {
        _repeatMode.value = playerManager.toggleRepeat()
    }


    fun playSong(song: Song) {
        viewModelScope.launch {
            val index = _queue.value.indexOf(song)
            _currentIndex.value = index
            _currentSong.value = song

            val resolvedSong = if (song.isStream && song.uri.toString().startsWith("ytmusic://placeholder/")) {
                _isBuffering.value = true
                withContext(Dispatchers.IO) {
                    val streamUrl = ytMusicRepository.resolveStreamUrl(song)
                    _isBuffering.value = false
                    if (streamUrl != null) {
                        song.copy(uri = Uri.parse(streamUrl))
                    } else {
                        song
                    }
                }
            } else {
                song
            }

            val queue = _queue.value.toMutableList()
            if (index >= 0 && index < queue.size) {
                queue[index] = resolvedSong
            }
            playerManager.playSong(resolvedSong, queue, maxOf(index, 0))
            incrementPlayCount(song.id)
            loadArt(song)
            loadLyric(song)
        }
    }

    fun playNext() {
        viewModelScope.launch {
            val list = _queue.value
            if (list.isEmpty()) return@launch
            val isLast = _currentIndex.value >= list.size - 1

            val nextIndex = when {
                _repeatMode.value == Player.REPEAT_MODE_ONE -> _currentIndex.value
                isLast && _repeatMode.value == Player.REPEAT_MODE_ALL -> 0
                isLast -> return@launch
                else -> _currentIndex.value + 1
            }
            _currentIndex.value = nextIndex
            val song = list[nextIndex]
            _currentSong.value = song

            val resolvedSong = if (song.isStream && song.uri.toString().startsWith("ytmusic://placeholder/")) {
                _isBuffering.value = true
                withContext(Dispatchers.IO) {
                    val streamUrl = ytMusicRepository.resolveStreamUrl(song)
                    _isBuffering.value = false
                    if (streamUrl != null) {
                        song.copy(uri = Uri.parse(streamUrl))
                    } else {
                        song
                    }
                }
            } else {
                song
            }

            val queue = _queue.value.toMutableList()
            if (nextIndex >= 0 && nextIndex < queue.size) {
                queue[nextIndex] = resolvedSong
            }
            playerManager.playSong(resolvedSong, queue, nextIndex)
            loadArt(song)
            loadLyric(song)
        }
    }

    fun playPrevious() {
        viewModelScope.launch {
            val list = _queue.value
            if (list.isEmpty()) return@launch
            val prevIndex = (_currentIndex.value - 1).coerceAtLeast(0)
            _currentIndex.value = prevIndex
            val song = list[prevIndex]
            _currentSong.value = song

            val resolvedSong = if (song.isStream && song.uri.toString().startsWith("ytmusic://placeholder/")) {
                _isBuffering.value = true
                withContext(Dispatchers.IO) {
                    val streamUrl = ytMusicRepository.resolveStreamUrl(song)
                    _isBuffering.value = false
                    if (streamUrl != null) {
                        song.copy(uri = Uri.parse(streamUrl))
                    } else {
                        song
                    }
                }
            } else {
                song
            }

            val queue = _queue.value.toMutableList()
            if (prevIndex >= 0 && prevIndex < queue.size) {
                queue[prevIndex] = resolvedSong
            }
            playerManager.playSong(resolvedSong, queue, prevIndex)
            loadArt(song)
            loadLyric(song)
        }
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
                    com.ianocent.musicplayer.data.AlbumArtLoader.getEmbeddedArt(appContext, song.uri, targetSize = 400)
                }
            }
            _albumArt.value = bitmap
            _ambientColor.value = bitmap?.let {
                com.ianocent.musicplayer.data.AlbumArtLoader.extractDominantColor(it)
            } ?: Color(0xFF333333)
            _paletteColors.value = bitmap?.let {
                com.ianocent.musicplayer.data.AlbumArtLoader.extractPaletteColors(it)
            } ?: emptyList()
        }
    }
    private val _syncedLyric = MutableStateFlow<List<com.ianocent.musicplayer.data.LyricLine>?>(null)
    val syncedLyric: StateFlow<List<com.ianocent.musicplayer.data.LyricLine>?> = _syncedLyric

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
        playerManager.togglePlayPause()
    }

    fun seekTo(positionMs: Long) {
        playerManager.seekTo(positionMs)
        _currentPosition.value = positionMs
    }

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

    fun getAudioFormats(song: Song, onResult: (List<com.ianocent.musicplayer.data.AudioFormat>) -> Unit) {
        val remoteId = song.remoteId ?: run {
            onResult(listOf(com.ianocent.musicplayer.data.AudioFormat(
                url = song.uri.toString(),
                mimeType = "audio/mpeg",
                bitrate = 0,
                qualityLabel = "Standard"
            )))
            return
        }
        viewModelScope.launch {
            var formats = if (remoteId.length == 11 || (!song.uri.toString().contains("saavn") && !song.uri.toString().contains("soundcloud"))) {
                ytMusicRepository.getAudioFormats(remoteId)
            } else emptyList()
            
            if (formats.isEmpty()) {
                val resolvedUrl = if (song.uri.toString().startsWith("ytmusic://placeholder/")) {
                    withContext(Dispatchers.IO) { ytMusicRepository.resolveStreamUrl(song) }
                } else song.uri.toString()

                onResult(listOf(com.ianocent.musicplayer.data.AudioFormat(
                    url = resolvedUrl ?: song.uri.toString(),
                    mimeType = "audio/mpeg",
                    bitrate = 0,
                    qualityLabel = "Standard"
                )))
            } else {
                onResult(formats.sortedByDescending { it.bitrate })
            }
        }
    }

    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun downloadSong(song: Song, format: com.ianocent.musicplayer.data.AudioFormat) {
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
                Log.e("MusicViewModel", "Could not resolve download URL for: ${song.title}")
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
            val list = withContext(Dispatchers.IO) { repository.getAllSongs() }
            originalOrder = list
            _songs.value = list
            _queue.value = list
        }
    }

    fun dismissUpdate() {
        _isUpdateAvailable.value = false
        _updateInfo.value = null
    }

    fun deleteSong(song: Song) {
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                repository.deleteSong(song)
            }
            if (success) {
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
            } else {
                // If it failed, it stays in the list, avoiding the "relog" surprise
                // In a real app, we'd show a Toast or handle Scoped Storage permissions here
            }
        }
    }

    fun updateSongInfo(songId: Long, newTitle: String, newArtist: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.updateSongInfo(songId, newTitle, newArtist)
            }
            _songs.value = _songs.value.map {
                if (it.id == songId) it.copy(title = newTitle, artist = newArtist) else it
            }
            _queue.value = _queue.value.map {
                if (it.id == songId) it.copy(title = newTitle, artist = newArtist) else it
            }
            if (_currentSong.value?.id == songId) {
                _currentSong.value = _currentSong.value?.copy(title = newTitle, artist = newArtist)
            }
        }
    }
}