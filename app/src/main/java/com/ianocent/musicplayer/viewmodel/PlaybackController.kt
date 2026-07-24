package com.ianocent.musicplayer.viewmodel

import android.content.SharedPreferences
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.ianocent.musicplayer.data.AudioFormat
import com.ianocent.musicplayer.data.Song
import com.ianocent.musicplayer.data.YTMusicRepository
import com.ianocent.musicplayer.player.PlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class PlaybackController(
    val playerManager: PlayerManager,
    private val prefs: SharedPreferences,
    private val viewModelScope: CoroutineScope,
    val ytMusicRepository: YTMusicRepository,
    private val getSongs: () -> List<Song>,
    private val getAllStreamSongs: () -> List<Song>,
    private val onSongPlayed: (Song) -> Unit,
    private val onBufferingChange: (Boolean) -> Unit,
    private val onIsPlayingChange: (Boolean) -> Unit,
) {
    // == Playback State ==
    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue

    val currentSong: StateFlow<Song?> get() = _currentSong
    private val _currentSong = MutableStateFlow<Song?>(null)

    val isPlaying: StateFlow<Boolean> get() = _isPlaying
    private val _isPlaying = MutableStateFlow(false)

    val currentPosition: StateFlow<Long> get() = _currentPosition
    private val _currentPosition = MutableStateFlow(0L)

    val duration: StateFlow<Long> get() = _duration
    private val _duration = MutableStateFlow(0L)

    val isShuffleOn: StateFlow<Boolean> get() = _isShuffleOn
    private val _isShuffleOn = MutableStateFlow(prefs.getBoolean("is_shuffle_on", false))

    val repeatMode: StateFlow<Int> get() = _repeatMode
    private val _repeatMode = MutableStateFlow(prefs.getInt("repeat_mode", Player.REPEAT_MODE_OFF))

    val isBuffering: StateFlow<Boolean> get() = _isBuffering
    private val _isBuffering = MutableStateFlow(false)

    val audioSessionId: StateFlow<Int> get() = _audioSessionId
    private val _audioSessionId = MutableStateFlow(0)

    var currentIndex: Int = -1
        private set

    fun setQueueState(queue: List<Song>, index: Int, song: Song?) {
        _queue.value = queue
        currentIndex = index
        if (song != null) _currentSong.value = song
    }

    fun setAudioSessionId(id: Int) {
        if (id != 0) _audioSessionId.value = id
    }

    private var baseQueueBeforeShuffle: List<Song> = emptyList()
    var pendingMediaId: Long? = null
    val prefetchingIds = mutableSetOf<Long>()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            onIsPlayingChange(isPlaying)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            _duration.value = playerManager.getDuration()
            syncStateFromPlayer()

            val currentItem = playerManager.player?.currentMediaItem
            if (currentItem != null && currentItem.localConfiguration?.uri?.toString()?.startsWith("ytmusic://placeholder/") == true) {
                _currentSong.value?.let { resolveAndPlayStream(it) }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val isBuf = playbackState == Player.STATE_BUFFERING
            _isBuffering.value = isBuf
            onBufferingChange(isBuf)
            if (playbackState == Player.STATE_ENDED) {
                if (playerManager.player?.repeatMode != Player.REPEAT_MODE_ONE) {
                    playNext()
                }
            }
            if (playbackState == Player.STATE_IDLE && playerManager.player?.playerError != null) {
                _isPlaying.value = false

                val currentUri = playerManager.player?.currentMediaItem?.localConfiguration?.uri?.toString()
                if (currentUri?.startsWith("ytmusic://placeholder/") == true) return

                try {
                    playerManager.player?.prepare()
                    playerManager.player?.play()
                } catch (e: Exception) {
                    Timber.w(e, "PlaybackController player retry failed, skip to next")
                    playNext()
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            error.printStackTrace()
            _isPlaying.value = false

            val currentUri = playerManager.player?.currentMediaItem?.localConfiguration?.uri?.toString()
            if (currentUri?.startsWith("ytmusic://placeholder/") == true) return

            playNext()
        }
    }

    fun attachListener() {
        playerManager.player?.addListener(playerListener)
    }

    fun startPositionPolling() {
        viewModelScope.launch {
            while (isActive) {
                try {
                    _currentPosition.value = playerManager.getCurrentPosition()
                    _duration.value = playerManager.getDuration()

                    if (_audioSessionId.value == 0 || _audioSessionId.value != playerManager.getAudioSessionId()) {
                        val sid = playerManager.getAudioSessionId()
                        if (sid != 0) _audioSessionId.value = sid
                    }

                    val remaining = _duration.value - _currentPosition.value
                    if (_duration.value > 0 && remaining in 0..8000) {
                        prefetchNextIfNeeded()
                    }
                } catch (e: Exception) {
                    Timber.w(e, "PlaybackController position polling error")
                }
                delay(100)
            }
        }
    }

    fun tryRestoreCurrentSong() {
        if (playerManager.player == null) return
        try {
            tryRestoreCurrentSongInternal()
        } catch (e: Exception) {
            Timber.w(e, "PlaybackController tryRestoreCurrentSong failed")
        }
    }

    fun tryRestoreCurrentSongOnSongsChanged() {
        val mediaId = pendingMediaId ?: return
        var activeSong = getSongs().find { it.id == mediaId }
        if (activeSong == null) {
            activeSong = getAllStreamSongs().find { it.id == mediaId }
        }
        if (activeSong == null) {
            activeSong = _queue.value.find { it.id == mediaId }
        }
        if (activeSong == null) {
            val p = playerManager.player ?: return
            val item = p.currentMediaItem ?: return
            val meta = item.mediaMetadata
            val songUri = item.localConfiguration?.uri ?: Uri.EMPTY
            activeSong = Song(
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
            currentIndex = _queue.value.indexOfFirst { it.id == activeSong.id }.coerceAtLeast(0)
            _isPlaying.value = playerManager.player?.isPlaying ?: false
            pendingMediaId = null
        }
    }

    private fun tryRestoreCurrentSongInternal() {
        val mediaId = pendingMediaId ?: return
        var activeSong = getSongs().find { it.id == mediaId }
        if (activeSong == null) {
            activeSong = getAllStreamSongs().find { it.id == mediaId }
        }
        if (activeSong == null) {
            activeSong = _queue.value.find { it.id == mediaId }
        }
        if (activeSong == null) {
            val p = playerManager.player ?: return
            val item = p.currentMediaItem ?: return
            val meta = item.mediaMetadata
            val songUri = item.localConfiguration?.uri ?: Uri.EMPTY
            activeSong = Song(
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
            currentIndex = _queue.value.indexOfFirst { it.id == activeSong.id }.coerceAtLeast(0)
            _isPlaying.value = playerManager.player?.isPlaying ?: false
            onSongPlayed(activeSong)
            pendingMediaId = null
        }
    }

    private fun syncStateFromPlayer() {
        try {
            val p = playerManager.player ?: return
            val item = p.currentMediaItem ?: return
            val mediaId = item.mediaId.toLongOrNull() ?: return
            if (_currentSong.value?.id == mediaId) return

            var song = _queue.value.find { it.id == mediaId }
                ?: getSongs().find { it.id == mediaId }
                ?: getAllStreamSongs().find { it.id == mediaId }

            if (song == null) {
                val meta = item.mediaMetadata
                val songUri = item.localConfiguration?.uri ?: Uri.EMPTY
                song = Song(
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
            currentIndex = _queue.value.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
            _audioSessionId.value = playerManager.getAudioSessionId()
            onSongPlayed(song)
        } catch (e: Exception) {
            Timber.w(e, "PlaybackController syncStateFromPlayer failed")
        }
    }

    // == Queue Management ==

    fun toggleShuffle() {
        _isShuffleOn.value = !_isShuffleOn.value
        prefs.edit().putBoolean("is_shuffle_on", _isShuffleOn.value).apply()
        val current = _currentSong.value
        if (_isShuffleOn.value) {
            baseQueueBeforeShuffle = _queue.value
            val currentSong = current
            val others = _queue.value.filter { it.id != currentSong?.id }.shuffled()
            _queue.value = if (currentSong != null) listOf(currentSong) + others else others
        } else {
            _queue.value = baseQueueBeforeShuffle.ifEmpty { getSongs() }
        }
        currentIndex = _queue.value.indexOfFirst { it.id == current?.id }.coerceAtLeast(0)
        savePlayerState()
    }

    fun setQueue(newQueue: List<Song>, startSong: Song? = null) {
        val currentSong = _currentSong.value

        val compareBase = if (_isShuffleOn.value && baseQueueBeforeShuffle.isNotEmpty()) baseQueueBeforeShuffle else _queue.value
        val isSameContent = newQueue.size == compareBase.size &&
            newQueue.zip(compareBase).all { (a, b) -> a.id == b.id }

        if (_isShuffleOn.value) {
            if (!isSameContent) {
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

    // == Playback Control ==

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
        val index = snapshot.indexOfFirst { it.id == song.id }
        if (index == -1) return

        viewModelScope.launch {
            if (playerManager.player?.isPlaying == true) {
                fadeVolume(1f, 0f, 150)
            } else {
                playerManager.player?.volume = 0f
            }

            currentIndex = index
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

            fadeVolume(0f, 1f, 150)

            onSongPlayed(song)
            savePlayerState()
        }
    }

    private fun resolveAndPlayStream(song: Song) {
        val savedIndex = _queue.value.indexOfFirst { it.id == song.id }
        viewModelScope.launch {
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
            val useIndex = if (savedIndex >= 0) savedIndex else _queue.value.indexOfFirst { it.id == resolvedSong.id }.coerceAtLeast(0)
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

            fadeVolume(0f, 1f, 150)
        }
    }

    fun playNext() {
        val list = _queue.value
        if (list.isEmpty()) return
        val isLast = currentIndex >= list.size - 1

        val nextIndex = when {
            isLast && _repeatMode.value == Player.REPEAT_MODE_ALL -> 0
            isLast -> return
            else -> currentIndex + 1
        }
        playSong(list[nextIndex])
    }

    fun playPrevious() {
        val list = _queue.value
        if (list.isEmpty()) return
        val isFirst = currentIndex <= 0

        if (_currentPosition.value > 3000) {
            seekTo(0)
            return
        }

        val prevIndex = when {
            isFirst && _repeatMode.value == Player.REPEAT_MODE_ALL -> list.size - 1
            isFirst -> 0
            else -> currentIndex - 1
        }
        playSong(list[prevIndex])
    }

    fun playNext(song: Song) {
        val list = _queue.value.toMutableList()
        val curIdx = currentIndex
        if (curIdx < 0) return

        val insertIdx = curIdx + 1
        list.add(insertIdx.coerceAtMost(list.size), song)
        _queue.value = list

        playerManager.insertAfterCurrent(song, curIdx)
        playSong(song)
    }

    fun addToQueue(song: Song) {
        val list = _queue.value.toMutableList()
        list.add(song)
        _queue.value = list

        playerManager.addToQueue(song)
        savePlayerState()
    }

    fun togglePlayPause() {
        viewModelScope.launch {
            val player = playerManager.player ?: return@launch
            if (player.isPlaying) {
                fadeVolume(1f, 0f, 150)
                playerManager.togglePlayPause()
                player.volume = 1f
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

    fun reorderUpNext(fromIndexInUpNext: Int, toIndexInUpNext: Int) {
        val cur = _currentSong.value ?: return
        val list = _queue.value
        val curIdx = list.indexOfFirst { it.id == cur.id }
        if (curIdx == -1) return

        val actualFrom = curIdx + 1 + fromIndexInUpNext
        val actualTo = curIdx + 1 + toIndexInUpNext
        if (actualFrom !in list.indices || actualTo !in list.indices) return

        val newQueue = list.toMutableList()
        val movedItem = newQueue.removeAt(actualFrom)
        newQueue.add(actualTo, movedItem)
        _queue.value = newQueue
        currentIndex = newQueue.indexOfFirst { it.id == cur.id }.coerceAtLeast(0)

        playerManager.moveQueuedItem(actualFrom, actualTo)
        savePlayerState()
    }

    // == Persistence ==

    fun savePlayerState() {
        val current = _currentSong.value ?: return
        val arr = org.json.JSONArray()
        _queue.value.forEach { s ->
            val obj = org.json.JSONObject()
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
            .putInt("last_index", currentIndex)
            .apply()
    }

    fun restorePlayerState() {
        if (_queue.value.isNotEmpty()) return
        val queueJson = prefs.getString("last_queue", null) ?: return
        val savedSongId = prefs.getLong("last_song_id", -1L)
        val savedIndex = prefs.getInt("last_index", -1)
        if (savedSongId < 0 || savedIndex < 0) return
        try {
            val arr = org.json.JSONArray(queueJson)
            val restoredQueue = mutableListOf<Song>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                restoredQueue.add(
                    Song(
                        id = obj.getLong("id"),
                        title = obj.getString("title"),
                        artist = obj.getString("artist"),
                        duration = obj.optLong("duration", 0L),
                        uri = Uri.parse(obj.getString("uri")),
                        album = obj.optString("album", "Unknown Album"),
                        isStream = obj.optBoolean("isStream", false),
                        remoteArtUrl = if (obj.has("remoteArtUrl")) obj.getString("remoteArtUrl") else null,
                        remoteId = if (obj.has("remoteId")) obj.getString("remoteId") else null
                    )
                )
            }
            if (restoredQueue.isNotEmpty()) {
                _queue.value = restoredQueue
                currentIndex = savedIndex.coerceIn(0, restoredQueue.size - 1)
                pendingMediaId = savedSongId
            }
        } catch (_: Exception) {}
    }

    private fun prefetchNextIfNeeded() {
        val list = _queue.value
        val idx = currentIndex
        if (idx < 0 || idx >= list.size - 1) return

        val next = list[idx + 1]
        if (!next.isStream || !next.uri.toString().startsWith("ytmusic://placeholder/")) return
        if (!prefetchingIds.add(next.id)) return

        viewModelScope.launch {
            val resolvedUrl = withContext(Dispatchers.IO) {
                try { ytMusicRepository.resolveStreamUrl(next) } catch (e: Exception) { Timber.w(e, "PlaybackController prefetchNext failed"); null }
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

    // == ViewModel cross-cutting helpers ==

    fun removeSongFromQueue(songId: Long) {
        _queue.value = _queue.value.filter { it.id != songId }
        if (_currentSong.value?.id == songId) {
            _currentSong.value = null
            playerManager.player?.stop()
        }
    }

    fun updateSongInQueue(songId: Long, updater: (Song) -> Song) {
        _queue.value = _queue.value.map { if (it.id == songId) updater(it) else it }
        if (_currentSong.value?.id == songId) {
            _currentSong.value = _currentSong.value?.let(updater)
        }
    }

    fun setDefaultQueueIfEmpty(songs: List<Song>) {
        if (_currentSong.value == null) {
            _queue.value = songs
        }
    }

    fun release() {
        playerManager.release()
    }
}
