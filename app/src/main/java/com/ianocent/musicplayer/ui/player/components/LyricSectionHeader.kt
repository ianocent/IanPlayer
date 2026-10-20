package com.ianocent.musicplayer.ui.player.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ianocent.musicplayer.R
import com.ianocent.musicplayer.data.LyricSource
import com.ianocent.musicplayer.ui.glass.glassIconBackdrop
import com.ianocent.musicplayer.ui.glass.isAppInDark
import com.ianocent.musicplayer.ui.glass.nounIcon

/** Collapsible lyric section header with card + source controls. */
@Composable
fun LyricSectionHeader(
    isExpanded: Boolean,
    hasSelectedLines: Boolean,
    lyricSource: LyricSource?,
    isLyricLoading: Boolean,
    hasSong: Boolean,
    accentColor: Color,
    onToggle: () -> Unit,
    onOpenLyricCard: () -> Unit,
    onCycleSource: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onToggle() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Lyric :", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .glassIconBackdrop(
                        dark = isAppInDark(),
                        accent = accentColor,
                        active = isExpanded,
                        shape = CircleShape
                    )
                    .clip(CircleShape)
                    .clickable { onToggle() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = nounIcon(R.drawable.noun_collapse_expand),
                    contentDescription = "Toggle Lyric",
                    tint = if (isExpanded) accentColor else Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .glassIconBackdrop(
                        dark = isAppInDark(),
                        accent = accentColor,
                        active = hasSelectedLines,
                        shape = CircleShape
                    )
                    .clip(CircleShape)
                    .clickable { onOpenLyricCard() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = nounIcon(R.drawable.noun_songlyric),
                    contentDescription = "Buat Kartu Lirik",
                    tint = if (hasSelectedLines) accentColor else Color.Gray.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .glassIconBackdrop(
                        dark = isAppInDark(),
                        accent = accentColor,
                        active = false,
                        shape = CircleShape
                    )
                    .clip(CircleShape)
                    .clickable(enabled = hasSong && !isLyricLoading) { onCycleSource() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = nounIcon(R.drawable.noun_switch),
                    contentDescription = "Ganti Sumber Lirik",
                    tint = if (isLyricLoading) Color.Gray.copy(alpha = 0.5f) else accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (lyricSource != null && lyricSource != LyricSource.UNKNOWN) {
                Text(
                    text = lyricSource.name.replace("_", " "),
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor.copy(alpha = 0.85f),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
