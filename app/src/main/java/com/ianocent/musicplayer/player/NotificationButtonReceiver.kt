package com.ianocent.musicplayer.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.media3.common.Player

class NotificationButtonReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.getStringExtra(EXTRA_ACTION) ?: return
        val player = PlaybackService.playerInstance ?: return

        when (action) {
            ACTION_PREV -> player.seekToPrevious()
            ACTION_PLAY_PAUSE -> {
                if (player.isPlaying) {
                    player.pause()
                } else {
                    if (player.playbackState == Player.STATE_ENDED) {
                        player.seekToDefaultPosition()
                    }
                    player.play()
                }
            }
            ACTION_NEXT -> player.seekToNext()
            ACTION_SHUFFLE -> {
                player.shuffleModeEnabled = !player.shuffleModeEnabled
            }
            ACTION_REPEAT -> {
                player.repeatMode = when (player.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
            }
        }
    }

    companion object {
        const val ACTION_NOTIFICATION_BUTTON = "com.ianocent.musicplayer.NOTIFICATION_BUTTON"
        const val EXTRA_ACTION = "action"
        const val ACTION_PREV = "prev"
        const val ACTION_PLAY_PAUSE = "play_pause"
        const val ACTION_NEXT = "next"
        const val ACTION_SHUFFLE = "shuffle"
        const val ACTION_REPEAT = "repeat"
    }
}
