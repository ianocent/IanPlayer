package com.ianocent.musicplayer.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.ianocent.musicplayer.data.Song
import com.ianocent.musicplayer.player.PlaybackService

class PlayerManager(private val context: Context) {
    // Sekarang pakai Player bawaan interface Media3, bukan ExoPlayer langsung
    var player: Player? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    // Fungsi buat konek UI ke PlaybackService
    fun initialize(onReady: () -> Unit) {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener(
            {
                player = controllerFuture?.get()
                onReady() // Panggil listener pas player udah siap
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    fun playSong(song: Song, queueSongs: List<Song> = emptyList(), startIndex: Int = 0, startPositionMs: Long = 0) {
        try {
            val mediaItems = if (queueSongs.isNotEmpty()) {
                queueSongs.map { s ->
                    val meta = MediaMetadata.Builder()
                        .setTitle(s.title)
                        .setArtist(s.artist)
                        .setAlbumTitle(s.album)
                        .apply {
                            if (!s.remoteArtUrl.isNullOrEmpty()) {
                                setArtworkUri(android.net.Uri.parse(s.remoteArtUrl))
                            }
                        }
                        .build()
                    MediaItem.Builder()
                        .setUri(s.uri)
                        .setMediaId(s.id.toString())
                        .setMediaMetadata(meta)
                        .build()
                }
            } else {
                val metadata = MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .apply {
                        if (!song.remoteArtUrl.isNullOrEmpty()) {
                            setArtworkUri(android.net.Uri.parse(song.remoteArtUrl))
                        }
                    }
                    .build()
                listOf(
                    MediaItem.Builder()
                        .setUri(song.uri)
                        .setMediaId(song.id.toString())
                        .setMediaMetadata(metadata)
                        .build()
                )
            }
            player?.let { p ->
                if (p.playbackState == Player.STATE_IDLE && p.playerError != null) {
                    p.stop()
                }
                p.setMediaItems(mediaItems, if (queueSongs.isNotEmpty()) startIndex else 0, startPositionMs)
                p.prepare()
                p.play()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun togglePlayPause() {
        try {
            if (player?.isPlaying == true) {
                player?.pause()
            } else {
                player?.play()
            }
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
    }

    fun getCurrentPosition(): Long = player?.currentPosition ?: 0L

    fun getDuration(): Long = player?.duration ?: 0L

    private var currentRepeatMode = Player.REPEAT_MODE_OFF

    fun toggleRepeat(): Int {
        currentRepeatMode = when (currentRepeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        player?.repeatMode = currentRepeatMode
        return currentRepeatMode
    }

    fun release() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        player = null
    }
}