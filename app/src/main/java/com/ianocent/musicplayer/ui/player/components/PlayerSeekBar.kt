package com.ianocent.musicplayer.ui.player.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Fraction-based seek slider. Takes the position as a [State] and reads it here,
 * so the 100ms playback tick recomposes only this slider (and not the whole
 * Now Playing screen). Phase 10 swaps style behind this seam.
 */
@Composable
fun PlayerSeekBar(
    positionState: State<Long>,
    durationMs: Long,
    accentColor: Color,
    onSeekFraction: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val positionMs = positionState.value
    Box(modifier = modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.Center) {
        Slider(
            value = if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f,
            onValueChange = { fraction -> onSeekFraction(fraction) },
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = accentColor.copy(alpha = 0.25f)
            )
        )
    }
}
