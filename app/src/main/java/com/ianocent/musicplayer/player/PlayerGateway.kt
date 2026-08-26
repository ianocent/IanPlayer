package com.ianocent.musicplayer.player

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.ianocent.musicplayer.data.AudioFormat
import com.ianocent.musicplayer.data.Song
import com.ianocent.musicplayer.data.SongStore
import com.ianocent.musicplayer.data.StreamResolvers
import com.ianocent.musicplayer.data.StreamSearchResult
import com.ianocent.musicplayer.data.StreamSources
import com.ianocent.musicplayer.data.YTMusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * The deep playback module.
 *
 * Owns the MediaController connection, queue truth, stream resolution handoff,
 * volume fades, repeat mode, restore, prefetch, and the audio session id.
 * Callers see StateFlows plus intent-level verbs; nobody outside this module
 * touches a raw Player instance.
 *
 * External controllers (voice assistant, Bluetooth/Wear) may send transport
 * commands to the session directly. That is legitimate: they are adapters at
 * the session seam, and this module stays the single source of truth by being
 * purely reactive to session events — every transition re-syncs state from the
 * live item and persists it.
 */
class PlayerGateway(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val songStore: SongStore,
    private val scope: CoroutineScope,
    private val ytMusicRepository: YTMusicRepository,
    private val streamResolvers: StreamResolvers,
    private val getSongs: () -> List<Song>,
    private val getAllStreamSongs: () -> List<Song>,
    private val onSongPlayed: (Song) -> Unit,
    private val onBufferingChange: (Boolean) -> Unit,
    private val onIsPlayingChange: (Boolean) -> Unit,
) {
    // == Player connection ==

    private var player: Player? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    // == Observable state ==

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
    private val _isShuffleOn = MutableStateFlow(prefs.getBoolean(PREF_IS_SHUFFLE_ON, false))

    val repeatMode: StateFlow<Int> get() = _repeatMode
    private val _repeatMode = MutableStateFlow(prefs.getInt(PREF_REPEAT_MODE, Player.REPEAT_MODE_OFF))

    val isBuffering: StateFlow<Boolean> get() = _isBuffering
    private val _isBuffering = MutableStateFlow(false)

    val audioSessionId: StateFlow<Int> get() = _audioSessionId
    private val _audioSessionId = MutableStateFlow(0)

    var currentIndex: Int = -1
        private set

    // == Internal machinery ==

    private var baseQueueBeforeShuffle: List<Song> = emptyList()
    private var pendingMediaId: Long? = null
    private val prefetchingIds = mutableSetOf<Long>()
    private val playedSongIds = mutableSetOf<Long>()
    private var autoFillJob: Job? = null
    private var playbackJob: Job? = null
    private var continuousFailures = 0

    private fun isPlaceholderUri(uriString: String?): Boolean =
        uriString?.startsWith(StreamSources.PLACEHOLDER_PREFIX) == true

    // == Player event handling ==

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            onIsPlayingChange(isPlaying)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            _duration.value = getDuration()
            syncStateFromPlayer()
            // Persist on every auto-advance so prefs never go stale; otherwise a
            // warm reconnect (activity recreated while the service kept playing)
            // restores an outdated "current song".
            savePlayerState()

            val currentItem = player?.currentMediaItem
            if (currentItem != null && isPlaceholderUri(currentItem.localConfiguration?.uri?.toString())) {
                _currentSong.value?.let { resolveAndStart(it) }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val isBuf = playbackState == Player.STATE_BUFFERING
            _isBuffering.value = isBuf
            onBufferingChange(isBuf)
            if (playbackState == Player.STATE_ENDED) {
                if (player?.repeatMode != Player.REPEAT_MODE_ONE) {
                    playNext()
                }
            }
            if (playbackState == Player.STATE_IDLE && player?.playerError != null) {
                _isPlaying.value = false

                val currentUri = player?.currentMediaItem?.localConfiguration?.uri?.toString()
                if (isPlaceholderUri(currentUri)) return

                try {
                    player?.prepare()
                    player?.play()
                } catch (e: Exception) {
                    Timber.w(e, "PlayerGateway player retry failed, skip to next")
                    playNext()
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            error.printStackTrace()
            _isPlaying.value = false

            val currentUri = player?.currentMediaItem?.localConfiguration?.uri?.toString()
            if (isPlaceholderUri(currentUri)) return

            playNext()
        }
    }

    // == Lifecycle ==

    /**
     * Connects to the [PlaybackService]. [onReady] receives whether a song was
     * restored from the live session and whether it is currently playing.
     */
    fun initialize(onReady: (hasRestoredSong: Boolean, isPlaying: Boolean) -> Unit) {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener(
            {
                player = controllerFuture?.get()
                player?.addListener(playerListener)
                val restored = restoreCurrentFromPlayer(fireOnSongPlayed = true)
                onReady(restored, player?.isPlaying ?: false)
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    fun release() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        player = null
    }

    fun startPositionPolling() {
        scope.launch {
            while (isActive) {
                try {
                    _currentPosition.value = getCurrentPosition()
                    _duration.value = getDuration()

                    val sid = readAudioSessionId()
                    if (sid != 0 && sid != _audioSessionId.value) _audioSessionId.value = sid

                    // Prefetch only matters while actually approaching the end of a
                    // playing track; skip the network work when paused/idle.
                    val playing = player?.isPlaying == true
                    val remaining = _duration.value - _currentPosition.value
                    if (playing && _duration.value > 0 && remaining in 0..8000) {
                        prefetchNextIfNeeded()
                    }

                    // 100ms while playing (smooth progress), relaxed when idle —
                    // position doesn't move anyway.
                    delay(if (playing) 100 else 500)
                } catch (e: Exception) {
                    Timber.w(e, "PlayerGateway position polling error")
                }
            }
        }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun readAudioSessionId(): Int {
        val p = player
        return if (p is ExoPlayer) p.audioSessionId else PlaybackService.audioSessionId
    }

    // == Restore ==
    //
    // One algorithm, three entry points: saved-queue restore at startup,
    // live-session restore after connect, and re-resolution when the local
    // library finishes loading.

    fun restorePlayerState() {
        if (_queue.value.isNotEmpty()) return
        val restored = SongStore.restoreQueueState(prefs) ?: return
        _queue.value = restored.queue
        currentIndex = restored.index
        // The live session outranks persisted state. Only queue a pending id
        // when nothing is actually playing (a true cold start); otherwise the
        // stale persisted id would race with (and possibly clobber) reality.
        if (player?.currentMediaItem == null) {
            pendingMediaId = restored.currentSongId
        }
    }

    /** Re-resolves the current song once the local library or caches are available. */
    fun tryRestoreCurrentSong() {
        if (player == null) return
        try {
            applyPendingMediaId(fireOnSongPlayed = true)
        } catch (e: Exception) {
            Timber.w(e, "PlayerGateway tryRestoreCurrentSong failed")
        }
    }

    fun tryRestoreCurrentSongOnSongsChanged() {
        applyPendingMediaId(fireOnSongPlayed = false)
    }

    /** Resolves [mediaId] against local library, stream search results, then the queue. */
    private fun findSongByAnyId(mediaId: Long): Song? =
        getSongs().find { it.id == mediaId }
            ?: getAllStreamSongs().find { it.id == mediaId }
            ?: _queue.value.find { it.id == mediaId }

    /** Builds a best-effort Song from the player's current MediaItem metadata. */
    private fun songFromMediaItem(item: MediaItem, fallbackId: Long): Song? {
        val p = player ?: return null
        val meta = item.mediaMetadata
        val songUri = item.localConfiguration?.uri ?: Uri.EMPTY
        val isStream = songUri.scheme == "http" || songUri.scheme == "https"
        return Song(
            id = item.mediaId.toLongOrNull() ?: fallbackId,
            title = meta.title?.toString() ?: "Unknown",
            artist = meta.artist?.toString() ?: "Unknown Artist",
            duration = p.duration,
            uri = songUri,
            album = meta.albumTitle?.toString() ?: "Unknown Album",
            isStream = isStream,
            remoteArtUrl = meta.artworkUri?.toString(),
            remoteId = if (isStream) item.mediaId else null
        )
    }

    /**
     * Consumes [pendingMediaId], resolving the active song and publishing it.
     * Returns true when a current song was established.
     *
     * The live session is the single authority on what is playing: a pending id
     * loaded from persisted state can be stale (tracks auto-advanced while the
     * activity was away), so it must never clobber a live item.
     */
    private fun applyPendingMediaId(fireOnSongPlayed: Boolean): Boolean {
        val mediaId = pendingMediaId ?: return false

        player?.currentMediaItem?.let { item ->
            resolveSongFromItem(item)?.let { liveSong ->
                publishCurrentSong(liveSong)
                pendingMediaId = null
                return true
            }
        }

        var activeSong = findSongByAnyId(mediaId)
        if (activeSong == null) {
            val item = player?.currentMediaItem ?: return false
            activeSong = songFromMediaItem(item, mediaId) ?: return false
        }
        publishCurrentSong(activeSong)
        if (fireOnSongPlayed) onSongPlayed(activeSong)
        pendingMediaId = null
        return true
    }

    /**
     * Live-session variant of restore. Always consults the player first; an
     * already-published song only short-circuits when it actually matches the
     * live media id, never merely because one exists.
     */
    private fun restoreCurrentFromPlayer(fireOnSongPlayed: Boolean): Boolean {
        val item = player?.currentMediaItem ?: return false
        val mediaIdStr = item.mediaId
        val published = _currentSong.value
        if (published != null &&
            (published.id.toString() == mediaIdStr || published.remoteId == mediaIdStr)
        ) {
            return true // already in sync with the session
        }
        val song = resolveSongFromItem(item) ?: return false
        publishCurrentSong(song)
        if (fireOnSongPlayed) onSongPlayed(song)
        return true
    }

    /** Matches a live [MediaItem] against queue, library, and stream cache by id or remote id. */
    private fun resolveSongFromItem(item: MediaItem): Song? {
        val mediaIdStr = item.mediaId
        return _queue.value.find { it.id.toString() == mediaIdStr || it.remoteId == mediaIdStr }
            ?: getSongs().find { it.id.toString() == mediaIdStr }
            ?: getAllStreamSongs().find { it.remoteId == mediaIdStr }
            ?: songFromMediaItem(item, mediaIdStr.hashCode().toLong())
    }

    private fun syncStateFromPlayer() {
        try {
            val p = player ?: return
            val item = p.currentMediaItem ?: return

            val song = resolveSongFromItem(item)
            if (song != null) {
                publishCurrentSong(song)
                onSongPlayed(song)
            }
        } catch (e: Exception) {
            Timber.w(e, "PlayerGateway syncStateFromPlayer failed")
        }
    }

    private fun publishCurrentSong(song: Song) {
        _currentSong.value = song
        currentIndex = _queue.value.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
    }

    // == Queue management ==

    fun markAsPlayed(songId: Long) {
        playedSongIds.add(songId)
    }

    fun autoFillUpNext() {
        if (_repeatMode.value == Player.REPEAT_MODE_ALL) return
        val list = _queue.value
        val idx = currentIndex
        if (idx < 0) return
        val remaining = list.size - idx - 1
        if (remaining > 4) return
        if (autoFillJob?.isActive == true) return
        val current = _currentSong.value ?: return
        if (!current.isStream || current.remoteId == null) return

        autoFillJob = scope.launch {
            Timber.d("PlayerGateway triggering smart auto-fill for: ${current.title}")
            val relatedSongs = ytMusicRepository.fetchRelatedSongs(current.remoteId)

            if (relatedSongs.isEmpty()) {
                // Fallback to plain search when the 'next' endpoint fails
                val query = "${current.artist} ${current.title} radio"
                val result = withContext(Dispatchers.IO) {
                    try { ytMusicRepository.searchSongs(query, onPartial = {}) } catch (e: Exception) { null }
                }
                if (result is StreamSearchResult.Success) {
                    processAutoFillResults(result.songs)
                }
            } else {
                processAutoFillResults(relatedSongs)
            }
        }
    }

    private fun processAutoFillResults(songs: List<Song>) {
        val currentList = _queue.value
        val currentIds = currentList.map { it.id }.toSet()
        val fresh = songs
            .filterNot { it.id in playedSongIds }
            .filterNot { it.id in currentIds }
            .take(5)

        if (fresh.isEmpty()) return

        _queue.value = currentList.toMutableList().apply { addAll(fresh) }
        fresh.forEach { addToPlayerQueue(it) }
        savePlayerState()
    }

    fun toggleShuffle() {
        _isShuffleOn.value = !_isShuffleOn.value
        prefs.edit().putBoolean(PREF_IS_SHUFFLE_ON, _isShuffleOn.value).apply()
        val current = _currentSong.value
        if (_isShuffleOn.value) {
            baseQueueBeforeShuffle = _queue.value
            val others = _queue.value.filter { it.id != current?.id }.shuffled()
            _queue.value = if (current != null) listOf(current) + others else others
        } else {
            _queue.value = baseQueueBeforeShuffle.ifEmpty { getSongs() }
        }
        currentIndex = _queue.value.indexOfFirst { it.id == current?.id }.coerceAtLeast(0)
        savePlayerState()
    }

    fun setQueue(newQueue: List<Song>, startSong: Song? = null) {
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
        target?.let { song ->
            // Play the song as present in the latest queue (match by ID)
            val currentInQueue = _queue.value.find { it.id == song.id } ?: song
            playSong(currentInQueue)
        }
    }

    fun toggleRepeat() {
        val next = when (_repeatMode.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        _repeatMode.value = next
        player?.repeatMode = next
        prefs.edit().putInt(PREF_REPEAT_MODE, next).apply()
    }

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

        moveQueuedItem(actualFrom, actualTo)
        savePlayerState()
    }

    fun removeSongFromQueue(songId: Long) {
        _queue.value = _queue.value.filter { it.id != songId }
        if (_currentSong.value?.id == songId) {
            _currentSong.value = null
            player?.stop()
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

    // == Playback control ==

    fun playSong(song: Song) {
        playbackJob?.cancel()
        playbackJob = scope.launch {
            // Check current index in current queue (don't snapshot yet)
            val initialIndex = _queue.value.indexOfFirst { it.id == song.id }
            if (initialIndex == -1) return@launch

            fadeOutIfPlaying()

            _currentSong.value = song
            currentIndex = initialIndex

            val resolvedSong = resolveIfNeeded(song)

            if (resolvedSong == null) {
                Timber.w("PlayerGateway failed to resolve stream for ${song.title}, aborting")
                _isBuffering.value = false
                handleResolveFailure()
                return@launch
            }
            continuousFailures = 0

            // Update only the specific song in the LATEST queue
            val currentQueue = _queue.value.toMutableList()
            val finalIndex = currentQueue.indexOfFirst { it.id == resolvedSong.id }
            if (finalIndex != -1) {
                currentQueue[finalIndex] = resolvedSong
                _queue.value = currentQueue
                currentIndex = finalIndex
            }

            startPlayback(resolvedSong, _queue.value, if (finalIndex != -1) finalIndex else 0)

            fadeIn()
            onSongPlayed(song)
            savePlayerState()
        }
    }

    /** Auto-transition path when ExoPlayer lands on a placeholder item on its own. */
    private fun resolveAndStart(song: Song) {
        playbackJob?.cancel()
        playbackJob = scope.launch {
            fadeOutIfPlaying()

            val resolvedSong = resolveIfNeeded(song)

            if (resolvedSong == null) {
                Timber.w("PlayerGateway failed to resolve stream in auto-transition, skipping")
                handleResolveFailure()
                return@launch
            }
            continuousFailures = 0

            val currentQueue = _queue.value.toMutableList()
            val useIndex = currentQueue.indexOfFirst { it.id == resolvedSong.id }
            if (useIndex != -1) {
                currentQueue[useIndex] = resolvedSong
                _queue.value = currentQueue
                currentIndex = useIndex
            }

            startPlayback(resolvedSong, _queue.value, startIndex = if (useIndex != -1) useIndex else 0)

            fadeIn()
        }
    }

    /** Shared resolution step: unresolved stream songs go through the provider resolver. */
    private suspend fun resolveIfNeeded(song: Song): Song? {
        if (!StreamSources.needsResolution(song)) return song
        _isBuffering.value = true
        return try {
            val streamUrl = streamResolvers.resolve(song)
            _isBuffering.value = false
            if (streamUrl != null && !isPlaceholderUri(streamUrl)) song.copy(uri = Uri.parse(streamUrl)) else null
        } catch (e: Exception) {
            _isBuffering.value = false
            null
        }
    }

    /** Shared retry policy after resolution failure: skip, unless we failed repeatedly. */
    private fun handleResolveFailure() {
        continuousFailures++
        if (continuousFailures < 3) {
            playNext()
        } else {
            continuousFailures = 0
            _isPlaying.value = false
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
        autoFillUpNext()
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

        insertAfterCurrent(song, curIdx)
        playSong(song)
    }

    fun addToQueue(song: Song) {
        _queue.value = _queue.value.toMutableList().apply { add(song) }
        addToPlayerQueue(song)
        savePlayerState()
    }

    fun togglePlayPause() {
        scope.launch {
            val p = player ?: return@launch
            if (p.isPlaying) {
                fadeVolume(1f, 0f, 150)
                pauseOrPlay()
                p.volume = 1f
            } else {
                p.volume = 0f
                pauseOrPlay()
                fadeVolume(0f, 1f, 150)
            }
        }
    }

    private fun pauseOrPlay() {
        try {
            if (player?.isPlaying == true) player?.pause() else player?.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun seekTo(positionMs: Long) {
        try {
            player?.seekTo(positionMs)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _currentPosition.value = positionMs
    }

    fun getLivePosition(): Long = getCurrentPosition()

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
        scope.launch {
            var formats = ytMusicRepository.getAudioFormats(remoteId)
            if (formats.isEmpty()) {
                val resolvedUrl = if (StreamSources.needsResolution(song)) {
                    streamResolvers.resolve(song)
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

    // == Volume fades ==

    private suspend fun fadeOutIfPlaying() {
        if (player?.isPlaying == true) {
            fadeVolume(1f, 0f, 150)
        } else {
            player?.volume = 0f
        }
    }

    private suspend fun fadeIn() = fadeVolume(0f, 1f, 150)

    private suspend fun fadeVolume(from: Float, to: Float, durationMs: Long) {
        val p = player ?: return
        val steps = 10
        val interval = durationMs / steps
        val delta = (to - from) / steps
        var currentVol = from
        for (i in 1..steps) {
            currentVol += delta
            p.volume = currentVol.coerceIn(0f, 1f)
            delay(interval)
        }
        p.volume = to.coerceIn(0f, 1f)
    }

    // == Prefetch ==

    private fun prefetchNextIfNeeded() {
        val list = _queue.value
        val idx = currentIndex
        if (idx < 0 || idx >= list.size - 1) return

        val next = list[idx + 1]
        if (!next.isStream || !isPlaceholderUri(next.uri.toString())) return
        if (!prefetchingIds.add(next.id)) return

        scope.launch {
            val resolvedUrl = try { streamResolvers.resolve(next) } catch (e: Exception) { Timber.w(e, "PlayerGateway prefetchNext failed"); null }
            prefetchingIds.remove(next.id)
            if (resolvedUrl == null) return@launch

            val resolvedNext = next.copy(uri = Uri.parse(resolvedUrl))
            val currentList = _queue.value.toMutableList()
            val nextPos = currentList.indexOfFirst { it.id == next.id }
            if (nextPos == -1) return@launch
            currentList[nextPos] = resolvedNext
            _queue.value = currentList

            replaceQueuedItem(nextPos, resolvedNext)
        }
    }

    // == Persistence ==

    fun savePlayerState() {
        val current = _currentSong.value ?: return
        SongStore.saveQueueState(prefs, _queue.value, current.id, currentIndex)
    }

    // == Raw-player operations (private; the Player never leaks past this seam) ==

    private fun startPlayback(song: Song, queueSongs: List<Song>, startIndex: Int, startPositionMs: Long = 0) {
        try {
            val p = player ?: return

            // Swap-in path: the session already sits on this song's placeholder item;
            // replacing it preserves the position instead of rebuilding the timeline.
            val targetMediaId = if (song.isStream && !song.remoteId.isNullOrBlank()) song.remoteId else song.id.toString()
            if (p.currentMediaItem?.mediaId == targetMediaId &&
                isPlaceholderUri(p.currentMediaItem?.localConfiguration?.uri?.toString()) &&
                !isPlaceholderUri(song.uri.toString())) {

                val newItem = buildMediaItem(song, mediaIdOverride = song.id.toString())
                p.replaceMediaItem(p.currentMediaItemIndex, newItem)
                p.prepare()
                p.play()
                return
            }

            val mediaItems = buildMediaItems(song, queueSongs)

            p.setMediaItems(mediaItems, if (queueSongs.isNotEmpty()) startIndex else 0, startPositionMs)
            p.prepare()
            p.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun buildMediaItems(song: Song, queueSongs: List<Song>): List<MediaItem> {
        return if (queueSongs.isNotEmpty()) {
            val targetIdx = queueSongs.indexOfFirst { it.id == song.id }
            if (targetIdx == -1) {
                queueSongs.map { buildMediaItem(it) }
            } else {
                queueSongs.mapIndexed { i, s ->
                    if (i == targetIdx) buildMediaItem(song) else buildMediaItem(s)
                }
            }
        } else {
            listOf(buildMediaItem(song))
        }
    }

    private fun buildMediaItem(s: Song, mediaIdOverride: String? = null): MediaItem {
        val meta = MediaMetadata.Builder()
            .setTitle(s.title)
            .setArtist(s.artist)
            .setAlbumTitle(s.album)
            .apply {
                if (!s.remoteArtUrl.isNullOrEmpty()) {
                    setArtworkUri(Uri.parse(s.remoteArtUrl))
                }
            }
            .build()
        // Use remoteId (the YouTube video id) as MediaId when available; more unique than hashCode.
        val mediaId = mediaIdOverride
            ?: if (s.isStream && !s.remoteId.isNullOrBlank()) s.remoteId else s.id.toString()
        return MediaItem.Builder()
            .setUri(s.uri)
            .setMediaId(mediaId)
            .setMediaMetadata(meta)
            .build()
    }

    private fun getCurrentPosition(): Long = player?.currentPosition ?: 0L

    private fun getDuration(): Long = player?.duration ?: 0L

    private fun addToPlayerQueue(song: Song) {
        try {
            player?.addMediaItem(buildMediaItem(song))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun insertAfterCurrent(song: Song, currentIndex: Int) {
        try {
            val p = player ?: return
            val insertPos = (currentIndex + 1).coerceIn(0, p.mediaItemCount)
            p.addMediaItem(insertPos, buildMediaItem(song))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun moveQueuedItem(from: Int, to: Int) {
        try {
            val p = player ?: return
            if (from !in 0 until p.mediaItemCount || to !in 0 until p.mediaItemCount) return
            p.moveMediaItem(from, to)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun replaceQueuedItem(index: Int, song: Song) {
        try {
            val p = player ?: return
            if (index < 0 || index >= p.mediaItemCount) return
            // Swap only the URI at that index, WITHOUT prepare()/play() — the item is not
            // playing yet, it is being pre-resolved. When ExoPlayer auto-advances to it
            // (or the user taps next in the notification, which seeks straight into
            // ExoPlayer), its URI is already real instead of a placeholder.
            val current = p.getMediaItemAt(index)
            val newItem = current.buildUpon()
                .setUri(song.uri)
                .build()
            p.replaceMediaItem(index, newItem)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val PREF_IS_SHUFFLE_ON = "is_shuffle_on"
        private const val PREF_REPEAT_MODE = "repeat_mode"
    }
}
