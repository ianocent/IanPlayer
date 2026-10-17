package com.ianocent.musicplayer.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import kotlin.math.cos
import kotlin.math.sin

/**
 * Owns ambient/palette color state and the crossfade between album palettes
 * when the track changes. Extracted from NowPlayingScreen so every player
 * layout can consume the same colors without duplicating transition logic.
 *
 * Mirrors TRACE-019: prev/next blend + ~1.4s transition using motion tokens.
 */

/** Blends an accent color toward black (dark) or white (light) for readability. */
fun blendForTheme(color: Color, dark: Boolean): Color = Color(
    if (dark) ColorUtils.blendARGB(color.toArgb(), android.graphics.Color.BLACK, 0.85f)
    else ColorUtils.blendARGB(color.toArgb(), android.graphics.Color.WHITE, 0.75f)
)

fun blendPaletteSlot(color: Color, dark: Boolean): Color = Color(
    if (dark) ColorUtils.blendARGB(color.toArgb(), android.graphics.Color.BLACK, 0.8f)
    else ColorUtils.blendARGB(color.toArgb(), android.graphics.Color.WHITE, 0.65f)
)

/**
 * Pure builder for the rotating ambient gradient used behind every layout.
 * Shared so BASE_IAN and alternate layouts stay visually coherent.
 */
fun ambientGradientBrush(
    rootBackground: Color,
    slot0: Color,
    slot1: Color,
    slot2: Color,
    angleRadians: Float,
    screenWidth: Float,
    screenHeight: Float
): Brush {
    val dx = (cos(angleRadians.toDouble()).toFloat()) * screenWidth
    val dy = (sin(angleRadians.toDouble()).toFloat()) * screenHeight
    val cx = screenWidth / 2f
    val cy = screenHeight / 2f
    return Brush.linearGradient(
        colors = listOf(
            rootBackground,
            slot0,
            slot1,
            slot2,
            rootBackground
        ),
        start = Offset(cx - dx, cy - dy),
        end = Offset(cx + dx, cy + dy)
    )
}

/**
 * Composable helper returning the animated ambient gradient state.
 */
@Composable
fun rememberAmbientPalette(
    ambientColor: Color,
    paletteColors: List<Color>,
    isDarkMode: Boolean,
    motionScale: Float = 1f,
    reduceMotion: Boolean = false
): AmbientPaletteState {
    val rootBackground = remember(ambientColor, isDarkMode) { blendForTheme(ambientColor, isDarkMode) }

    val targetPaletteBlend = remember(paletteColors, isDarkMode) {
        val base = paletteColors.ifEmpty { listOf(ambientColor) }
        List(3) { i ->
            blendPaletteSlot(base.getOrElse(i) { base.last() }, isDarkMode)
        }
    }

    val paletteTransitionProgress = remember { Animatable(0f) }
    var prevPaletteBlend by remember { mutableStateOf(targetPaletteBlend) }
    var nextPaletteBlend by remember { mutableStateOf(targetPaletteBlend) }

    LaunchedEffect(targetPaletteBlend) {
        prevPaletteBlend = List(3) { i ->
            androidx.compose.ui.graphics.lerp(
                prevPaletteBlend[i], nextPaletteBlend[i], paletteTransitionProgress.value
            )
        }
        nextPaletteBlend = targetPaletteBlend
        paletteTransitionProgress.snapTo(0f)
        if (reduceMotion) {
            paletteTransitionProgress.snapTo(1f)
        } else {
            paletteTransitionProgress.animateTo(
                1f,
                MotionTokens.scaledTween(MotionTokens.PALETTE, motionScale, false)
            )
        }
    }

    val slot0 = androidx.compose.ui.graphics.lerp(prevPaletteBlend[0], nextPaletteBlend[0], paletteTransitionProgress.value)
    val slot1 = androidx.compose.ui.graphics.lerp(prevPaletteBlend[1], nextPaletteBlend[1], paletteTransitionProgress.value)
    val slot2 = androidx.compose.ui.graphics.lerp(prevPaletteBlend[2], nextPaletteBlend[2], paletteTransitionProgress.value)

    return AmbientPaletteState(
        rootBackground = rootBackground,
        slot0 = slot0,
        slot1 = slot1,
        slot2 = slot2
    )
}

class AmbientPaletteState(
    val rootBackground: Color,
    val slot0: Color,
    val slot1: Color,
    val slot2: Color
)