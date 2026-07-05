package com.ianocent.musicplayer.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.ianocent.musicplayer.data.Song

class PlayerManager(context: Context) {

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build()

    fun playSong(song: Song) {
        val mediaItem = MediaItem.fromUri(song.uri)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.play()
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
    }

    fun getCurrentPosition(): Long = exoPlayer.currentPosition

    fun getDuration(): Long = exoPlayer.duration.coerceAtLeast(0L)

    fun release() {
        exoPlayer.release()
    }
}