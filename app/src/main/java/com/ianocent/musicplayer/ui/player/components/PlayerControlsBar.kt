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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.ianocent.musicplayer.R
import com.ianocent.musicplayer.ui.glass.IanGlassConfig
import com.ianocent.musicplayer.ui.glass.IanGlassSurface
import com.ianocent.musicplayer.ui.glass.glassIconBackdrop
import com.ianocent.musicplayer.ui.glass.nounIcon
import dev.chrisbanes.haze.HazeState

/** Glass transport bar with the 5 primary playback actions in user-preferred order: Pause/Play, Prev, Next, Shuffle, Repeat. */
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
            val dark = isDarkMode
            val defaultIconTint = if (dark) Color.White else Color(0xFF1A1A1A)

            // 1. Play / Pause
            ControlButton(
                icon = if (isPlaying) nounIcon(R.drawable.noun_pause) else nounIcon(R.drawable.noun_play),
                onClick = onPlayPause,
                active = isPlaying,
                accent = accentColor,
                dark = dark,
                tint = if (isPlaying) accentColor else defaultIconTint,
                buttonSize = 48.dp,
                iconSize = 24.dp
            )

            // 2. Previous
            ControlButton(
                icon = nounIcon(R.drawable.noun_prev),
                onClick = onPrevious,
                accent = accentColor,
                dark = dark,
                tint = defaultIconTint,
                buttonSize = 48.dp,
                iconSize = 24.dp
            )

            // 3. Next
            ControlButton(
                icon = nounIcon(R.drawable.noun_next),
                onClick = onNext,
                accent = accentColor,
                dark = dark,
                tint = defaultIconTint,
                buttonSize = 48.dp,
                iconSize = 24.dp
            )

            // 4. Shuffle
            ControlButton(
                icon = nounIcon(R.drawable.noun_shuffle),
                onClick = onToggleShuffle,
                active = isShuffleOn,
                accent = accentColor,
                dark = dark,
                tint = if (isShuffleOn) accentColor else defaultIconTint,
                buttonSize = 48.dp,
                iconSize = 24.dp
            )

            // 5. Repeat
            ControlButton(
                icon = if (repeatMode == Player.REPEAT_MODE_ONE) nounIcon(R.drawable.noun_repeat_one) else nounIcon(R.drawable.noun_repeat),
                onClick = onToggleRepeat,
                active = repeatMode != Player.REPEAT_MODE_OFF,
                accent = accentColor,
                dark = dark,
                tint = if (repeatMode != Player.REPEAT_MODE_OFF) accentColor else defaultIconTint,
                buttonSize = 48.dp,
                iconSize = 24.dp
            )
        }
    }

    if (glassConfig.enabled) {
        IanGlassSurface(
            config = glassConfig.copy(cornerRadius = 28f),
            shape = RoundedCornerShape(28.dp),
            modifier = modifier.fillMaxWidth(),
            state = state,
            liveBackdrop = liveBackdrop
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                accentColor.copy(alpha = 0.22f),
                                accentColor.copy(alpha = 0.08f)
                            )
                        )
                    )
                    .padding(horizontal = 12.dp, vertical = 14.dp)
            ) {
                buttons()
            }
        }
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(accentColor.copy(alpha = 0.8f), accentColor.copy(alpha = 0.4f))
                    )
                )
                .padding(horizontal = 12.dp, vertical = 14.dp)
        ) {
            buttons()
        }
    }
}

@Composable
fun ControlButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    badge: String? = null,
    accent: Color = Color.Unspecified,
    dark: Boolean = false,
    tint: Color = Color.Unspecified,
    buttonSize: Dp = 48.dp,
    iconSize: Dp = 24.dp
) {
    val defaultTint = if (dark) Color.White else Color(0xFF1A1A1A)
    val effectiveTint = if (tint != Color.Unspecified) tint else defaultTint

    Box(
        modifier = modifier
            .size(buttonSize)
            .glassIconBackdrop(
                dark = dark,
                accent = accent,
                active = active,
                shape = CircleShape
            )
            .clip(CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = effectiveTint,
            modifier = Modifier.size(iconSize)
        )
        if (badge != null) {
            Text(
                text = badge,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                color = if (active) effectiveTint else effectiveTint.copy(alpha = 0.5f),
                modifier = Modifier.offset(x = 1.dp, y = 1.dp)
            )
        }
    }
}
