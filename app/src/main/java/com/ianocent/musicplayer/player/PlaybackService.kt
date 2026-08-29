package com.ianocent.musicplayer.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.ianocent.musicplayer.MainActivity
import java.io.File

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null

    private var headsetReceiver: HeadsetReceiver? = null

    override fun onCreate() {
        super.onCreate()
        registerHeadsetReceiver()
        createNotificationChannel()
        // Balanced buffer for streaming — 2x default gives smooth playback
        // on all network conditions without wasting memory on low-end devices
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

        // Smart decoder selection for all SoCs:
        // - Hardware decoders first (battery efficient — Qualcomm/MTK/Exynos/Kirin DSP)
        // - Software decoders as fallback (compatible — if HW fails, Media3 retries with SW)
        // - No arbitrary exclusion of any vendor — if a HW decoder crashes,
        //   fallback mechanism catches it and retries with the next decoder in list
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
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setHandleAudioBecomingNoisy(true)
            .setLoadControl(loadControl)
            .setAudioAttributes(playbackAttributes, true)
            .setSkipSilenceEnabled(false) // Standar musik: jangan skip silence di lagu
            .setPauseAtEndOfMediaItems(false)
            .build().also { exoPlayer ->
                audioSessionId = exoPlayer.audioSessionId
                exoPlayer.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (audioSessionId == 0) {
                            audioSessionId = exoPlayer.audioSessionId
                        }
                    }
                })
            }
        val sessionIntent = Intent(this, MainActivity::class.java).let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        mediaSession = MediaSession.Builder(this, player!!)
            .setSessionActivity(sessionIntent)
            .setId("IanPlayerSession")
            .build()
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
        // Force notification update to ensure it stays visible during stream resolution
        super.onUpdateNotification(session, true)
    }

    override fun onDestroy() {
        unregisterReceiver(headsetReceiver)
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
        registerReceiver(headsetReceiver, filter)
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

        /** Written by the session owner, read cross-module as a fallback — must be volatile. */
        @Volatile
        var audioSessionId: Int = 0
    }
}