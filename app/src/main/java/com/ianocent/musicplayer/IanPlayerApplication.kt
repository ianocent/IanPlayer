package com.ianocent.musicplayer

import android.app.Application
import com.zemer.cipher.ZemerCipher

class IanPlayerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Must run before any streaming/PoToken/cipher call (YTMusicRepository).
        ZemerCipher.initialize(
            context = applicationContext,
            debugLogging = BuildConfig.DEBUG
        )
    }
}
