package com.ianocent.musicplayer.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.ianocent.musicplayer.data.LyricRepository
import com.ianocent.musicplayer.data.MusicRepository
import com.ianocent.musicplayer.data.Song
import com.ianocent.musicplayer.player.PlayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Color
import com.ianocent.musicplayer.data.Playlist

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists

    fun createPlaylist(name: String, songIds: List<Long>) {
        val newPlaylist = Playlist(
            id = System.currentTimeMillis(),
            name = name,
            songIds = songIds.toMutableList()
        )
        _playlists.value = _playlists.value + newPlaylist
    }

    fun getSongsInPlaylist(playlist: Playlist): List<Song> {
        return _songs.value.filter { it.id in playlist.songIds }
    }

    fun deletePlaylist(playlist: Playlist) {
        _playlists.value = _playlists.value.filter { it.id != playlist.id }
    }
    private val _isDarkMode = MutableStateFlow(true)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode

    fun toggleDarkMode() {
        _isDarkMode.value = !_isDarkMode.value
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

    private val _isShuffleOn = MutableStateFlow(false)

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

    private val artCache = mutableMapOf<Long, android.graphics.Bitmap?>()

    fun getCachedArt(song: Song, onLoaded: (android.graphics.Bitmap?) -> Unit) {
        if (artCache.containsKey(song.id)) {
            onLoaded(artCache[song.id])
            return
        }
        viewModelScope.launch {
            val art = withContext(Dispatchers.IO) {
                com.ianocent.musicplayer.data.AlbumArtLoader.getEmbeddedArt(appContext, song.uri)
            }
            artCache[song.id] = art
            onLoaded(art)
        }
    }

    private val appContext = application.applicationContext
    private var pendingMediaId: Long? = null

    init {
        playerManager.initialize {
            val player = playerManager.player

            player?.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                }
                override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                    _duration.value = playerManager.getDuration()
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

        viewModelScope.launch {
            while (isActive) {
                _currentPosition.value = playerManager.getCurrentPosition()
                _duration.value = playerManager.getDuration()
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
        }
    }
    fun toggleShuffle() {
        _isShuffleOn.value = !_isShuffleOn.value
        val current = _currentSong.value
        _queue.value = if (_isShuffleOn.value) {
            _songs.value.shuffled()
        } else {
            _songs.value
        }
        _currentIndex.value = _queue.value.indexOf(current)
    }

    fun toggleRepeat() {
        _repeatMode.value = playerManager.toggleRepeat()
    }


    fun playSong(song: Song) {
        val index = _queue.value.indexOf(song)
        _currentIndex.value = index
        _currentSong.value = song
        playerManager.playSong(song)
        loadArt(song)
        loadLyric(song)
    }

    fun playNext() {
        val list = _queue.value
        if (list.isEmpty()) return
        val isLast = _currentIndex.value >= list.size - 1

        val nextIndex = when {
            _repeatMode.value == Player.REPEAT_MODE_ONE -> _currentIndex.value
            isLast && _repeatMode.value == Player.REPEAT_MODE_ALL -> 0
            isLast -> return
            else -> _currentIndex.value + 1
        }
        _currentIndex.value = nextIndex
        _currentSong.value = list[nextIndex]
        playerManager.playSong(list[nextIndex])
        loadArt(list[nextIndex])
        loadLyric(list[nextIndex])
    }

    fun playPrevious() {
        val list = _queue.value
        if (list.isEmpty()) return
        val prevIndex = (_currentIndex.value - 1).coerceAtLeast(0)
        _currentIndex.value = prevIndex
        _currentSong.value = list[prevIndex]
        playerManager.playSong(list[prevIndex])
        loadArt(list[prevIndex])
        loadLyric(list[prevIndex])
    }

    private val _ambientColor = MutableStateFlow(Color(0xFF333333))
    val ambientColor: StateFlow<Color> = _ambientColor

    private fun loadArt(song: Song) {
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                com.ianocent.musicplayer.data.AlbumArtLoader.getEmbeddedArt(appContext, song.uri, targetSize = 400)
            }
            _albumArt.value = bitmap
            _ambientColor.value = bitmap?.let {
                com.ianocent.musicplayer.data.AlbumArtLoader.extractDominantColor(it)
            } ?: Color(0xFF333333)
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
        playerManager.release()
    }
}