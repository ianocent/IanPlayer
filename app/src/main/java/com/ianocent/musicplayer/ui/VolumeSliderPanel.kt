package com.ianocent.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Shared volume slider used by the listening pill and the mini-player bar.
 * [modifier] sizes the row per host layout.
 */
@Composable
fun VolumeSliderPanel(
    currentVolume: Int,
    maxVolume: Int,
    adaptiveColor: Color,
    onVolumeChange: (Int) -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .padding(end = 8.dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .clickable { onCollapse() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.ChevronLeft,
                contentDescription = "Back",
                tint = Color.Gray,
                modifier = Modifier.size(20.dp)
            )
        }
        Slider(
            value = currentVolume.toFloat(),
            onValueChange = { v -> onVolumeChange(v.toInt()) },
            valueRange = 0f..maxVolume.toFloat(),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .height(24.dp),
            colors = SliderDefaults.colors(
                thumbColor = adaptiveColor,
                activeTrackColor = adaptiveColor,
                inactiveTrackColor = adaptiveColor.copy(alpha = 0.25f)
            )
        )
        Icon(
            Icons.Rounded.VolumeUp,
            contentDescription = "Vol max",
            tint = adaptiveColor,
            modifier = Modifier.size(20.dp)
        )
    }
}
