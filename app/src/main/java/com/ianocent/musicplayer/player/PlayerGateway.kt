package com.ianocent.musicplayer.player

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.view.TextureView
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.ianocent.musicplayer.data.AudioFormat
import com.ianocent.musicplayer.data.ArtLoader
import com.ianocent.musicplayer.data.Song
import com.ianocent.musicplayer.data.SongStore
import com.ianocent.musicplayer.data.StreamResolvers
import com.ianocent.musicplayer.data.StreamSearchResult
import com.ianocent.musicplayer.data.StreamSources
import com.ianocent.musicplayer.data.VideoStreamUri
import com.ianocent.musicplayer.data.YTMusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
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
    private val onVideoModeUnsupported: () -> Unit = {},
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
    private var playbackGeneration = 0
    private var overlapPlayer: ExoPlayer? = null
    private var overlapGen = -1

    // The TextureView the session player renders video into (video mode). A
    // TextureView (not SurfaceView) so the UI can sample frames for the
    // dynamic ambient/vibrance color.
    private var videoTexture: TextureView? = null

    private fun isPlaceholderUri(uriString: String?): Boolean =
        uriString?.startsWith(StreamSources.PLACEHOLDER_PREFIX) == true

    /**
     * One-line traces for the music/video mode-switch seam. Filter logcat with
     * the keyword `MODESWITCH` to follow exactly what changed and why a track
     * advanced (transition, ENDED, errors, reverts, skips) without the noise.
     */
    private fun mlog(msg: String) = Timber.tag("MODESWITCH").d(msg)

    // == Player event handling ==

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            onIsPlayingChange(isPlaying)
            if (isPlaying) continuousFailures = 0
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            mlog("onMediaItemTransition: id=${mediaItem?.mediaId} reason=$reason queueIdx=${player?.currentMediaItemIndex} pos=${getCurrentPosition()} videoActive=$videoSourceActive")
            _duration.value = getDuration()
            syncStateFromPlayer()
            savePlayerState()

            val currentItem = player?.currentMediaItem
            if (currentItem != null && isPlaceholderUri(currentItem.localConfiguration?.uri?.toString())) {
                if (playbackJob?.isActive != true) {
                    _currentSong.value?.let { resolveAndStart(it) }
                }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val isBuf = playbackState == Player.STATE_BUFFERING
            _isBuffering.value = isBuf
            onBufferingChange(isBuf)
            if (playbackState == Player.STATE_ENDED) {
                mlog("STATE_ENDED pos=${getCurrentPosition()} dur=${getDuration()} videoActive=$videoSourceActive overlapGen=$overlapGen playbackJobActive=${playbackJob?.isActive} repeat=${player?.repeatMode}")
                // During an overlap fade the service player may hit the end of the
                // old track naturally while the temp player is already playing the
                // next — don't auto-advance on top of the handoff.
                if (overlapGen >= 0 && playbackJob?.isActive == true) { mlog("STATE_ENDED ignored (overlap handoff active)"); return }
                // A merged video source can report ENDED spuriously when its
                // timeline collapses. If we're nowhere near the real end (or can't
                // tell because the duration is unknown), fall back to the
                // audio-only stream for the SAME song instead of cascading through
                // the queue ("ganti-ganti lagu").
                if (isVideoSourceActive()) {
                    val pos = getCurrentPosition()
                    val dur = getDuration()
                    if ((dur <= 0L || pos < dur - 1500L) && tryRevertVideoToAudio()) { mlog("STATE_ENDED -> revert to audio (same song)"); return }
                }
                if (player?.repeatMode != Player.REPEAT_MODE_ONE) {
                    mlog("STATE_ENDED -> playNext()")
                    playNext()
                }
            }
            if (playbackState == Player.STATE_IDLE && player?.playerError != null) {
                mlog("STATE_IDLE error=${player?.playerError} videoActive=$videoSourceActive")
                _isPlaying.value = false

                val currentUri = player?.currentMediaItem?.localConfiguration?.uri?.toString()
                if (isPlaceholderUri(currentUri)) { mlog("STATE_IDLE ignored (placeholder item)"); return }

                // A failed merged video source must not cascade through the queue:
                // fall back to the audio-only stream for the SAME song first.
                if (tryRevertVideoToAudio()) { mlog("STATE_IDLE -> revert to audio (same song)"); return }

                try {
                    player?.prepare()
                    player?.play()
                } catch (e: Exception) {
                    Timber.w(e, "PlayerGateway player retry failed, skip to next")
                    mlog("STATE_IDLE retry failed -> playNext()")
                    playNext()
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            mlog("onPlayerError: ${(error.cause?.cause ?: error.cause ?: error).message} videoActive=$videoSourceActive")
            error.printStackTrace()
            _isPlaying.value = false
            // Don't skip songs when the merged video source fails — revert to the
            // audio-only item for the current track and leave video mode instead.
            if (tryRevertVideoToAudio()) { mlog("onPlayerError -> revert to audio (same song)"); return }
            mlog("onPlayerError -> resolve-failure path")
            handleResolveFailure()
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
                // The listener fires on BOTH success and failure. If the bind failed
                // (e.g. the previous session's PlaybackService is still tearing down
                // when the app is relaunched after playback), `get()` throws
                // ExecutionException. Left unhandled it crashes the process — which is
                // exactly the intermittent "force close on reopen, works after retries".
                try {
                    player = controllerFuture?.get()
                    player?.addListener(playerListener)
                    // Sync play/pause state from the live session — the UI must reflect
                    // reality (e.g. when restoring after the app was killed while playing).
                    _isPlaying.value = player?.isPlaying ?: false
                    // Restore persisted repeat mode to the player (ExoPlayer defaults to OFF).
                    player?.repeatMode = _repeatMode.value
                    // Restore persisted shuffle mode to the player.
                    player?.shuffleModeEnabled = _isShuffleOn.value
                    val restored = restoreCurrentFromPlayer(fireOnSongPlayed = true)
                    onReady(restored, player?.isPlaying ?: false)
                } catch (e: Exception) {
                    Timber.e(e, "PlayerGateway failed to connect to PlaybackService")
                    player = null
                    _isPlaying.value = false
                    onReady(false, false)
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    fun release() {
        releaseOverlapPlayer()
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
        val isVideoSource = songUri.scheme == VideoStreamUri.SCHEME
        val isStream = isVideoSource || songUri.scheme == "http" || songUri.scheme == "https"
        return Song(
            id = item.mediaId.toLongOrNull() ?: fallbackId,
            title = meta.title?.toString() ?: "Unknown",
            artist = meta.artist?.toString() ?: "Unknown Artist",
            duration = p.duration,
            uri = songUri,
            album = meta.albumTitle?.toString() ?: "Unknown Album",
            isStream = isStream,
            isVideo = isVideoSource,
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
                savePlayerState()
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
        savePlayerState()
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

        // Ensure the live song exists in the queue so "Up Next" stays coherent.
        val inQueue = _queue.value.any { it.id == song.id }
        if (!inQueue) {
            _queue.value = listOf(song) + _queue.value
            currentIndex = 0
        }

        if (fireOnSongPlayed) onSongPlayed(song)
        // Persist so next cold start restores the *live* state, not the stale prefs.
        savePlayerState()
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

    fun autoFillUpNext(context: AutoFillContext = AutoFillContext()) {
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
            Timber.d("PlayerGateway triggering smart auto-fill for: ${current.title} (context: ${context})")
            val relatedSongs = ytMusicRepository.fetchRelatedSongs(current.remoteId)

            val filtered = relatedSongs
                .filterNot { it.id in playedSongIds }
                .filterNot { it.id in _queue.value.map { it.id }.toSet() }
                .take(5)

            if (filtered.isNotEmpty()) {
                _queue.value = _queue.value.toMutableList().apply { addAll(filtered) }
                filtered.forEach { addToPlayerQueue(it) }
                savePlayerState()
            }
        }
    }

    fun toggleShuffle() {
        _isShuffleOn.value = !_isShuffleOn.value
        prefs.edit().putBoolean(PREF_IS_SHUFFLE_ON, _isShuffleOn.value).apply()
        player?.shuffleModeEnabled = _isShuffleOn.value
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
        mlog("playSong: ${song.title} id=${song.id} preemptingJob=${playbackJob?.isActive}")
        playbackJob?.cancel()
        val gen = ++playbackGeneration
        releaseOverlapPlayer()
        playbackJob = scope.launch {
            // Check current index in current queue (don't snapshot yet)
            var initialIndex = _queue.value.indexOfFirst { it.id == song.id }
            if (initialIndex == -1) {
                // Song not in queue — add it so playback can proceed
                val updated = _queue.value.toMutableList()
                updated.add(song)
                _queue.value = updated
                initialIndex = updated.size - 1
            }

            _currentSong.value = song
            currentIndex = initialIndex
            savePlayerState()

            val resolvedSong = resolveIfNeeded(song)

            if (resolvedSong == null) {
                Timber.w("PlayerGateway failed to resolve stream for ${song.title}, aborting")
                mlog("playSong(${song.title}): resolve FAILED -> failure path")
                _isBuffering.value = false
                handleResolveFailure()
                return@launch
            }

            // Update only the specific song in the LATEST queue
            val currentQueue = _queue.value.toMutableList()
            val finalIndex = currentQueue.indexOfFirst { it.id == resolvedSong.id }
            if (finalIndex != -1) {
                currentQueue[finalIndex] = resolvedSong
                _queue.value = currentQueue
                currentIndex = finalIndex
            }

            startWithCrossfade(resolvedSong, _queue.value, if (finalIndex != -1) finalIndex else 0, gen)

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
        if (!needsPlaybackResolution(song)) return song
        _isBuffering.value = true
        return try {
            // Video-catalogue songs play the merged video+audio source directly —
            // no runtime mode toggle, no replaceMediaItem mid-playback (the whole
            // swap seam was the source of mid-track cascades).
            if (song.isVideo && song.source == StreamSources.YOUTUBE && !song.remoteId.isNullOrBlank()) {
                val videoStream = withContext(Dispatchers.IO) { ytMusicRepository.resolveVideoStream(song) }
                _isBuffering.value = false
                when {
                    videoStream == null -> {
                        mlog("resolveIfNeeded(${song.title}): no video stream -> audio fallback")
                        onVideoModeUnsupported()
                        fallbackAudio(song)
                    }
                    else -> song.copy(uri = VideoStreamUri.build(videoStream))
                }
            } else {
                val streamUrl = streamResolvers.resolve(song)
                _isBuffering.value = false
                if (streamUrl != null && !isPlaceholderUri(streamUrl)) song.copy(uri = Uri.parse(streamUrl)) else null
            }
        } catch (e: Exception) {
            _isBuffering.value = false
            null
        }
    }

    /** Audio-only resolution for a video song whose video stream is unavailable. */
    private suspend fun fallbackAudio(song: Song): Song? {
        return try {
            val url = streamResolvers.resolve(song)
            if (url != null && !isPlaceholderUri(url)) song.copy(uri = Uri.parse(url)) else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * True when [song] still needs its playable URI resolved — an unresolved
     * placeholder, a stale `ianvideo://` merged URI, or a signed audio URL whose
     * `expire` epoch has passed (restored from persisted state, or left playing
     * past its lifetime). Expired URLs used to be handed straight to the player,
     * whose error path then skipped to the next track.
     */
    private fun needsPlaybackResolution(song: Song): Boolean =
        StreamSources.needsResolution(song) || song.uri.scheme == VideoStreamUri.SCHEME ||
            (song.isStream && song.remoteId != null && StreamSources.isSignedUrlExpired(song.uri.toString()))

    /** Shared retry policy after resolution failure: skip, unless we failed repeatedly. */
    private fun handleResolveFailure() {
        continuousFailures++
        if (continuousFailures < 3) {
            mlog("resolveFailure #$continuousFailures -> playNext (skip)")
            playNext()
        } else {
            continuousFailures = 0
            _isPlaying.value = false
            mlog("resolveFailure x3 -> stopped, no skip")
        }
    }

    /**
     * When the merged video source fails to load, ExoPlayer emits an error and
     * the shared retry policy would skip through the queue ("ganti-ganti lagu").
     * Instead, swap the SAME song back to its audio-only stream so playback
     * continues, and tell the UI to drop video mode. Returns true when a revert
     * was started (the caller must then stop handling the error itself).
     */
    private fun tryRevertVideoToAudio(): Boolean {
        if (!isVideoSourceActive()) return false
        // One revert at a time: onPlayerError and STATE_IDLE can both fire for the
        // same failure, and two concurrent swaps would fight each other.
        if (revertingVideo) { mlog("revertToAudio: already reverting, swallow"); return true }
        revertingVideo = true
        val song = _currentSong.value
        val p = player ?: run { revertingVideo = false; return false }
        if (song == null) { revertingVideo = false; return false }
        mlog("revertToAudio: start song=${song.title} pos=${getCurrentPosition()} playheadIdx=${p.currentMediaItemIndex}")

        val pos = getCurrentPosition()
        val wasPlaying = p.isPlaying
        onVideoModeUnsupported()

        scope.launch {
            try {
                val url = try {
                    // Always resolve the AUDIO-only URL for the fallback — never
                    // the merged `ianvideo://` URI, which would just fail again.
                    streamResolvers.resolve(song)
                } catch (e: Exception) {
                    Timber.w(e, "tryRevertVideoToAudio resolve failed")
                    null
                }
                if (url.isNullOrBlank() || isPlaceholderUri(url)) {
                    mlog("revertToAudio: resolve FAILED -> resolve-failure path")
                    handleResolveFailure()
                    return@launch
                }
                mlog("revertToAudio: resolved, swapping item + prepare @pos=$pos")
                try {
                    p.setVideoTextureView(null)
                    videoTexture = null
                    videoSourceActive = false
                    // A video-switch fade may have left the player at silence;
                    // make sure the audio fallback comes back at full volume.
                    p.volume = 1f
                    val audioItem = buildMediaItem(song.copy(uri = Uri.parse(url)))
                    // The cascade may have moved the playhead off the video item
                    // before the failure fired. Swap in place at the video item's
                    // own slot and re-seat the playhead there, so exactly ONE copy
                    // of this song's audio exists and current stays coherent.
                    val videoSlot = (0 until p.mediaItemCount)
                        .firstOrNull { p.getMediaItemAt(it).mediaId == songMediaId(song) }
                    val seats: Int
                    if (videoSlot != null && videoSlot != p.currentMediaItemIndex) {
                        mlog("revertToAudio: playhead ran away to idx=${p.currentMediaItemIndex}; restoring slot=$videoSlot")
                        p.replaceMediaItem(videoSlot, audioItem)
                        seats = videoSlot
                    } else {
                        val idx = p.currentMediaItemIndex
                        if (idx >= 0) p.replaceMediaItem(idx, audioItem) else p.setMediaItem(audioItem)
                        seats = if (idx >= 0) idx else 0
                    }
                    p.prepare()
                    if (pos > 0) p.seekTo(seats, pos) else if (seats != p.currentMediaItemIndex) p.seekTo(seats, 0)
                    if (wasPlaying) p.play()
                    mlog("revertToAudio: OK slot=$seats")
                } catch (e: Exception) {
                    Timber.w(e, "tryRevertVideoToAudio swap failed")
                    mlog("revertToAudio: swap FAILED -> resolve-failure path")
                    handleResolveFailure()
                }
            } finally {
                revertingVideo = false
            }
        }
        return true
    }

    fun playNext() {
        val list = _queue.value
        if (list.isEmpty()) return
        val isLast = currentIndex >= list.size - 1

        val nextIndex = when {
            isLast && _repeatMode.value == Player.REPEAT_MODE_ALL -> 0
            isLast -> { mlog("playNext: blocked (last track, no repeat)"); return }
            else -> currentIndex + 1
        }
        mlog("playNext: cur=$currentIndex next=$nextIndex (${
            list.getOrNull(currentIndex)?.title} -> ${list.getOrNull(nextIndex)?.title})")
        playSong(list[nextIndex])
        autoFillUpNext(AutoFillContext())
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

    fun playNext(song: Song, autoFillContext: AutoFillContext = AutoFillContext()) {
        val list = _queue.value.toMutableList()
        val curIdx = currentIndex
        if (curIdx < 0) return

        val insertIdx = curIdx + 1
        list.add(insertIdx.coerceAtMost(list.size), song)
        _queue.value = list

        insertAfterCurrent(song, curIdx)
        playSong(song)
        autoFillUpNext(autoFillContext)
    }

    fun addToQueue(song: Song) {
        _queue.value = _queue.value.toMutableList().apply { add(song) }
        addToPlayerQueue(song)
        savePlayerState()
    }

    fun togglePlayPause() {
        scope.launch {
            val p = player ?: return@launch
            // Cold-start restore publishes the current song but never repopulates
            // the fresh player's timeline (the service was stopped on task remove).
            // Resuming via playSong rebuilds the queue instead of no-oping.
            if (p.mediaItemCount == 0) {
                val song = _currentSong.value
                if (song != null) {
                    playSong(song)
                }
                return@launch
            }
            // Never leave the temp overlap player audible if the user pauses.
            if (overlapGen >= 0) releaseOverlapPlayer()
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

    /** Binds (or clears, with null) the TextureView the session player renders video to. */
    fun setVideoTextureView(view: TextureView?) {
        videoTexture = view
        runCatching { player?.setVideoTextureView(view) }
    }

    // Guards against two concurrent audio fallbacks for the same failed video item.
    private var revertingVideo = false

    /** Media id rule shared with [buildMediaItem]: remoteId first, else local id. */
    private fun songMediaId(song: Song): String =
        if (song.isStream && !song.remoteId.isNullOrBlank()) song.remoteId else song.id.toString()

    // Tracks whether the player currently sits on the merged video+audio source.
    // Set explicitly on every swap instead of relying only on the MediaController
    // item's URI (which is not guaranteed to expose localConfiguration).
    private var videoSourceActive = false

    /** True while the player's current item is the merged video+audio source. */
    private fun isVideoSourceActive(): Boolean =
        videoSourceActive ||
            player?.currentMediaItem?.localConfiguration?.uri?.scheme == VideoStreamUri.SCHEME

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
                val resolvedUrl = if (needsPlaybackResolution(song)) {
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

    /** Crossfade/inter-track fade duration in ms. 0 keeps the old instant cut. */
    var crossfadeMs: Int = 0
        set(value) {
            field = value.coerceIn(0, 2000)
        }

    private suspend fun fadeOutIfPlaying() {
        val p = player ?: return
        if (p.isPlaying) {
            val ms = crossfadeMs
            if (ms <= 0) { p.volume = 0f; return }
            easeFade(p, 1f, 0f, ms.toLong())
        } else {
            p.volume = 0f
        }
    }

    private suspend fun fadeIn() {
        val p = player ?: return
        val ms = crossfadeMs
        if (ms <= 0) { p.volume = 1f; return }
        easeFade(p, 0f, 1f, ms.toLong())
    }

    /** Smoothstep-curved volume ramp so track switches feel like a crossfade. */
    private suspend fun easeFade(p: Player, from: Float, to: Float, durationMs: Long) {
        val steps = 12
        val interval = (durationMs / steps).coerceAtLeast(1L)
        for (i in 1..steps) {
            val t = i / steps.toFloat()
            val eased = t * t * (3f - 2f * t)
            p.volume = (from + (to - from) * eased).coerceIn(0f, 1f)
            delay(interval)
        }
        p.volume = to.coerceIn(0f, 1f)
    }

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

    // == True-overlap crossfade ==
    //
    // A second, focus-free ExoPlayer briefly plays the *next* track while the
    // session player fades the *old* one out, then the handoff moves to the
    // session player (so notifications/session stay coherent) and the temp
    // player is retired. Only runs for user-initiated switches; the auto
    // advance path has no audible tail to overlap.

    private suspend fun startWithCrossfade(song: Song, queueSongs: List<Song>, startIndex: Int, gen: Int) {
        val svc = player
        if (crossfadeMs <= 0 || svc == null || !svc.isPlaying) {
            fadeOutIfPlaying()
            startPlayback(song, queueSongs, startIndex)
            fadeIn()
            return
        }

        val temp = ensureOverlapPlayer()
        try {
            temp.setMediaItem(buildMediaItem(song))
            temp.prepare()
            temp.volume = 0f
            temp.play()
        } catch (e: Exception) {
            Timber.w(e, "PlayerGateway overlap prepare failed, fell back to direct switch")
            releaseOverlapPlayer()
            fadeOutIfPlaying()
            startPlayback(song, queueSongs, startIndex)
            fadeIn()
            return
        }
        overlapGen = gen

        try {
            val svcStartVolume = svc.volume.coerceIn(0f, 1f)
            crossfadeVolumes(svc, temp, svcStartVolume, crossfadeMs.toLong())
            if (gen != playbackGeneration) return

            startPlayback(song, queueSongs, startIndex)
            svc.volume = 1f
            waitForTargetPlayback(svc, song)
            if (gen != playbackGeneration) return

            // Handoff complete: duck the temp player out quickly, then retire it.
            easeFade(temp, 1f, 0f, 80)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "PlayerGateway crossfade handoff failed, falling back")
            try {
                startPlayback(song, queueSongs, startIndex)
                svc.volume = 1f
            } catch (_: Exception) {
            }
        } finally {
            if (overlapPlayer === temp) releaseOverlapPlayer()
            if (gen == playbackGeneration) {
                player?.let { p -> runCatching { p.volume = 1f } }
            }
        }
    }

    private suspend fun crossfadeVolumes(svc: Player, temp: Player, fromSvc: Float, fadeMs: Long) {
        val steps = 16
        val interval = (fadeMs / steps).coerceAtLeast(16L)
        for (i in 1..steps) {
            val t = i / steps.toFloat()
            val eased = t * t * (3f - 2f * t)
            svc.volume = (fromSvc * (1f - eased)).coerceIn(0f, 1f)
            temp.volume = eased.coerceIn(0f, 1f)
            delay(interval)
        }
        svc.volume = 0f
        temp.volume = 1f
    }

    /** Blocks until the session player actually starts the [song] target item. */
    private suspend fun waitForTargetPlayback(svc: Player, song: Song) {
        val targetId = if (song.isStream && !song.remoteId.isNullOrBlank()) song.remoteId else song.id.toString()
        val deadline = System.currentTimeMillis() + 8000
        while (System.currentTimeMillis() < deadline) {
            if (svc.playerError != null) {
                handleResolveFailure()
                return
            }
            if (svc.isPlaying && svc.currentMediaItem?.mediaId == targetId) return
            delay(40)
        }
        Timber.w("PlayerGateway overlap handoff timed out waiting for ${song.title}")
    }

    private fun ensureOverlapPlayer(): ExoPlayer {
        overlapPlayer?.let { return it }
        return ExoPlayer.Builder(context)
            .build()
            .also { p ->
                p.setAudioAttributes(AudioAttributes.DEFAULT, false)
                p.setHandleAudioBecomingNoisy(false)
                overlapPlayer = p
            }
    }

    private fun releaseOverlapPlayer() {
        overlapPlayer?.let { p ->
            runCatching {
                p.stop()
                p.clearMediaItems()
            }
            p.release()
        }
        overlapPlayer = null
        overlapGen = -1
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
            val resolvedUrl = if (next.isVideo && next.source == StreamSources.YOUTUBE && !next.remoteId.isNullOrBlank()) {
                try { ytMusicRepository.resolveVideoStream(next)?.let { VideoStreamUri.build(it).toString() } }
                catch (e: Exception) { Timber.w(e, "PlayerGateway prefetchNext video failed"); null }
            } else {
                try { streamResolvers.resolve(next) } catch (e: Exception) { Timber.w(e, "PlayerGateway prefetchNext failed"); null }
            }
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
            // Fresh playback always starts audio-only until the user opts into
            // video mode for this track.
            videoSourceActive = false

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
                    setArtworkUri(Uri.parse(ArtLoader.standardArtworkUrl(s.remoteArtUrl)))
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

    private fun getCurrentPosition(): Long = player?.currentPosition?.coerceAtLeast(0L) ?: 0L

    /**
     * Player duration, sanitised. Merged video sources report C.TIME_UNSET
     * (Long.MIN_VALUE) until the container is fully read, which leaked into the
     * UI as a huge negative "total time". Fall back to the song's known duration
     * for that window.
     */
    private fun getDuration(): Long {
        val d = player?.duration ?: 0L
        if (d != androidx.media3.common.C.TIME_UNSET && d > 0L) return d
        return _currentSong.value?.duration?.takeIf { it > 0L } ?: 0L
    }

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

    data class AutoFillContext(
        val source: AutoFillSource = AutoFillSource.Default,
        val genre: String? = null
    )

    enum class AutoFillSource {
        Default,
        Playlist,
        Genre
    }

    companion object {
        private const val PREF_IS_SHUFFLE_ON = "is_shuffle_on"
        private const val PREF_REPEAT_MODE = "repeat_mode"
    }
}
