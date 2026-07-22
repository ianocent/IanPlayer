package com.ianocent.musicplayer.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber

class HeadsetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("ian_player_prefs", 0)
        val assistantEnabled = prefs.getBoolean("voice_assistant_enabled", false)
        
        if (!assistantEnabled) return

        when (intent.action) {
            Intent.ACTION_HEADSET_PLUG -> {
                val state = intent.getIntExtra("state", -1)
                if (state == 1) {
                    Timber.d("Headset connected")
                    // Ensure PlaybackService is running for the MediaSession
                    val playbackIntent = Intent(context, PlaybackService::class.java)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(playbackIntent)
                    } else {
                        context.startService(playbackIntent)
                    }
                    IanVoiceAssistantService.start(context)
                } else if (state == 0) {
                    Timber.d("Headset disconnected")
                    IanVoiceAssistantService.stop(context)
                }
            }
            "android.bluetooth.adapter.action.STATE_CHANGED" -> {
                // You can add more specific Bluetooth checks here if needed
            }
            android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED -> {
                Timber.d("Bluetooth connected")
                val playbackIntent = Intent(context, PlaybackService::class.java)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(playbackIntent)
                } else {
                    context.startService(playbackIntent)
                }
                IanVoiceAssistantService.start(context)
            }
            android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                Timber.d("Bluetooth disconnected")
                IanVoiceAssistantService.stop(context)
            }
        }
    }
}
