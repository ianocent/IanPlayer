package com.ianocent.musicplayer.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
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

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository(application)
    val playerManager = PlayerManager(application)

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs

    private val _currentIndex = MutableStateFlow(-1)

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration

    init {
        playerManager.exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                _duration.value = playerManager.getDuration()
            }
        })

        // Poll posisi tiap 500ms buat update progress bar
        viewModelScope.launch {
            while (isActive) {
                _currentPosition.value = playerManager.getCurrentPosition()
                _duration.value = playerManager.getDuration()
                delay(500)
            }
        }
    }

    fun loadSongs() {
        viewModelScope.launch {
            _songs.value = withContext(Dispatchers.IO) {
                repository.getAllSongs()
            }
        }
    }

    fun playSong(song: Song) {
        val index = _songs.value.indexOf(song)
        _currentIndex.value = index
        _currentSong.value = song
        playerManager.playSong(song)
    }

    fun playNext() {
        val list = _songs.value
        if (list.isEmpty()) return
        val nextIndex = (_currentIndex.value + 1).coerceAtMost(list.size - 1)
        _currentIndex.value = nextIndex
        _currentSong.value = list[nextIndex]
        playerManager.playSong(list[nextIndex])
    }

    fun playPrevious() {
        val list = _songs.value
        if (list.isEmpty()) return
        val prevIndex = (_currentIndex.value - 1).coerceAtLeast(0)
        _currentIndex.value = prevIndex
        _currentSong.value = list[prevIndex]
        playerManager.playSong(list[prevIndex])
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