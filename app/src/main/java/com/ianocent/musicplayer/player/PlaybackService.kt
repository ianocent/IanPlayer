package com.ianocent.musicplayer.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.ianocent.musicplayer.MainActivity
import com.ianocent.musicplayer.R

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

    private var headsetReceiver: HeadsetReceiver? = null
    private var notificationButtonReceiver: NotificationButtonReceiver? = null

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(IanPlayerNotificationProvider(this))
        registerHeadsetReceiver()
        createNotificationChannel()
        registerNotificationReceiver()
        val loadControl: LoadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                DefaultLoadControl.DEFAULT_MIN_BUFFER_MS * 2,
                DefaultLoadControl.DEFAULT_MAX_BUFFER_MS * 2,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS * 2,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS * 2
            )
            .build()

        val playbackAttributes = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setAllowedCapturePolicy(C.ALLOW_CAPTURE_BY_ALL)
            .build()

        val mediaCodecSelector = object : MediaCodecSelector {
            override fun getDecoderInfos(
                mimeType: String,
                requiresSecureDecoder: Boolean,
                requiresTunnelingDecoder: Boolean
            ): List<MediaCodecInfo> {
                val allInfos = MediaCodecSelector.DEFAULT.getDecoderInfos(
                    mimeType, requiresSecureDecoder, requiresTunnelingDecoder
                )
                return allInfos.sortedBy { it.softwareOnly }
            }
        }
        val renderersFactory = DefaultRenderersFactory(this)
            .setMediaCodecSelector(mediaCodecSelector)
            .setEnableDecoderFallback(true)
        player = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(VideoMergeMediaSourceFactory(this))
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setHandleAudioBecomingNoisy(true)
            .setLoadControl(loadControl)
            .setAudioAttributes(playbackAttributes, true)
            .setSkipSilenceEnabled(false)
            .setPauseAtEndOfMediaItems(false)
            .build().also { exoPlayer ->
                audioSessionId = exoPlayer.audioSessionId
                playerInstance = exoPlayer
                exoPlayer.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (audioSessionId == 0) {
                            audioSessionId = exoPlayer.audioSessionId
                        }
                    }

                    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                        updateMediaSessionCustomLayout()
                    }

                    override fun onRepeatModeChanged(repeatMode: Int) {
                        updateMediaSessionCustomLayout()
                    }
                })
            }
        val sessionIntent = Intent(this, MainActivity::class.java).let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        val shuffleCmd = SessionCommand(CUSTOM_CMD_SHUFFLE, Bundle.EMPTY)
        val repeatCmd = SessionCommand(CUSTOM_CMD_REPEAT, Bundle.EMPTY)

        mediaSession = MediaSession.Builder(this, player!!)
            .setSessionActivity(sessionIntent)
            .setId("IanPlayerSession")
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    val connectionResult = super.onConnect(session, controller)
                    val availableSessionCommands = connectionResult.availableSessionCommands.buildUpon()
                        .add(shuffleCmd)
                        .add(repeatCmd)
                        .build()
                    return MediaSession.ConnectionResult.accept(
                        availableSessionCommands,
                        connectionResult.availablePlayerCommands
                    )
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle
                ): com.google.common.util.concurrent.ListenableFuture<SessionResult> {
                    when (customCommand.customAction) {
                        CUSTOM_CMD_SHUFFLE -> {
                            val p = player ?: return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                            p.shuffleModeEnabled = !p.shuffleModeEnabled
                            updateMediaSessionCustomLayout()
                        }
                        CUSTOM_CMD_REPEAT -> {
                            val p = player ?: return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                            p.repeatMode = when (p.repeatMode) {
                                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                else -> Player.REPEAT_MODE_OFF
                            }
                            updateMediaSessionCustomLayout()
                        }
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            })
            .build()

        updateMediaSessionCustomLayout()
    }

    private fun updateMediaSessionCustomLayout() {
        val p = player ?: return
        val isShuffle = p.shuffleModeEnabled
        val repeatMode = p.repeatMode

        val shuffleIcon = if (isShuffle) R.drawable.noun_shuffle else R.drawable.noun_shuffle_off

        val shuffleButton = CommandButton.Builder()
            .setDisplayName(if (isShuffle) "Shuffle On" else "Shuffle Off")
            .setSessionCommand(SessionCommand(CUSTOM_CMD_SHUFFLE, Bundle.EMPTY))
            .setIconResId(shuffleIcon)
            .setEnabled(true)
            .build()

        val repeatIcon = when (repeatMode) {
            Player.REPEAT_MODE_ONE -> R.drawable.noun_repeat_one
            Player.REPEAT_MODE_ALL -> R.drawable.noun_repeat_on
            else -> R.drawable.noun_repeat_off
        }
        val repeatTitle = when (repeatMode) {
            Player.REPEAT_MODE_ONE -> "Repeat One"
            Player.REPEAT_MODE_ALL -> "Repeat All"
            else -> "Repeat Off"
        }
        val repeatButton = CommandButton.Builder()
            .setDisplayName(repeatTitle)
            .setSessionCommand(SessionCommand(CUSTOM_CMD_REPEAT, Bundle.EMPTY))
            .setIconResId(repeatIcon)
            .setEnabled(true)
            .build()

        mediaSession?.setCustomLayout(ImmutableList.of(shuffleButton, repeatButton))
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = player ?: return
        if (!p.playWhenReady || p.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        super.onUpdateNotification(session, true)
    }

    override fun onDestroy() {
        unregisterReceiver(headsetReceiver)
        unregisterReceiver(notificationButtonReceiver)
        playerInstance = null
        mediaSession?.run {
            player?.release()
            release()
            if (mediaSession == this) {
                mediaSession = null
            }
        }
        player = null
        super.onDestroy()
    }

    private fun registerHeadsetReceiver() {
        headsetReceiver = HeadsetReceiver()
        val filter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        androidx.core.content.ContextCompat.registerReceiver(
            this, headsetReceiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun registerNotificationReceiver() {
        notificationButtonReceiver = NotificationButtonReceiver()
        val filter = IntentFilter(NotificationButtonReceiver.ACTION_NOTIFICATION_BUTTON)
        androidx.core.content.ContextCompat.registerReceiver(
            this, notificationButtonReceiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Media playback controls"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "ianplayer_playback"
        private const val CUSTOM_CMD_SHUFFLE = "ianplayer.CMD_TOGGLE_SHUFFLE"
        private const val CUSTOM_CMD_REPEAT = "ianplayer.CMD_TOGGLE_REPEAT"

        @Volatile
        var audioSessionId: Int = 0

        @Volatile
        var playerInstance: ExoPlayer? = null
            private set
    }
}
