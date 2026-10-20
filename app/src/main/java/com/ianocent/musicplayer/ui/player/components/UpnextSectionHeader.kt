package com.ianocent.musicplayer.ui.player.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.ianocent.musicplayer.ui.glass.isAppInDark
import com.ianocent.musicplayer.ui.glass.nounIcon
import androidx.compose.ui.res.vectorResource

/** Collapsible up-next section header. */
@Composable
fun UpnextSectionHeader(
    isExpanded: Boolean,
    onToggle: () -> Unit,
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
        Text("Upnext :", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Icon(
            imageVector = nounIcon(com.ianocent.musicplayer.R.drawable.noun_collapse_expand),
            contentDescription = "Toggle Upnext",
            tint = if (isExpanded) Color.Gray else MaterialTheme.colorScheme.primary
        )
    }
}
