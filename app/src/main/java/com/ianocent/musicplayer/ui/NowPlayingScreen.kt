package com.ianocent.musicplayer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ianocent.musicplayer.viewmodel.MusicViewModel
import java.util.concurrent.TimeUnit
import kotlin.collections.indexOf
import androidx.compose.runtime.getValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.media3.common.Player
import kotlinx.coroutines.launch

@Composable
fun NowPlayingScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit
) {
    val song by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val albumArt by viewModel.albumArt.collectAsState()
    val isShuffleOn by viewModel.isShuffleOn.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val ambientColor by viewModel.ambientColor.collectAsState()
//    var offsetY by remember { mutableStateOf(0f) }
    val offsetY = remember { Animatable(0f) }
    val dismissThreshold = 300f
    val coroutineScope = rememberCoroutineScope()
    val screenHeight = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val dragProgress = (offsetY.value / screenHeight).coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(0, offsetY.value.roundToInt()) }
            .graphicsLayer {
                scaleX = 1f - dragProgress * 0.05f
                scaleY = 1f - dragProgress * 0.05f
            }
            .statusBarsPadding()
            .navigationBarsPadding()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        coroutineScope.launch {
                            if (offsetY.value > dismissThreshold) {
                                offsetY.animateTo(screenHeight, animationSpec = spring())
                                onBack()
                            } else {
                                offsetY.animateTo(0f, animationSpec = spring())
                            }
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        coroutineScope.launch {
                            offsetY.snapTo((offsetY.value + dragAmount).coerceAtLeast(0f))
                        }
                    }
                )
            }
    ) {
    // Drag handle
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(40.dp)
                .height(4.dp)
                .background(Color.LightGray, RoundedCornerShape(2.dp))
                .clickable { onBack() }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Album art + song info
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .background(
                        if (albumArt == null)
                            Brush.linearGradient(listOf(Color(0xFF8B1E1E), Color(0xFF2B0A0A)))
                        else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (albumArt != null) {
                    Image(
                        bitmap = albumArt!!.asImageBitmap(),
                        contentDescription = "Album Art",
                        modifier = Modifier.fillMaxSize()
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = formatTime(currentPosition),
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text("Song by :", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Text(
                    song?.artist ?: "Unknown Artist",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    song?.title ?: "No song playing",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f },
                    modifier = Modifier.fillMaxWidth().height(6.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(3.dp)),
                    color = Color(0xFFD32F2F),
                    trackColor = Color(0xFFE0E0E0)
                )
                Text(
                    formatTime(currentPosition),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Lyric section
        val syncedLyric by viewModel.syncedLyric.collectAsState()
        val plainLyric by viewModel.plainLyric.collectAsState()
        val isLyricLoading by viewModel.isLyricLoading.collectAsState()

        Text("Lyric :", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier.fillMaxWidth().height(150.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
        ) {
            when {
                isLyricLoading -> Text("Memuat lirik...", modifier = Modifier.align(Alignment.Center), color = Color.Gray)
                syncedLyric != null -> SyncedLyricView(lines = syncedLyric!!, currentPosition = currentPosition)
                !plainLyric.isNullOrBlank() -> {
                    val scrollState = rememberScrollState()
                    Text(
                        plainLyric!!, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().verticalScroll(scrollState).padding(16.dp)
                    )
                }
                else -> Text("Lirik belum tersedia untuk lagu ini", modifier = Modifier.align(Alignment.Center), color = Color.Gray)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Upnext section
        Text(
            "Upnext :",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))

        val songs by viewModel.songs.collectAsState()
        val upNextList = remember(songs, song) {
            val idx = songs.indexOf(song)
            if (idx == -1) songs.take(3) else songs.drop(idx + 1).take(3)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(
                    Color(0xFF1E1E1E),
                    androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                )
                .padding(12.dp)
        ) {
            LazyColumn {
                items(upNextList) { upSong ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.playSong(upSong) }
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        ) {
                            Text(
                                upSong.title,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Text(
                                upSong.artist,
                                color = Color.Gray,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            formatTime(upSong.duration),
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Controls
        Text(
            "Controls :",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))

        val isDarkMode by viewModel.isDarkMode.collectAsState()
        val adaptiveColor = remember(ambientColor, isDarkMode) {
            com.ianocent.musicplayer.data.getAdaptiveControlColor(ambientColor, isDarkMode)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(adaptiveColor, adaptiveColor.copy(alpha = 0.7f))
                    )
                )
                .padding(vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val buttonBg = if (isDarkMode) Color.Black.copy(alpha = 0.3f) else Color.White
                val iconColor = if (isDarkMode) Color.White else Color.Black

                ControlButton(
                    icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    onClick = { viewModel.togglePlayPause() },
                    bgColor = buttonBg, iconTint = iconColor
                )
                ControlButton(icon = Icons.Default.SkipPrevious, onClick = { viewModel.playPrevious() }, bgColor = buttonBg, iconTint = iconColor)
                ControlButton(icon = Icons.Default.SkipNext, onClick = { viewModel.playNext() }, bgColor = buttonBg, iconTint = iconColor)
                ControlButton(icon = Icons.Default.Shuffle, onClick = { viewModel.toggleShuffle() }, active = isShuffleOn, bgColor = buttonBg, iconTint = iconColor)
                ControlButton(
                    icon = Icons.Default.Repeat,
                    onClick = { viewModel.toggleRepeat() },
                    active = repeatMode != Player.REPEAT_MODE_OFF,
                    badge = if (repeatMode == Player.REPEAT_MODE_ONE) "1" else null,
                    bgColor = buttonBg, iconTint = iconColor
                )
            }
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
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = iconTint,
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 2.dp, end = 2.dp)
            )
        }
    }
}
@Composable
fun SyncedLyricView(lines: List<com.ianocent.musicplayer.data.LyricLine>, currentPosition: Long) {
    val activeIndex = remember(currentPosition, lines) {
        lines.indexOfLast { it.timeMs <= currentPosition }.coerceAtLeast(0)
    }
    val listState = rememberLazyListState()

    LaunchedEffect(activeIndex) {
        listState.animateScrollToItem((activeIndex - 1).coerceAtLeast(0))
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(vertical = 12.dp)) {
        itemsIndexed(lines) { index, line ->
            Text(
                line.text,
                textAlign = TextAlign.Center,
                fontSize = if (index == activeIndex) 16.sp else 14.sp,
                fontWeight = if (index == activeIndex) FontWeight.Bold else FontWeight.Normal,
                color = if (index == activeIndex) MaterialTheme.colorScheme.primary else Color.Gray,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            )
        }
    }
}

fun formatTime(ms: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return String.format("%02d:%02d", minutes, seconds)
}