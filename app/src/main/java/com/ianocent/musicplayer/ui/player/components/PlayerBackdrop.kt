package com.ianocent.musicplayer.ui.player.components

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.ianocent.musicplayer.data.Song
import com.ianocent.musicplayer.data.canvas.CanvasLog
import com.ianocent.musicplayer.data.canvas.CanvasMode
import com.ianocent.musicplayer.data.canvas.CanvasVideoSource
import com.ianocent.musicplayer.data.canvas.providers.CanvasProvider
import com.ianocent.musicplayer.ui.canvas.PlayerCanvasLayer
import com.ianocent.musicplayer.ui.motion.MotionTokens
import com.ianocent.musicplayer.ui.motion.ambientGradientBrush
import com.ianocent.musicplayer.ui.motion.rememberAmbientPalette
import com.ianocent.musicplayer.ui.motion.rememberPlaybackClock

data class PlayerBackdropState(
    val adaptiveColor: Color,
    val animatedAmbient: Color,
    val backgroundBrush: Brush,
    val canvasUsesVideo: Boolean,
    val canvasVideoPath: String?,
    val contentModifier: Modifier,
)

/**
 * Shared ambient/canvas pipeline for every player layout. Consolidates the
 * animated gradient, palette crossfade and canvas draw layer that BASE_IAN,
 * YT_MUSIC and SPOTIFY all used to duplicate. Phase 4 extraction.
 *
 * The rotating gradient is driven by [rememberPlaybackClock] and rendered from
 * a drawBehind callback: while playing the backdrop repaints per frame without
 * recomposing, and when paused (or reduce-motion) it freezes completely.
 */
@Composable
fun rememberPlayerBackdrop(
    song: Song?,
    canvasModeId: Int,
    isPlaying: Boolean,
    ambientColor: Color,
    paletteColors: List<Color>,
    albumArt: Bitmap?,
    isDarkMode: Boolean,
    reduceMotion: Boolean,
    remoteCanvasUrl: String? = null,
    motionScale: Float = 1f,
    screenWidth: Float,
    screenHeight: Float,
): PlayerBackdropState {
    val animatedAmbient by animateColorAsState(
        targetValue = ambientColor,
        animationSpec = MotionTokens.scaledTween(MotionTokens.PALETTE, motionScale, reduceMotion),
        label = "ambientAnim"
    )
    // Derive the control tint from the ANIMATED ambient, not the raw target:
    // otherwise every gamut-shifting video frame snapped the on-screen controls
    // to a new colour instantly while only the gradient eased. Reading the
    // animated value keeps buttons/pills gliding along with the backdrop.
    val adaptiveColor = remember(animatedAmbient, isDarkMode) {
        com.ianocent.musicplayer.data.getAdaptiveControlColor(animatedAmbient, isDarkMode)
    }
    val palette = rememberAmbientPalette(animatedAmbient, paletteColors, isDarkMode, motionScale, reduceMotion)

    val context = LocalContext.current
    val canvasMode = remember(canvasModeId) { CanvasMode.from(canvasModeId) }

    // Local user-provided canvas file (IanCanvas folder). Only honoured when the
    // user has video canvas enabled.
    val localCanvasPath = remember(song?.id, canvasModeId, context) {
        if (canvasMode == CanvasMode.VIDEO) CanvasVideoSource.resolve(song, context) else null
    }

    // Real provider chain: walk from the requested mode, falling back to the
    // next provider (e.g. VIDEO → GENERATIVE) when the primary can't render.
    val resolvedProvider = remember(canvasMode, song?.id, canvasModeId, context) {
        CanvasProvider.resolveFor(canvasMode, song, context)
    }
    val canvasVideoPath = remember(song?.id, canvasModeId, remoteCanvasUrl) {
        // Priority (spec §6.4.3): user's local canvas video file first, then the
        // live Spotify canvas URL, then nothing (existing look stays).
        when {
            localCanvasPath != null -> localCanvasPath
            !remoteCanvasUrl.isNullOrBlank() -> remoteCanvasUrl
            else -> null
        }.also {
            CanvasLog.d(
                "UI: backdrop song=${song?.id} canvasMode=$canvasMode " +
                    "localCanvasPath=$localCanvasPath remoteCanvasUrl=${remoteCanvasUrl ?: "-"} " +
                    "videoPath=${it ?: "-"} source=${when {
                        localCanvasPath != null -> "LOCAL_FILE"
                        !remoteCanvasUrl.isNullOrBlank() -> "REMOTE_SPOTIFY"
                        else -> "NONE"
                    }}"
            )
        }
    }
    val canvasUsesVideo = canvasVideoPath != null
    val canvasDrawModifier = if (canvasUsesVideo || resolvedProvider == null) Modifier else {
        PlayerCanvasLayer(
            mode = resolvedProvider.id,
            isPlaying = isPlaying,
            ambientColor = animatedAmbient,
            paletteColors = paletteColors,
            albumArt = albumArt,
            reduceMotion = reduceMotion
        )
    }

    val gradientClock = rememberPlaybackClock(isPlaying, reduceMotion)
    val contentModifier = Modifier
        .drawBehind {
            val degrees = (gradientClock.value % MotionTokens.GRADIENT_ROTATE).toFloat() /
                MotionTokens.GRADIENT_ROTATE.toFloat() * 360f
            val angle = Math.toRadians(degrees.toDouble()).toFloat()
            val brush = ambientGradientBrush(
                palette.rootBackground,
                palette.slot0,
                palette.slot1,
                palette.slot2,
                angle,
                screenWidth,
                screenHeight
            )
            drawRect(brush)
        }
        .then(canvasDrawModifier)

    return PlayerBackdropState(
        adaptiveColor = adaptiveColor,
        animatedAmbient = animatedAmbient,
        backgroundBrush = Brush.verticalGradient(listOf(palette.rootBackground, palette.rootBackground)),
        canvasUsesVideo = canvasUsesVideo,
        canvasVideoPath = canvasVideoPath,
        contentModifier = contentModifier,
    )
}
