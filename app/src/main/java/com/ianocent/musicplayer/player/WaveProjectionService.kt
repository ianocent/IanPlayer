package com.ianocent.musicplayer.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import timber.log.Timber

class WaveProjectionService : Service() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel("media_projection", "Recording", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ForegroundServiceType")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val n = NotificationCompat.Builder(this, "media_projection")
            .setContentTitle("IanPlayer")
            .setContentText("Recording audio visualizer")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(1001, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } else {
                startForeground(1001, n)
            }
        } catch (e: SecurityException) {
            Timber.w(e, "startForeground with mediaProjection type failed, retrying without type")
            try {
                @Suppress("DEPRECATION")
                startForeground(1001, n)
            } catch (e2: Exception) {
                Timber.e(e2, "startForeground fallback also failed")
            }
        }

        return START_NOT_STICKY
    }
}
