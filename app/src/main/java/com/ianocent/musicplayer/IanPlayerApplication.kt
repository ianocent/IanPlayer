package com.ianocent.musicplayer

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import com.zemer.cipher.ZemerCipher
import timber.log.Timber

class IanPlayerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        ZemerCipher.initialize(
            context = applicationContext,
            debugLogging = BuildConfig.DEBUG
        )
    }
}
