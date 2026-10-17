package com.ianocent.musicplayer.ui.motion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalInspectionMode

/**
 * Monotonic millisecond clock that only advances while [isPlaying] and motion is
 * allowed. It exists so continuous backdrop effects (aurora, Ken Burns, rotating
 * gradient) stop burning frames the moment playback pauses or the user asked for
 * reduced motion, instead of running an [androidx.compose.animation.core.rememberInfiniteTransition]
 * forever.
 *
 * Read [State.value] at DRAW time (inside [androidx.compose.ui.draw.drawBehind] /
 * drawWithCache) rather than in composition, so the effect redraws each frame
 * without recomposing any composable. That is the whole point: the previous
 * implementation read an infinite-transition value in the composable body, which
 * recomposed the backdrop subtree ~60x/second.
 */
@Composable
fun rememberPlaybackClock(isPlaying: Boolean, reduceMotion: Boolean = false): State<Long> {
    val running = isPlaying && !reduceMotion && !LocalInspectionMode.current
    val phase = remember { mutableLongStateOf(0L) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            phase.longValue += (now - last) / 1_000_000L
            last = now
        }
    }
    return phase
}
