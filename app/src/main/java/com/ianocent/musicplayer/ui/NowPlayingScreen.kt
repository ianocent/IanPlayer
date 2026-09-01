package com.ianocent.musicplayer.ui

import com.ianocent.musicplayer.ResponsiveSnapList
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ianocent.musicplayer.player.PlaybackService
import com.ianocent.musicplayer.viewmodel.MusicViewModel
import java.util.concurrent.TimeUnit
import androidx.compose.foundation.rememberScrollState
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.compositeOver
import com.ianocent.musicplayer.data.LyricSource
import com.ianocent.musicplayer.data.Song
import com.ianocent.musicplayer.data.ElementRect
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import kotlin.math.cos
import kotlin.math.sin


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
    val paletteColors by viewModel.paletteColors.collectAsState()
    val offsetY = remember { Animatable(0f) }
    val dismissThreshold = 300f
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val screenHeight = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val screenWidth = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
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
        (18f * (1f - p) + 12f * p).dp
    } else 12.dp

    val handleBack: () -> Unit = {
        if (initialAlbumArtRect != null && heroAnimProgress.value > 0.05f) {
            isExiting = true
            coroutineScope.launch {
                heroAnimProgress.animateTo(0f, animationSpec = tween(300, easing = FastOutSlowInEasing))
            }
        }
        onBack()
    }

    BackHandler(enabled = true) {
        handleBack()
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
    var showWaveRecordSheet by remember { mutableStateOf(false) }
    
    var isLyricExpanded by remember { mutableStateOf(true) }
    var isUpnextExpanded by remember { mutableStateOf(true) }
    
    val lyricWeight by animateFloatAsState(
        targetValue = when {
            isLyricExpanded && isUpnextExpanded -> 0.4f
            isLyricExpanded -> 1f
            else -> 0f
        },
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "lyricWeight"
    )
    val upnextWeight by animateFloatAsState(
        targetValue = when {
            isUpnextExpanded && isLyricExpanded -> 0.6f
            isUpnextExpanded -> 1f
            else -> 0f
        },
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "upnextWeight"
    )
    val fillerWeight by animateFloatAsState(
        targetValue = if (!isLyricExpanded && !isUpnextExpanded) 1f else 0f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "fillerWeight"
    )
    val lyricAlpha by animateFloatAsState(
        targetValue = if (isLyricExpanded) 1f else 0f,
        animationSpec = tween(400),
        label = "lyricAlpha"
    )
    val upnextAlpha by animateFloatAsState(
        targetValue = if (isUpnextExpanded) 1f else 0f,
        animationSpec = tween(400),
        label = "upnextAlpha"
    )
    val sectionSpacerHeight by animateDpAsState(
        targetValue = if (isLyricExpanded && isUpnextExpanded) 12.dp else 0.dp,
        animationSpec = tween(500),
        label = "sectionSpacerHeight"
    )

    val syncedLyric by viewModel.syncedLyric.collectAsState()
    val plainLyric by viewModel.plainLyric.collectAsState()
    val isLyricLoading by viewModel.isLyricLoading.collectAsState()
    val rootBackgroundColor = remember(animatedAmbient, isDarkMode) {
        if (isDarkMode) {
            Color(ColorUtils.blendARGB(animatedAmbient.toArgb(), android.graphics.Color.BLACK, 0.85f))
        } else {
            Color(ColorUtils.blendARGB(animatedAmbient.toArgb(), android.graphics.Color.WHITE, 0.75f))
        }
    }

    val targetPaletteBlend = remember(paletteColors, isDarkMode) {
        val base = paletteColors.ifEmpty { listOf(animatedAmbient) }
        List(3) { i ->
            val c = base.getOrElse(i) { base.last() }
            if (isDarkMode) Color(ColorUtils.blendARGB(c.toArgb(), android.graphics.Color.BLACK, 0.8f))
            else Color(ColorUtils.blendARGB(c.toArgb(), android.graphics.Color.WHITE, 0.65f))
        }
    }

    val paletteTransitionProgress = remember { Animatable(0f) }
    var prevPaletteBlend by remember { mutableStateOf(targetPaletteBlend) }
    var nextPaletteBlend by remember { mutableStateOf(targetPaletteBlend) }

    LaunchedEffect(targetPaletteBlend) {
        prevPaletteBlend = List(3) { i ->
            androidx.compose.ui.graphics.lerp(
                prevPaletteBlend[i], nextPaletteBlend[i], paletteTransitionProgress.value
            )
        }
        nextPaletteBlend = targetPaletteBlend
        paletteTransitionProgress.snapTo(0f)
        paletteTransitionProgress.animateTo(1f, tween(1400, easing = FastOutSlowInEasing))
    }

    val currentSlot0 = androidx.compose.ui.graphics.lerp(prevPaletteBlend[0], nextPaletteBlend[0], paletteTransitionProgress.value)
    val currentSlot1 = androidx.compose.ui.graphics.lerp(prevPaletteBlend[1], nextPaletteBlend[1], paletteTransitionProgress.value)
    val currentSlot2 = androidx.compose.ui.graphics.lerp(prevPaletteBlend[2], nextPaletteBlend[2], paletteTransitionProgress.value)

    val gradientTransition = rememberInfiniteTransition(label = "ambientGradient")
    val gradientAngle by gradientTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 30000, easing = LinearEasing)
        ),
        label = "gradientAngle"
    )

    val animatedBackgroundBrush = remember(
        currentSlot0, currentSlot1, currentSlot2,
        rootBackgroundColor, gradientAngle, screenWidth, screenHeight
    ) {
        val rad = Math.toRadians(gradientAngle.toDouble())
        val dx = (cos(rad).toFloat()) * screenWidth
        val dy = (sin(rad).toFloat()) * screenHeight
        val cx = screenWidth / 2f
        val cy = screenHeight / 2f
        Brush.linearGradient(
            colors = listOf(
                rootBackgroundColor,
                currentSlot0,
                currentSlot1,
                currentSlot2,
                rootBackgroundColor
            ),
            start = Offset(cx - dx, cy - dy),
            end = Offset(cx + dx, cy + dy)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(0, offsetY.value.roundToInt()) }
            .graphicsLayer {
                scaleX = 1f - dragProgress * 0.05f
                scaleY = 1f - dragProgress * 0.05f
            }
            .background(animatedBackgroundBrush)
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

        // Album art + song info
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
                        rotationZ = 0f
                        val p = heroAnimProgress.value
                        if (initialAlbumArtRect != null) {
                            scaleX = heroInitScale + (1f - heroInitScale) * p
                            scaleY = heroInitScale + (1f - heroInitScale) * p
                            translationX = heroInitOffsetX * (1f - p)
                            translationY = heroInitOffsetY * (1f - p)
                        }
                        clip = true
                        shape = RoundedCornerShape(heroCornerRadius)
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Song by :", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    song?.let { s ->
                        val isFav by viewModel.favoriteIds.collectAsState()
                        val liked = isFav.contains(s.id)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { viewModel.toggleFavorite(s.id) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                    contentDescription = if (liked) "Unlike" else "Like",
                                    tint = if (liked) Color(0xFFE91E63) else Color.Gray,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            IconButton(
                                onClick = { showWaveRecordSheet = true },
                                modifier = Modifier.size(28.dp)
                            ) {
                                androidx.compose.foundation.Canvas(
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    val strokeWidth = 2.dp.toPx()
                                    val center = Offset(size.width / 2, size.height / 2)
                                    val radius = (size.width - strokeWidth) / 2
                                    // Circle outline
                                    drawCircle(
                                        color = Color(0xFFE91E63),
                                        radius = radius,
                                        center = center,
                                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                    )
                                    // Center dot
                                    drawCircle(
                                        color = Color(0xFFE91E63),
                                        radius = radius * 0.35f,
                                        center = center
                                    )
                                }
                            }
                        }
                    }
                }
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

        // Lyric & Upnext
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
                    .clickable { isLyricExpanded = !isLyricExpanded }
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
                    Spacer(modifier = Modifier.width(4.dp))
                    val lyricSource by viewModel.lyricSource.collectAsState()
                    IconButton(
                        onClick = { song?.let { viewModel.cycleLyricSource(it) } },
                        modifier = Modifier.size(24.dp),
                        enabled = song != null && !isLyricLoading
                    ) {
                        Icon(Icons.Rounded.SwapHoriz, "Ganti Sumber Lirik",
                            tint = if (isLyricLoading) Color.Gray.copy(alpha = 0.5f) else adaptiveColor)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    if (lyricSource != LyricSource.UNKNOWN) {
                        Text(
                            lyricSource.name.replace("_", " "),
                            style = MaterialTheme.typography.labelSmall,
                            color = adaptiveColor.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .weight(lyricWeight.coerceAtLeast(0.001f))
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = lyricAlpha
                        clip = true
                    }
            ) {
                if (lyricWeight > 0.02f) {
                    Box(modifier = Modifier.fillMaxSize()) {
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
            }

            Spacer(modifier = Modifier.height(sectionSpacerHeight))

            // Upnext toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { isUpnextExpanded = !isUpnextExpanded }
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
                    upNextSongs.add(to.index, upNextSongs.removeAt(from.index))
                    viewModel.reorderUpNext(from.index, to.index)
                }
            )

            Box(
                modifier = Modifier
                    .weight(upnextWeight.coerceAtLeast(0.001f))
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = upnextAlpha
                        clip = true
                    }
            ) {
                if (upnextWeight > 0.02f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                adaptiveColor.copy(alpha = if (isDarkMode) 0.08f else 0.05f)
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
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .zIndex(if (isDragging) 1f else 0f)
                                        .shadow(elevation, RoundedCornerShape(12.dp))
                                        .padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                                            alpha = 0.3f
                                        )
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .padding(start = 8.dp)
                                                .size(24.dp)
                                                .clip(CircleShape)
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
            }

            if (fillerWeight > 0.01f) {
                Spacer(modifier = Modifier.weight(fillerWeight))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Controls
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
                    bgColor = buttonBg, iconTint = iconColor
                )
            }
        }

        val audioSessionId by viewModel.audioSessionId.collectAsState()

        if (showWaveRecordSheet) {
            WaveRecordSheet(
                song = song,
                syncedLyric = syncedLyric,
                plainLyric = plainLyric,
                currentPositionValue = currentPosition,
                currentPosition = { viewModel.getLivePosition() },
                albumArt = albumArt,
                accentColor = adaptiveColor,
                isPlaying = isPlaying,
                audioSessionId = audioSessionId,
                onDismiss = { showWaveRecordSheet = false }
            )
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
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
                overflow = TextOverflow.Ellipsis
            )
            Text(
                upSong.artist,
                color = if (isDarkMode) Color.Gray else Color.Gray,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
            listState.animateScrollToItem(activeIndex)
        }
    }

    ResponsiveSnapList(
        items = lines,
        key = { it.timeMs },
        scrollbarColor = highlightColor,
        listState = listState,
        minItemHeight = 52.dp,
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
