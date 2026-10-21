package com.ianocent.musicplayer.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import com.google.common.collect.ImmutableList
import com.ianocent.musicplayer.MainActivity
import com.ianocent.musicplayer.R

/**
 * Media notification carrying the full five-button deck:
 * prev / play-pause / next / shuffle / repeat.
 *
 * Why: Media3's default provider only posts prev-play-next, which is exactly
 * the Samsung symptom ("notification cuman prev, pause, next, gada shuffle
 * sama repeat"). Custom commands cannot be turned into notification buttons,
 * so the two toggles route through the app's own
 * [NotificationButtonReceiver] broadcast (prev/next/play-pause too), whose
 * player mutations sync back into the session and show up here.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class IanPlayerNotificationProvider(context: Context) : MediaNotification.Provider {

    private val appContext = context.applicationContext

    override fun createNotification(
        session: MediaSession,
        commandButtons: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        callback: MediaNotification.Provider.Callback
    ): MediaNotification {
        val player = session.player
        val isPlaying = player?.isPlaying == true
        val metadata = player?.mediaMetadata
        val repeatMode = player?.repeatMode

        val contentIntent = PendingIntent.getActivity(
            appContext,
            0,
            Intent(appContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(appContext, PlaybackService.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(metadata?.title ?: appContext.getString(R.string.app_name))
            .setContentText(metadata?.artist ?: "")
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setOngoing(isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // Compact view on Samsung: 0/1/2 → prev, play-pause, next. Expanded adds 3/4.
            .addAction(notificationButton(NotificationButtonReceiver.ACTION_PREV, "Previous", R.drawable.noun_prev))
            .addAction(
                notificationButton(
                    NotificationButtonReceiver.ACTION_PLAY_PAUSE,
                    if (isPlaying) "Pause" else "Play",
                    if (isPlaying) R.drawable.noun_pause else R.drawable.noun_play
                )
            )
            .addAction(notificationButton(NotificationButtonReceiver.ACTION_NEXT, "Next", R.drawable.noun_next))
            .addAction(
                notificationButton(
                    NotificationButtonReceiver.ACTION_SHUFFLE, "Shuffle", R.drawable.noun_shuffle
                )
            )
            .addAction(
                notificationButton(
                    NotificationButtonReceiver.ACTION_REPEAT, "Repeat",
                    when (repeatMode) {
                        androidx.media3.common.Player.REPEAT_MODE_ONE -> R.drawable.noun_repeat_one
                        androidx.media3.common.Player.REPEAT_MODE_ALL -> R.drawable.noun_repeat_on
                        else -> R.drawable.noun_repeat
                    }
                )
            )
            .setStyle(
                MediaStyleNotificationHelper.MediaStyle(session)
                    .setShowActionsInCompactView(0, 1, 2)
            )

        return MediaNotification(NOTIFICATION_ID, builder.build())
    }

    /** Broadcast PendingIntent targeting the same-app [NotificationButtonReceiver]. */
    private fun notificationButton(action: String, title: String, iconRes: Int): NotificationCompat.Action {
        val intent = Intent(NotificationButtonReceiver.ACTION_NOTIFICATION_BUTTON)
            .setClass(appContext, NotificationButtonReceiver::class.java)
            .putExtra(NotificationButtonReceiver.EXTRA_ACTION, action)
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(iconRes, title, pendingIntent).build()
    }

    /** Unhandled — all buttons use their own broadcast PendingIntents. */
    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: Bundle
    ): Boolean = false

    companion object {
        private const val NOTIFICATION_ID = 7719334
    }
}
