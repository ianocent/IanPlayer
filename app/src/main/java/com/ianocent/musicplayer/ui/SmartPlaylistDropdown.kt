package com.ianocent.musicplayer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun SortAndSmartPlaylistRow(
    sortMode: Int,
    onSortModeChange: (Int) -> Unit,
    selectedSmartPlaylist: Int,
    onSmartPlaylistChange: (Int) -> Unit,
    mostPlayedCount: Int,
    recentlyAddedCount: Int,
    neverPlayedCount: Int,
    adaptiveColor: Color
) {
    var showSortMenu by remember { mutableStateOf(false) }
    var showSmartPlaylistMenu by remember { mutableStateOf(false) }
    val sortLabels = listOf("A - Z", "Recently Added", "Most Played")

    Row(verticalAlignment = Alignment.CenterVertically) {
        // Sort button + dropdown
        Box {
            IconButton(
                onClick = { showSortMenu = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Rounded.Sort, "Sort",
                    tint = adaptiveColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            DropdownMenu(
                expanded = showSortMenu,
                onDismissRequest = { showSortMenu = false },
                shape = RoundedCornerShape(24.dp)
            ) {
                sortLabels.forEachIndexed { index, label ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (sortMode == index) {
                                    Icon(Icons.Rounded.Check, null, modifier = Modifier.size(16.dp), tint = adaptiveColor)
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(label)
                            }
                        },
                        onClick = {
                            onSortModeChange(index)
                            showSortMenu = false
                        }
                    )
                }
            }
        }

        // Smart playlist button + dropdown
        Box {
            IconButton(
                onClick = { showSmartPlaylistMenu = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Rounded.FlashOn, "Smart Playlist",
                    tint = if (selectedSmartPlaylist >= 0) adaptiveColor else Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }
            DropdownMenu(
                expanded = showSmartPlaylistMenu,
                onDismissRequest = { showSmartPlaylistMenu = false },
                shape = RoundedCornerShape(24.dp)
            ) {
                DropdownMenuItem(
                    text = { Text("Most Played ($mostPlayedCount)") },
                    onClick = {
                        onSmartPlaylistChange(if (selectedSmartPlaylist == 0) -1 else 0)
                        showSmartPlaylistMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            if (selectedSmartPlaylist == 0) Icons.Rounded.Check else Icons.Rounded.TrendingUp,
                            null, tint = adaptiveColor
                        )
                    }
                )
                DropdownMenuItem(
                    text = { Text("Recent ($recentlyAddedCount)") },
                    onClick = {
                        onSmartPlaylistChange(if (selectedSmartPlaylist == 1) -1 else 1)
                        showSmartPlaylistMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            if (selectedSmartPlaylist == 1) Icons.Rounded.Check else Icons.Rounded.History,
                            null, tint = adaptiveColor
                        )
                    }
                )
                DropdownMenuItem(
                    text = { Text("Never Played ($neverPlayedCount)") },
                    onClick = {
                        onSmartPlaylistChange(if (selectedSmartPlaylist == 2) -1 else 2)
                        showSmartPlaylistMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            if (selectedSmartPlaylist == 2) Icons.Rounded.Check else Icons.Rounded.Block,
                            null, tint = adaptiveColor
                        )
                    }
                )
            }
        }
    }
}
