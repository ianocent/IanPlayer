package com.ianocent.musicplayer.ui

import com.ianocent.musicplayer.ResponsiveSnapList
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.style.TextOverflow
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
import com.ianocent.musicplayer.data.ElementRect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.animation.core.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.material.icons.rounded.DragHandle
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex

@Composable
fun NowPlayingScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit,
    initialAlbumArtRect: ElementRect? = null
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
    val isBuffering by viewModel.isBuffering.collectAsState()

    val heroAnimProgress = remember { Animatable(0f) }
    var targetAlbumArtRect by remember { mutableStateOf<ElementRect?>(null) }
    var heroInitScale by remember { mutableStateOf(1f) }
    var heroInitOffsetX by remember { mutableStateOf(0f) }
    var heroInitOffsetY by remember { mutableStateOf(0f) }
    var isExiting by remember { mutableStateOf(false) }

    LaunchedEffect(initialAlbumArtRect, targetAlbumArtRect) {
        val from = initialAlbumArtRect ?: return@LaunchedEffect
        val to = targetAlbumArtRect ?: return@LaunchedEffect

        heroInitScale = (from.size.width / to.size.width).coerceIn(0.15f, 1f)
        heroInitOffsetX = from.center.x - to.center.x
        heroInitOffsetY = from.center.y - to.center.y

        heroAnimProgress.snapTo(0f)
        heroAnimProgress.animateTo(1f, animationSpec = tween(400, easing = FastOutSlowInEasing))
    }

    val heroCornerRadius = if (initialAlbumArtRect != null) {
        val p = heroAnimProgress.value
        (18f * (1f - p) + 24f * p).dp
    } else 24.dp

    val handleBack: () -> Unit = {
        if (initialAlbumArtRect != null && heroAnimProgress.value > 0.05f) {
            isExiting = true
            coroutineScope.launch {
                heroAnimProgress.animateTo(0f, animationSpec = tween(300, easing = FastOutSlowInEasing))
            }
        }
        onBack()
    }

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
    val lyricWeight by animateFloatAsState(
        targetValue = if (isLyricExpanded) (if (isUpnextExpanded) 0.4f else 1f) else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "lyricWeight"
    )
    val upnextWeight by animateFloatAsState(
        targetValue = if (isUpnextExpanded) (if (isLyricExpanded) 0.6f else 1f) else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "upnextWeight"
    )
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
                        if (offsetY.value > dismissThreshold) {
                            // KUNCI FIX GLITCH: nol-in paksa offset drag detik itu juga,
                            // biarin heroAnimProgress murni ngurusin animasi keluar tanpa ditimpa offsetY
                            coroutineScope.launch { offsetY.snapTo(0f) }
                            handleBack()
                        } else {
                            coroutineScope.launch {
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
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Drag handle
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(40.dp)
                .height(4.dp)
                .background(Color.LightGray, RoundedCornerShape(2.dp))
                .clickable { handleBack() }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Album art + song info (sesuai Figma: art kecil kiri, info + progress bar kanan)
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .onGloballyPositioned { coords ->
                        val pos = coords.positionInWindow()
                        val sz = coords.size
                        targetAlbumArtRect = ElementRect(
                            center = Offset(pos.x + sz.width / 2f, pos.y + sz.height / 2f),
                            size = androidx.compose.ui.geometry.Size(sz.width.toFloat(), sz.height.toFloat())
                        )
                    }
                    .graphicsLayer {
                        if (initialAlbumArtRect != null && heroAnimProgress.value < 0.99f) {
                            val p = heroAnimProgress.value
                            scaleX = heroInitScale + (1f - heroInitScale) * p
                            scaleY = heroInitScale + (1f - heroInitScale) * p
                            translationX = heroInitOffsetX * (1f - p)
                            translationY = heroInitOffsetY * (1f - p)
                            clip = true
                            shape = RoundedCornerShape(heroCornerRadius)
                        }
                    }
                    .clip(RoundedCornerShape(heroCornerRadius))
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
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    // Scrim gelap biar timer selalu kebaca di atas album art terang
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
                    val timerMins = remember(currentPosition) {
                        TimeUnit.MILLISECONDS.toMinutes(currentPosition).toString().padStart(2, '0')
                    }
                    val timerSecs = remember(currentPosition) {
                        (TimeUnit.MILLISECONDS.toSeconds(currentPosition) % 60).toString().padStart(2, '0')
                    }
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (isBuffering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp).padding(bottom = 8.dp),
                                color = Color.White,
                                strokeWidth = 3.dp
                            )
                        } else {
                            Text(timerMins, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            Text(timerSecs, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    if (isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = Color.White,
                            strokeWidth = 3.dp
                        )
                    } else {
                        Text(
                            text = formatTime(currentPosition),
                            color = Color.White, fontSize = 22.sp,
                            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text("Song by :", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Text(
                    song?.artist ?: "Unknown Artist",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    song?.title ?: "No song playing",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth().height(24.dp), contentAlignment = Alignment.Center) {
                    Slider(
                        value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                        onValueChange = { fraction -> viewModel.seekTo((fraction * duration).toLong()) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = adaptiveColor,
                            activeTrackColor = adaptiveColor,
                            inactiveTrackColor = adaptiveColor.copy(alpha = 0.25f)
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Lyric & Upnext dibungkus scrollable + weight(fill=true) biar section ini SELALU
        // ngisi semua sisa ruang yang ada -> Controls jadi ke-anchor konsisten di posisi yang sama
        // (ga "ngambang"/naik-turun tiap Lyric-Upnext di-collapse/expand), dan tetep aman dari
        // kepotong karena discroll sendiri kalau kontennya kepanjangan.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // Lyric toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        isLyricExpanded = !isLyricExpanded
                    }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Lyric :", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isLyricExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = "Toggle Lyric", tint = Color.Gray,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    IconButton(
                        onClick = { if (selectedLyricLines.isNotEmpty()) showLyricCardSheet = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Rounded.ReceiptLong, "Buat Kartu Lirik",
                            tint = if (selectedLyricLines.isNotEmpty()) adaptiveColor else Color.Gray.copy(alpha = 0.5f))
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))

            AnimatedVisibility(
                visible = isLyricExpanded,
                modifier = Modifier.weight(lyricWeight.coerceAtLeast(0.001f)),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Box(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                    when {
                        isLyricLoading -> SkeletonLyricLoader(adaptiveColor = adaptiveColor)
                        !syncedLyric.isNullOrEmpty() -> SyncedLyricView(
                            lines = syncedLyric!!,
                            currentPosition = currentPosition,
                            highlightColor = adaptiveColor,
                            selectedIndices = selectedLyricLines,
                            onLineClick = { index ->
                                selectedLyricLines = if (selectedLyricLines.contains(index))
                                    selectedLyricLines - index else selectedLyricLines + index
                            }
                        )
                        !plainLyric.isNullOrBlank() -> {
                            val lyricScrollState = rememberScrollState()
                            Text(plainLyric!!, textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().verticalScroll(lyricScrollState)
                                    .padding(horizontal = 16.dp).padding(bottom = 24.dp))
                        }
                        else -> Text("Lirik belum tersedia", modifier = Modifier.align(Alignment.Center), color = Color.Gray)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Upnext toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        isUpnextExpanded = !isUpnextExpanded
                    }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Upnext :", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Icon(
                    imageVector = if (isUpnextExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = "Toggle Upnext", tint = Color.Gray
                )
            }
            Spacer(modifier = Modifier.height(6.dp))

            val songs by viewModel.queue.collectAsState()
            val upNextSongs = remember(songs, song) {
                val idx = songs.indexOfFirst { it.id == song?.id }
                (if (idx == -1) songs else songs.drop(idx + 1)).toMutableStateList()
            }
            val upnextListState = rememberLazyListState()
            val upnextReorderableState = rememberReorderableLazyListState(
                lazyListState = upnextListState,
                onMove = { from, to ->
                    // 1. Update UI instan
                    upNextSongs.add(to.index, upNextSongs.removeAt(from.index))
                    // 2. Sync ke ViewModel (queue + ExoPlayer timeline)
                    viewModel.reorderUpNext(from.index, to.index)
                }
            )

            AnimatedVisibility(
                visible = isUpnextExpanded,
                modifier = Modifier.weight(upnextWeight.coerceAtLeast(0.001f)),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .background(
                            adaptiveColor.copy(alpha = if (isDarkMode) 0.15f else 0.12f)
                                .compositeOver(if (isDarkMode) Color(0xFF121212) else Color.White),
                            RoundedCornerShape(24.dp)
                        )
                ) {
                    ResponsiveSnapList(
                        items = upNextSongs,
                        key = { it.id },
                        scrollbarColor = adaptiveColor,
                        modifier = Modifier.padding(12.dp),
                        listState = upnextListState,
                        minItemHeight = 64.dp,
                        bottomPadding = 4.dp
                    ) { upSong, _ ->
                        ReorderableItem(upnextReorderableState, key = upSong.id) { isDragging ->
                            val elevation by animateDpAsState(
                                if (isDragging) 8.dp else 0.dp,
                                label = "upnext_drag_elevation"
                            )
                            // Warna solid biar item yang lagi di-drag beneran nutupin item di bawahnya,
                            // bukan cuma numpuk transparan (itu penyebab visual "tembus-tembusan" pas drag).
                            val rowBg = if (isDarkMode) Color(0xFF1E1E1E) else Color.White

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .zIndex(if (isDragging) 1f else 0f)
                                    .shadow(elevation, RoundedCornerShape(12.dp))
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(rowBg),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .size(24.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(adaptiveColor.copy(alpha = 0.2f))
                                        .draggableHandle(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.DragHandle,
                                        contentDescription = "Drag",
                                        tint = adaptiveColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                UpnextSongRow(
                                    upSong = upSong,
                                    viewModel = viewModel,
                                    isDarkMode = isDarkMode,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    viewModel.playSong(upSong)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Controls - SELALU tampil
        Text(
            text = "Controls :",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(adaptiveColor, adaptiveColor.copy(alpha = 0.5f))
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
fun UpnextSongRow(
    upSong: Song,
    viewModel: MusicViewModel,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var art by remember(upSong.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(upSong.id) {
        viewModel.getCachedArt(upSong) { bitmap -> art = bitmap?.asImageBitmap() }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 12.dp),
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
                Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.Gray)
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
        val mins = remember(upSong.duration) { TimeUnit.MILLISECONDS.toMinutes(upSong.duration).toString().padStart(2, '0') }
        val secs = remember(upSong.duration) { (TimeUnit.MILLISECONDS.toSeconds(upSong.duration) % 60).toString().padStart(2, '0') }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(mins, color = if (isDarkMode) Color.White else Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold, lineHeight = 12.sp)
            Text(secs, color = if (isDarkMode) Color.White else Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold, lineHeight = 12.sp)
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
            // Scroll agar baris aktif berada di paling atas area yang terlihat,
            // sehingga baris-baris lirik selanjutnya (upcoming) terlihat lebih banyak di bawahnya.
            listState.animateScrollToItem(activeIndex)
        }
    }

    // Menggunakan ResponsiveSnapList agar tinggi lirik dibagi rata sesuai sisa ruang
    // dan tidak ada baris yang "kepotong" setengah di bagian bawah/atas.
    ResponsiveSnapList(
        items = lines,
        key = { it.timeMs },
        scrollbarColor = highlightColor,
        listState = listState,
        minItemHeight = 52.dp, // Balikin ke jarak aman biar ga kepotong ("...") pas 2 baris
        bottomPadding = 8.dp
    ) { line, itemHeight ->
        val index = lines.indexOf(line)
        val isSelected = selectedIndices.contains(index)
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .padding(horizontal = 12.dp, vertical = 2.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isSelected) highlightColor.copy(alpha = 0.2f) else Color.Transparent)
                .clickable { onLineClick(index) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                line.text,
                textAlign = TextAlign.Center,
                fontSize = if (index == activeIndex) 16.sp else 14.sp,
                fontWeight = if (index == activeIndex) FontWeight.Bold else FontWeight.Normal,
                color = if (index == activeIndex) highlightColor else Color.Gray,
                maxLines = 2,
                lineHeight = 18.sp
            )
        }
    }
}

fun formatTime(ms: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return String.format("%02d:%02d", minutes, seconds)
}

@Composable
fun SkeletonLyricLoader(adaptiveColor: Color) {
    val shimmerColors = listOf(
        adaptiveColor.copy(alpha = 0.1f),
        adaptiveColor.copy(alpha = 0.3f),
        adaptiveColor.copy(alpha = 0.1f)
    )

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(x = translateAnim, y = translateAnim)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Pola lebar fake lyric biar keliatan natural (60%, 80%, 90%, 70%, 50%)
        val widths = listOf(0.6f, 0.8f, 0.9f, 0.7f, 0.5f)
        widths.forEach { fraction ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(14.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(brush)
            )
        }
    }
}
