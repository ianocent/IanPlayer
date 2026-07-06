package com.ianocent.musicplayer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.media3.common.Player
import kotlinx.coroutines.launch
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.compositeOver
import com.ianocent.musicplayer.data.Song
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ReceiptLong

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
    val offsetY = remember { Animatable(0f) }
    val dismissThreshold = 300f
    val coroutineScope = rememberCoroutineScope()
    val screenHeight = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val dragProgress = (offsetY.value / screenHeight).coerceIn(0f, 1f)
    val animatedAmbient by animateColorAsState(
        targetValue = ambientColor,
        animationSpec = tween(durationMillis = 800)
    )
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val adaptiveColor = remember(ambientColor, isDarkMode) {
        com.ianocent.musicplayer.data.getAdaptiveControlColor(ambientColor, isDarkMode)
    }
    var selectedLyricLines by remember { mutableStateOf(setOf<Int>()) }
    var showLyricCardSheet by remember { mutableStateOf(false) }
    var isLyricExpanded by remember { mutableStateOf(true) }
    var isUpnextExpanded by remember { mutableStateOf(true) }
    val syncedLyric by viewModel.syncedLyric.collectAsState()
    val plainLyric by viewModel.plainLyric.collectAsState()
    val isLyricLoading by viewModel.isLyricLoading.collectAsState()
    val rootBackgroundColor = remember(animatedAmbient, isDarkMode) {
        if (isDarkMode) {
            Color(ColorUtils.blendARGB(animatedAmbient.toArgb(), android.graphics.Color.BLACK, 0.6f))
        } else {
            Color(ColorUtils.blendARGB(animatedAmbient.toArgb(), android.graphics.Color.WHITE, 0.75f))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(0, offsetY.value.roundToInt()) }
            .graphicsLayer {
                scaleX = 1f - dragProgress * 0.05f
                scaleY = 1f - dragProgress * 0.05f
            }
            .background(rootBackgroundColor)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
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

        // Album art + song info (Persis Figma)
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(RoundedCornerShape(24.dp)) // Di Figma kelihatan cukup membulat (radius agak besar)
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
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(24.dp)),
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
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    song?.title ?: "No song playing",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Slider(
                        value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                        onValueChange = { fraction -> viewModel.seekTo((fraction * duration).toLong()) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .scale(scaleX = 1f, scaleY = 0.6f),
                        colors = SliderDefaults.colors(
                            thumbColor = adaptiveColor,
                            activeTrackColor = adaptiveColor,
                            inactiveTrackColor = adaptiveColor.copy(alpha = 0.25f)
                        )
                    )
                }
                Text(
                    formatTime(currentPosition),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Lyric section (Persis Figma: Tanpa background abu2, icon kecil di kanan)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isLyricExpanded = !isLyricExpanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Lyric :", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { if (selectedLyricLines.isNotEmpty()) showLyricCardSheet = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ReceiptLong,
                        contentDescription = "Buat Kartu Lirik",
                        tint = if (selectedLyricLines.isNotEmpty()) adaptiveColor else Color.Gray.copy(alpha = 0.5f)
                    )
                }
                Icon(
                    imageVector = if (isLyricExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = "Toggle Lyric",
                    tint = Color.Gray
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        AnimatedVisibility(
            visible = isLyricExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
            when {
                isLyricLoading -> Text("Memuat lirik...", modifier = Modifier.align(Alignment.Center), color = Color.Gray)
                syncedLyric != null -> SyncedLyricView(
                    lines = syncedLyric!!,
                    currentPosition = currentPosition,
                    highlightColor = adaptiveColor,
                    selectedIndices = selectedLyricLines,
                    onLineClick = { index ->
                        selectedLyricLines = if (selectedLyricLines.contains(index)) {
                            selectedLyricLines - index
                        } else {
                            selectedLyricLines + index
                        }
                    }
                )
                !plainLyric.isNullOrBlank() -> {
                    val scrollState = rememberScrollState()
                    Text(
                        plainLyric!!, textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 16.dp)
                    )
                }
                else -> Text("Lirik belum tersedia", modifier = Modifier.align(Alignment.Center), color = Color.Gray)
            }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Upnext section (Persis Figma: Ada image album art kecil)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isUpnextExpanded = !isUpnextExpanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Upnext :", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Icon(
                imageVector = if (isUpnextExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = "Toggle Upnext",
                tint = Color.Gray
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        val songs by viewModel.queue.collectAsState()
        val upNextList = remember(songs, song) {
            val idx = songs.indexOf(song)
            if (idx == -1) songs else songs.drop(idx + 1)
        }

        AnimatedVisibility(
            visible = isUpnextExpanded,
            modifier = Modifier.weight(1f),
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .background(
                        adaptiveColor.copy(alpha = if (isDarkMode) 0.15f else 0.12f).compositeOver(
                            if (isDarkMode) Color(0xFF121212) else Color.White
                        ),
                        RoundedCornerShape(24.dp)
                    )
            ) {
                LazyColumn(
                    modifier = Modifier.padding(12.dp),
                    contentPadding = PaddingValues(bottom = 60.dp)
                ) {
                    items(upNextList) { upSong ->
                        UpnextSongRow(upSong = upSong, viewModel = viewModel, isDarkMode = isDarkMode) {
                            viewModel.playSong(upSong)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Controls (Persis Figma)
        Text("Controls :", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp)) // Radius lebih besar ala figma
                .background(
                    Brush.verticalGradient(
                        listOf(adaptiveColor, adaptiveColor.copy(alpha = 0.5f)) // Gradient adaptif lu
                    )
                )
                .padding(vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val buttonBg = if (isDarkMode) Color.Black.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.8f)
                val iconColor = if (isDarkMode) Color.White else Color.Black

                ControlButton(
                    icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    onClick = { viewModel.togglePlayPause() },
                    bgColor = buttonBg, iconTint = iconColor
                )
                ControlButton(icon = Icons.Rounded.SkipPrevious, onClick = { viewModel.playPrevious() }, bgColor = buttonBg, iconTint = iconColor)
                ControlButton(icon = Icons.Rounded.SkipNext, onClick = { viewModel.playNext() }, bgColor = buttonBg, iconTint = iconColor)
                ControlButton(icon = Icons.Rounded.Shuffle, onClick = { viewModel.toggleShuffle() }, active = isShuffleOn, bgColor = buttonBg, iconTint = iconColor)
                ControlButton(
                    icon = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    onClick = { viewModel.toggleRepeat() },
                    active = repeatMode != Player.REPEAT_MODE_OFF,
                    badge = null,
                    bgColor = buttonBg, iconTint = iconColor
                )
            }
        }
    }

    if (showLyricCardSheet) {
        val selectedText = selectedLyricLines.sorted()
            .mapNotNull { syncedLyric?.getOrNull(it)?.text }
            .joinToString("\n")

        LyricCardSheet(
            song = song,
            lyricText = selectedText,
            albumArt = albumArt,
            accentColor = adaptiveColor,
            onDismiss = {
                showLyricCardSheet = false
                selectedLyricLines = emptySet()
            }
        )
    }
}

@Composable
fun UpnextSongRow(upSong: Song, viewModel: MusicViewModel, isDarkMode: Boolean, onClick: () -> Unit) {
    var art by remember(upSong.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(upSong.id) {
        viewModel.getCachedArt(upSong) { bitmap -> art = bitmap?.asImageBitmap() }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Album art kecil ala Figma
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isDarkMode) Color.DarkGray else Color.LightGray),
            contentAlignment = Alignment.Center
        ) {
            if (art != null) {
                Image(bitmap = art!!, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.Gray)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                upSong.title,
                color = if (isDarkMode) Color.White else Color.Black,
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
            color = if (isDarkMode) Color.White else Color.Black,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1
        )
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

@Composable
fun SyncedLyricView(
    lines: List<com.ianocent.musicplayer.data.LyricLine>,
    currentPosition: Long,
    highlightColor: Color,
    selectedIndices: Set<Int>,
    onLineClick: (Int) -> Unit
) {
    val activeIndex = remember(currentPosition, lines) {
        lines.indexOfLast { it.timeMs <= currentPosition }.coerceAtLeast(0)
    }
    val listState = rememberLazyListState()

    LaunchedEffect(activeIndex) {
        if (selectedIndices.isEmpty()) {
            listState.animateScrollToItem((activeIndex - 1).coerceAtLeast(0))
        }
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(lines) { index, line ->
            val isSelected = selectedIndices.contains(index)
            Text(
                line.text,
                textAlign = TextAlign.Center,
                fontSize = if (index == activeIndex) 16.sp else 14.sp,
                fontWeight = if (index == activeIndex) FontWeight.Bold else FontWeight.Normal,
                color = if (index == activeIndex) highlightColor else Color.Gray,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isSelected) highlightColor.copy(alpha = 0.2f) else Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { onLineClick(index) }
                    .padding(vertical = 6.dp, horizontal = 8.dp)
            )
        }
    }
}

fun formatTime(ms: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return String.format("%02d:%02d", minutes, seconds)
}