package com.ianocent.musicplayer.ui.player.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ianocent.musicplayer.data.LyricSource

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
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Lyric :", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = "Toggle Lyric",
                tint = Color.Gray,
                modifier = Modifier.padding(end = 8.dp)
            )
            IconButton(
                onClick = { onOpenLyricCard() },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Rounded.ReceiptLong,
                    "Buat Kartu Lirik",
                    tint = if (hasSelectedLines) accentColor else Color.Gray.copy(alpha = 0.5f)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = { onCycleSource() },
                modifier = Modifier.size(24.dp),
                enabled = hasSong && !isLyricLoading
            ) {
                Icon(
                    Icons.Rounded.SwapHoriz,
                    "Ganti Sumber Lirik",
                    tint = if (isLyricLoading) Color.Gray.copy(alpha = 0.5f) else accentColor
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            if (lyricSource != null && lyricSource != LyricSource.UNKNOWN) {
                Text(
                    lyricSource.name.replace("_", " "),
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}