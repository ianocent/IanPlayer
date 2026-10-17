package com.ianocent.musicplayer.ui.motion

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Central motion language for the player. All durations/easings/springs live
 * here so layouts share a consistent feel instead of scattering raw
 * tween(...)/spring(...) calls across screens.
 *
 * Phase 0 note: motion_scale/reduce_motion settings land in Phase 1 settings
 * UI; the scale helper below stays safe to call anytime.
 */

object MotionTokens {
    // Durations (ms)
    const val FAST = 150
    const val STANDARD = 250
    const val EXPRESSIVE = 400
    const val PALETTE = 1200
    const val CANVAS = 500

    // Easing
    val fastOutSlowIn get() = FastOutSlowInEasing
    val easeInOut get() = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)

    // Springs
    val fastSpring get() = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh)
    val standardSpring get() = spring<Float>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)
    val morphSpring get() = spring<Float>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
    val sheetSpring get() = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)

    // Named specs
    val smallUiChange get() = tween<Float>(FAST, easing = FastOutSlowInEasing)
    val playerSection get() = tween<Float>(STANDARD, easing = FastOutSlowInEasing)
    val artworkMorph get() = spring<Float>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
    val paletteTransition get() = tween<Float>(PALETTE, easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f))
    val canvasFade get() = tween<Float>(CANVAS, easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f))

    // Additional named durations for player UI consistency
    const val SECTION_EXPAND = 500
    const val LYRIC_HIGHLIGHT = 350
    const val SHIMMER = 1200
    const val GRADIENT_ROTATE = 30000

    /**
     * Returns a [tween] scaled by [motionScale] and instantly completed when
     * [reduceMotion] is true — single call site ensures every animation in the
     * player respects the user's accessibility preferences.
     */
    fun <T> scaledTween(
        baseDurationMs: Int,
        motionScale: Float,
        reduceMotion: Boolean,
        easing: Easing = FastOutSlowInEasing
    ): AnimationSpec<T> =
        if (reduceMotion) tween(0, easing = easing)
        else tween((baseDurationMs * motionScale).toInt().coerceAtLeast(0), easing = easing)
}