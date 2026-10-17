package com.ianocent.musicplayer.ui.glass

import androidx.compose.ui.graphics.Color

/** Tuning knobs for the shared glass system. Mirrors SettingsStore glass keys. */
data class IanGlassConfig(
    val enabled: Boolean = false,
    val blurRadius: Float = 16f,
    val vibrancy: Float = 0.3f,
    val highlight: Float = 0.1f,
    val tint: Color = Color.White.copy(alpha = 0.05f),
    val cornerRadius: Float = 16f
) {
    companion object {
        val Disabled = IanGlassConfig()
    }
}