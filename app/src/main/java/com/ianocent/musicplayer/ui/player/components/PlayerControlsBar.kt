package com.ianocent.musicplayer.ui.player.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.ianocent.musicplayer.ui.glass.IanGlassConfig
import com.ianocent.musicplayer.ui.glass.IanGlassSurface
import dev.chrisbanes.haze.HazeState

/** Glass transport bar with the 5 primary playback actions. */
@Composable
fun PlayerControlsBar(
    isPlaying: Boolean,
    isShuffleOn: Boolean,
    repeatMode: Int,
    isDarkMode: Boolean,
    accentColor: Color,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    modifier: Modifier = Modifier,
    glassConfig: IanGlassConfig = IanGlassConfig.Disabled,
    state: HazeState? = null,
    liveBackdrop: Boolean = true,
) {
    val buttons: @Composable BoxScope.() -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val buttonBg = if (isDarkMode) Color.Black.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.8f)
            val iconColor = if (isDarkMode) Color.White else Color.Black

            ControlButton(
                icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                onClick = onPlayPause,
                bgColor = buttonBg, iconTint = iconColor
            )
            ControlButton(
                icon = Icons.Rounded.SkipPrevious,
                onClick = onPrevious,
                bgColor = buttonBg, iconTint = iconColor
            )
            ControlButton(
                icon = Icons.Rounded.SkipNext,
                onClick = onNext,
                bgColor = buttonBg, iconTint = iconColor
            )
            ControlButton(
                icon = Icons.Rounded.Shuffle,
                onClick = onToggleShuffle,
                active = isShuffleOn,
                bgColor = buttonBg, iconTint = iconColor
            )
            ControlButton(
                icon = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                onClick = onToggleRepeat,
                active = repeatMode != Player.REPEAT_MODE_OFF,
                bgColor = buttonBg, iconTint = iconColor
            )
        }
    }

    if (glassConfig.enabled) {
        IanGlassSurface(
            config = glassConfig.copy(cornerRadius = 32f),
            shape = RoundedCornerShape(32.dp),
            modifier = modifier.fillMaxWidth(),
            state = state,
            liveBackdrop = liveBackdrop
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(accentColor.copy(alpha = 0.35f), accentColor.copy(alpha = 0.12f))
                        )
                    )
                    .padding(vertical = 16.dp)
            ) {
                buttons()
            }
        }
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(accentColor, accentColor.copy(alpha = 0.5f))
                    )
                )
                .padding(vertical = 16.dp)
        ) {
            buttons()
        }
    }
}

@Composable
fun ControlButton(
    icon: ImageVector,
    onClick: () -> Unit,
    active: Boolean = false,
    badge: String? = null,
    iconTint: Color = Color.Black,
    bgColor: Color = Color.White
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(50))
            .background(if (active) bgColor.copy(alpha = 0.7f) else bgColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = iconTint)
        if (badge != null) {
            Text(
                badge,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                color = if (active) iconTint else iconTint.copy(alpha = 0.5f),
                modifier = Modifier.offset(x = 1.dp, y = 1.dp)
            )
        }
    }
}