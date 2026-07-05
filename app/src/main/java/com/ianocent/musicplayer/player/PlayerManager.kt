package com.ianocent.musicplayer.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
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

    fun playSong(song: Song) {
        val mediaItem = MediaItem.Builder()
            .setUri(song.uri)
            .setMediaId(song.id.toString())
            .build()

        player?.setMediaItem(mediaItem)
        player?.prepare()
        player?.play()
    }

    fun togglePlayPause() {
        if (player?.isPlaying == true) {
            player?.pause()
        } else {
            player?.play()
        }
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
    }

    fun getCurrentPosition(): Long = player?.currentPosition ?: 0L

    fun getDuration(): Long = player?.duration ?: 0L

    fun toggleRepeat(): Int {
        val currentMode = player?.repeatMode ?: Player.REPEAT_MODE_OFF
        val nextMode = when (currentMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        player?.repeatMode = nextMode
        return nextMode
    }

    fun release() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        player = null
    }
}