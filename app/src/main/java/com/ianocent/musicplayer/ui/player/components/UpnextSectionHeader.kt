package com.ianocent.musicplayer.ui.player.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ianocent.musicplayer.R
import com.ianocent.musicplayer.ui.glass.glassIconBackdrop
import com.ianocent.musicplayer.ui.glass.isAppInDark
import com.ianocent.musicplayer.ui.glass.nounIcon

/** Collapsible up-next header. Controls share the Lyric header's 32dp glass
 *  button kit (collapse/expand, like, record) so both section headers read as
 *  one visual family: `<Label> : … [collapse][love][record]`. */
@Composable
fun UpnextSectionHeader(
    isExpanded: Boolean,
    hasSong: Boolean,
    isLiked: Boolean,
    accentColor: Color,
    onToggle: () -> Unit,
    onToggleLike: () -> Unit,
    onOpenRecord: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(remember { MutableInteractionSource() }, null) { onToggle() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Upnext :", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
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
                    contentDescription = "Toggle Upnext",
                    tint = if (isExpanded) accentColor else Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .glassIconBackdrop(
                        dark = isAppInDark(),
                        accent = if (isLiked) Color(0xFFE91E63) else Color.Gray,
                        active = isLiked,
                        shape = CircleShape
                    )
                    .clip(CircleShape)
                    .clickable(enabled = hasSong) { onToggleLike() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isLiked) nounIcon(R.drawable.noun_love) else nounIcon(R.drawable.noun_love_outline),
                    contentDescription = if (isLiked) "Unlike" else "Like",
                    tint = if (isLiked) Color(0xFFE91E63) else Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .glassIconBackdrop(
                        dark = isAppInDark(),
                        accent = Color(0xFFE91E63),
                        active = false,
                        shape = CircleShape
                    )
                    .clip(CircleShape)
                    .clickable(enabled = hasSong) { onOpenRecord() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = nounIcon(R.drawable.noun_record),
                    contentDescription = "Record",
                    tint = Color(0xFFE91E63),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
