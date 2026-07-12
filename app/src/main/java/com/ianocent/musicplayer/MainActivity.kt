package com.ianocent.musicplayer

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.ianocent.musicplayer.data.Song
import com.ianocent.musicplayer.data.ElementRect
import com.ianocent.musicplayer.ui.NowPlayingScreen
import com.ianocent.musicplayer.ui.theme.IanPlayerTheme
import com.ianocent.musicplayer.viewmodel.MusicViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.util.concurrent.TimeUnit
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.compositeOver
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MusicViewModel = viewModel()
            val systemUiController = rememberSystemUiController()
            val isDarkMode by viewModel.isDarkMode.collectAsState()

            LaunchedEffect(isDarkMode) {
                systemUiController.setSystemBarsColor(
                    color = Color.Transparent,
                    darkIcons = !isDarkMode
                )
            }

            IanPlayerTheme(darkTheme = isDarkMode, dynamicColor = false) {
                val animatedScaffoldBg by animateColorAsState(
                    targetValue = MaterialTheme.colorScheme.background,
                    animationSpec = tween(500),
                    label = "scaffoldBgAnim"
                )

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = animatedScaffoldBg
                ) { innerPadding ->
                    AppNavHost(viewModel = viewModel, innerPadding = innerPadding)
    }
}
}
    }
}

@Composable
fun FastScroller(
    listState: androidx.compose.foundation.lazy.LazyListState,
    letters: List<String>,
    color: Color,
    modifier: Modifier = Modifier
) {
    if (listState.layoutInfo.totalItemsCount <= 0 || letters.isEmpty()) return

    var isDragging by remember { mutableStateOf(false) }
    var tooltipLetter by remember { mutableStateOf("") }
    var containerHeightPx by remember { mutableStateOf(0f) }
    val coroutineScope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current

    val scrollFraction by remember {
        derivedStateOf {
            val idx = listState.firstVisibleItemIndex / listState.layoutInfo.totalItemsCount.toFloat()
            idx.coerceIn(0f, 1f)
        }
    }

    val letterFraction by remember(listState) {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            if (total <= 0) 0f
            else listState.firstVisibleItemIndex.toFloat() / total
        }
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(36.dp)
            .onGloballyPositioned { containerHeightPx = it.size.height.toFloat() }
            .pointerInput(letters) {
                detectVerticalDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        if (containerHeightPx > 0) {
                            val fraction = (change.position.y / containerHeightPx).coerceIn(0f, 1f)
                            val letterIdx = (fraction * (letters.size - 1)).toInt().coerceIn(0, letters.size - 1)
                            tooltipLetter = letters[letterIdx]
                            coroutineScope.launch {
                                val targetIdx = listState.layoutInfo.totalItemsCount * fraction.toDouble()
                                listState.scrollToItem(targetIdx.toInt())
                            }
                        }
                    }
                )
            },
        contentAlignment = Alignment.TopCenter
    ) {
        val totalItems = listState.layoutInfo.totalItemsCount
        val visibleCount = listState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
        val thumbFraction = (visibleCount.toFloat() / totalItems).coerceIn(0.06f, 1f)
        val thumbHeightDp = with(density) { (containerHeightPx * thumbFraction).toDp() }
        val offsetYDp = with(density) { (containerHeightPx * letterFraction * (1 - thumbFraction)).toDp() }
        val tooltipOffsetY = with(density) { (-(containerHeightPx / 2f) + containerHeightPx * letterFraction).toDp() }

        Box(
            modifier = Modifier
                .padding(end = 12.dp)
                .offset(y = offsetYDp)
                .width(4.dp)
                .height(thumbHeightDp.coerceAtLeast(32.dp))
                .clip(RoundedCornerShape(2.dp))
                .background(color.copy(alpha = if (isDragging) 0.9f else 0.35f))
        )

        if (isDragging && tooltipLetter.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = (-28).dp, y = tooltipOffsetY)
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    tooltipLetter,
                    fontWeight = FontWeight.Bold,
                    fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * List responsif yang selalu nampilin item secara utuh (ga ada yang kepotong),
 * ngitung tinggi tiap item berdasarkan sisa ruang layar yang tersedia (BoxWithConstraints)
 * dan pake snap fling behavior biar scroll-nya "magnet" ke item terdekat, jadi pas
 * discroll ga ada moment nanggung/kepotong setengah item.
 */
@Composable
fun <T> ResponsiveSnapList(
    items: List<T>,
    key: (T) -> Any,
    scrollbarColor: Color,
    modifier: Modifier = Modifier,
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
    minItemHeight: Dp = 72.dp,
    topPadding: Dp = 0.dp,
    bottomPadding: Dp = 90.dp,
    itemContent: @Composable (T, Dp) -> Unit
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val availableHeight = (maxHeight - topPadding).coerceAtLeast(minItemHeight)
        val itemsPerScreen = (availableHeight / minItemHeight).toInt().coerceAtLeast(1)
        val itemHeight = availableHeight / itemsPerScreen
        val snapBehavior = rememberSnapFlingBehavior(lazyListState = listState)

        // Safety net: kalau sumber data (misalnya hasil search stream) kebetulan
        // ngasih key yang kembar, LazyColumn bakal crash ("Key X was already used").
        // Jadi di-dedupe dulu di sini biar UI ga pernah force close gara-gara itu.
        val dedupedItems = remember(items) {
            val seenKeys = HashSet<Any>()
            items.filter { seenKeys.add(key(it)) }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                flingBehavior = snapBehavior,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding, end = 20.dp)
            ) {
                items(dedupedItems, key = key) { item ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(itemHeight),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        itemContent(item, itemHeight)
                    }
                }
            }
            DraggableScrollbar(listState, scrollbarColor)
        }
    }
}

@Composable
fun AppNavHost(viewModel: MusicViewModel, innerPadding: PaddingValues) {
    var showNowPlaying by remember { mutableStateOf(false) }
    var miniAlbumArtRect by remember { mutableStateOf<ElementRect?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        ListingScreen(
            viewModel = viewModel,
            modifier = Modifier.padding(innerPadding),
            onNowPlayingClick = { showNowPlaying = true },
            onMiniPlayerLayout = { albumRect, _ ->
                miniAlbumArtRect = albumRect
            }
        )

        AnimatedVisibility(
            visible = showNowPlaying,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(250)),
            modifier = Modifier.zIndex(1f)
        ) {
            NowPlayingScreen(
                viewModel = viewModel,
                onBack = { showNowPlaying = false },
                initialAlbumArtRect = miniAlbumArtRect
            )
        }
    }
}

@Composable
fun ListingScreen(
    viewModel: MusicViewModel,
    modifier: Modifier = Modifier,
    onNowPlayingClick: () -> Unit,
    onMiniPlayerLayout: (albumArtRect: ElementRect, progressRect: ElementRect) -> Unit = { _, _ -> }
) {
    var hasPermission by remember { mutableStateOf(false) }

    val permission = if (Build.VERSION.SDK_INT >= 33)
        Manifest.permission.READ_MEDIA_AUDIO
    else
        Manifest.permission.READ_EXTERNAL_STORAGE

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) viewModel.loadSongs()
    }

    LaunchedEffect(Unit) {
        launcher.launch(permission)
    }

    val songs by viewModel.songs.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val ambientColor by viewModel.ambientColor.collectAsState()
    val showRecapBanner by viewModel.showRecapBanner.collectAsState()
    val monthlyRecap by viewModel.monthlyRecap.collectAsState()
    val showRecapCard by viewModel.showRecapCard.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editingPlaylist by remember { mutableStateOf<com.ianocent.musicplayer.data.Playlist?>(null) }
    var selectedPlaylistId by remember { mutableStateOf<Long?>(null) }
    var selectedAlbum by remember { mutableStateOf<String?>(null) }
    val playlists by viewModel.playlists.collectAsState()
    val selectedPlaylist = remember(playlists, selectedPlaylistId) {
        playlists.find { it.id == selectedPlaylistId }
    }
    val isShuffleOn by viewModel.isShuffleOn.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()

    val isUpdateAvailable by viewModel.isUpdateAvailable.collectAsState()
    val updateInfo by viewModel.updateInfo.collectAsState()
    val isDownloading by viewModel.isDownloading.collectAsState()

    val sortMode by viewModel.sortMode.collectAsState()

    val paletteColors by viewModel.paletteColors.collectAsState()
    var controlBgColor by remember { mutableStateOf<Color?>(null) }
    var currentPaletteIndex by remember { mutableStateOf(0) }
    var showVolumeControl by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val audioManager = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager }
    val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
    var currentVolume by remember { mutableStateOf(audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)) }

    val isBuffering by viewModel.isBuffering.collectAsState()

    val adaptiveColor = remember(ambientColor, isDarkMode) {
        com.ianocent.musicplayer.data.getAdaptiveControlColor(ambientColor, isDarkMode)
    }
    val minibarTextColor = remember(adaptiveColor) {
        if (adaptiveColor.luminance() > 0.5f) Color.Black else Color.White
    }

    val filteredSongs = remember(songs, searchQuery) {
        if (searchQuery.isBlank()) songs
        else songs.filter { it.title.contains(searchQuery, true) || it.artist.contains(searchQuery, true) }
    }

    val sortedSongs = remember(filteredSongs, sortMode) {
        viewModel.applySort(filteredSongs)
    }

    if (!hasPermission) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // Animasi perubahan warna background
    val animatedBgColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.background,
        animationSpec = tween(500),
        label = "bgAnim"
    )

    val animatedContainerColor by animateColorAsState(
        targetValue = if (isDarkMode) Color(0xFF1C1C1E) else Color(0xFFF2F2F7),
        animationSpec = tween(500),
        label = "containerAnim"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(animatedBgColor)
    ) {
        // ---------- 1. HEADER ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Animasi Search Bar muncul dari samping
            AnimatedContent(
                targetState = isSearchActive,
                transitionSpec = {
                    (fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.95f))
                        .togetherWith(fadeOut(tween(150)))
                },
                modifier = Modifier.fillMaxWidth(),
                label = "header_transition"
            ) { searching ->
                if (searching) {
                    // Custom Search Bar yang Simetris
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Icon Search dengan background rounded full
                        Box(
                            modifier = Modifier
                                .padding(6.dp)
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(adaptiveColor.copy(alpha = 0.2f)), // Background ngikutin warna dominan
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Search, contentDescription = null, tint = adaptiveColor)
                        }

                        // Input Text Area
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onBackground
                            ),
                            cursorBrush = SolidColor(adaptiveColor),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text("Search songs...", color = Color.Gray, style = MaterialTheme.typography.bodyLarge)
                                }
                                innerTextField()
                            }
                        )

                        // Icon Close
                        IconButton(
                            onClick = { isSearchActive = false; searchQuery = "" },
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.Gray)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Rounded.Search, contentDescription = "Search")
                        }
                        Text(
                            "ıanplayer.",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(0.5f),
                            textAlign = TextAlign.Center
                        )
                        IconButton(onClick = { viewModel.toggleDarkMode() }) {
                            Icon(
                                imageVector = if (isDarkMode) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                contentDescription = "Toggle theme"
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ---------- 2. LISTENING TO PILL ----------
        AnimatedVisibility(
            visible = currentSong != null,
            enter = expandVertically(
                expandFrom = Alignment.Top,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f)
            ) + fadeIn(animationSpec = tween(300)),
            exit = shrinkVertically(
                shrinkTowards = Alignment.Top,
                animationSpec = tween(250)
            ) + fadeOut(animationSpec = tween(200))
        ) {
            currentSong?.let { song ->
                var art by remember(song.id) { mutableStateOf<ImageBitmap?>(null) }
                LaunchedEffect(song.id) {
                    viewModel.getCachedArt(song) { bitmap -> art = bitmap?.asImageBitmap() }
                }

                Box(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .pointerInput(Unit) {
                            var dragSum = 0f
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    if (dragSum > 60f) showVolumeControl = true
                                    else if (dragSum < -60f) showVolumeControl = false
                                    dragSum = 0f
                                }
                            ) { _, dragAmount ->
                                dragSum += dragAmount
                            }
                        }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    AnimatedContent(
                        targetState = showVolumeControl,
                        transitionSpec = {
                            (fadeIn(tween(200)) + slideInHorizontally(tween(200)) { it / 2 })
                                .togetherWith(fadeOut(tween(150)) + slideOutHorizontally(tween(150)) { -it / 2 })
                        },
                        label = "pill_content"
                    ) { showVol ->
                        if (showVol) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.VolumeDown,
                                    contentDescription = "Vol min",
                                    tint = adaptiveColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Slider(
                                    value = currentVolume.toFloat(),
                                    onValueChange = { v ->
                                        currentVolume = v.toInt()
                                        audioManager.setStreamVolume(
                                            android.media.AudioManager.STREAM_MUSIC,
                                            v.toInt(),
                                            0
                                        )
                                    },
                                    valueRange = 0f..maxVolume.toFloat(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 8.dp)
                                        .height(24.dp),
                                    colors = SliderDefaults.colors(
                                        thumbColor = adaptiveColor,
                                        activeTrackColor = adaptiveColor,
                                        inactiveTrackColor = adaptiveColor.copy(alpha = 0.25f)
                                    )
                                )
                                Icon(
                                    Icons.Rounded.VolumeUp,
                                    contentDescription = "Vol max",
                                    tint = adaptiveColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .clickable { onNowPlayingClick() }
                                        .onGloballyPositioned { coords ->
                                            val pos = coords.positionInWindow()
                                            val sz = coords.size
                                            onMiniPlayerLayout(
                                                ElementRect(
                                                    center = androidx.compose.ui.geometry.Offset(
                                                        pos.x + sz.width / 2f,
                                                        pos.y + sz.height / 2f
                                                    ),
                                                    size = androidx.compose.ui.geometry.Size(
                                                        sz.width.toFloat(),
                                                        sz.height.toFloat()
                                                    )
                                                ),
                                                ElementRect(
                                                    center = androidx.compose.ui.geometry.Offset.Zero,
                                                    size = androidx.compose.ui.geometry.Size.Zero
                                                )
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (art != null) {
                                        Image(
                                            bitmap = art!!,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(20.dp))
                                    }
                                    
                                    if (isBuffering) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.3f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                color = Color.White,
                                                strokeWidth = 2.dp
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Listening to: ${song.title}",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val animatedProgress by animateFloatAsState(
                                        targetValue = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                                        animationSpec = tween(durationMillis = 500)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(Color.Gray.copy(alpha = 0.3f))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(adaptiveColor)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                // Timer Vertikal dipojok kanan
                                val minutes = TimeUnit.MILLISECONDS.toMinutes(currentPosition)
                                val seconds = TimeUnit.MILLISECONDS.toSeconds(currentPosition) % 60
                                val minStr = String.format("%02d", minutes)
                                val secStr = String.format("%02d", seconds)

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Text(text = minStr, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    Text(text = secStr, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                    }
                }
            }
        }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ---------- 3. TABS ----------
        var tabPage by remember { mutableStateOf(0) } // 0 untuk halaman pertama, 1 untuk halaman kedua

        AnimatedContent(
            targetState = tabPage,
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally { width -> width } + fadeIn()).togetherWith(slideOutHorizontally { width -> -width } + fadeOut())
                } else {
                    (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(slideOutHorizontally { width -> width } + fadeOut())
                }
            },
            label = "tab_paging_animation"
        ) { page ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (page == 0) {
                    TabItem("Songs", selectedTab == 0, adaptiveColor) { selectedTab = 0 }
                    TabItem("Albums", selectedTab == 1, adaptiveColor) { selectedTab = 1 }

                    // Tombol Next (>)
                    IconButton(
                        onClick = {
                            tabPage = 1
                            selectedTab = 2 // <-- Otomatis aktifin tab Stream
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Rounded.ChevronRight, contentDescription = "More Tabs", tint = Color.Gray)
                    }
                } else {
                    // Tombol Prev (<)
                    IconButton(
                        onClick = {
                            tabPage = 0
                            selectedTab = 0 // <-- Otomatis balik ke tab Songs
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous Tabs", tint = Color.Gray)
                    }

                    TabItem("Stream", selectedTab == 2, adaptiveColor) { selectedTab = 2 }
                    TabItem("Playlists", selectedTab == 3, adaptiveColor) { selectedTab = 3 }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val streamSongs by viewModel.streamSongs.collectAsState()
        val isSearchingRemote by viewModel.isSearchingRemote.collectAsState()
        LaunchedEffect(searchQuery, selectedTab) {
            if (selectedTab == 2) {
                viewModel.searchRemoteSongs(searchQuery)
            }
        }

        // ---------- 4. MAIN FLOATING LIST CONTAINER ----------
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(animatedContainerColor)
        ) {
            Crossfade(targetState = selectedTab) { tab ->
                when (tab) {
                    0 -> { // SONGS
                        val listState = rememberLazyListState()
                        var showSortMenu by remember { mutableStateOf(false) }
                        val sortLabels = listOf("A - Z", "Recently Added", "Most Played")

                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
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
                                        onDismissRequest = { showSortMenu = false }
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
                                                    viewModel.setSortMode(index)
                                                    showSortMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            AnimatedVisibility(
                                visible = showRecapBanner && monthlyRecap != null,
                                enter = fadeIn() + slideInVertically(),
                                exit = fadeOut() + slideOutVertically()
                            ) {
                                monthlyRecap?.let { recap ->
                                    MusicRecapBanner(
                                        recap = recap,
                                        accentColor = adaptiveColor,
                                        onGenerateCard = { viewModel.openRecapCard() },
                                        onDismiss = { viewModel.dismissRecapBanner() }
                                    )
                                }
                            }

                            ResponsiveSnapList(
                                items = sortedSongs,
                                key = { it.id },
                                scrollbarColor = adaptiveColor,
                                modifier = Modifier.weight(1f),
                                listState = listState
                            ) { song, _ ->
                                SwipeableSongRow(song, viewModel, customOnClick = {
                                    viewModel.setQueue(sortedSongs, startSong = song)
                                }, adaptiveColor = adaptiveColor)
                            }
                        }
                    }

                    1 -> { // ALBUMS
                        val albumGroups = remember(songs) {
                            songs.groupBy { it.album }.entries.toList()
                        }

                        if (selectedAlbum != null) {
                            val albumSongs = albumGroups.find { it.key == selectedAlbum }?.value ?: emptyList()
                            AlbumDetailView(
                                album = selectedAlbum!!,
                                songs = albumSongs,
                                viewModel = viewModel,
                                adaptiveColor = adaptiveColor,
                                minibarTextColor = minibarTextColor,
                                onBack = { selectedAlbum = null },
                                onShuffle = {
                                    if (albumSongs.isNotEmpty()) {
                                        if (!isShuffleOn) viewModel.toggleShuffle()
                                        viewModel.setQueue(albumSongs)
                                    }
                                }
                            )
                        } else {
                            ResponsiveSnapList(
                                items = albumGroups,
                                key = { it.key },
                                scrollbarColor = adaptiveColor,
                                topPadding = 16.dp
                            ) { entry, _ ->
                                AlbumRow(
                                    album = entry.key,
                                    songs = entry.value,
                                    viewModel = viewModel,
                                    count = entry.value.size,
                                    onClick = { selectedAlbum = entry.key }
                                )
                            }
                        }
                    }

                    2 -> { // STREAM
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (isSearchingRemote) {
                                CircularProgressIndicator(
                                    modifier = Modifier.align(Alignment.Center),
                                    color = adaptiveColor
                                )
                            } else if (streamSongs.isEmpty()) {
                                Text(
                                    text = if (searchQuery.isBlank()) "Search songs to stream" else "Not found.",
                                    modifier = Modifier.align(Alignment.Center),
                                    color = Color.Gray
                                )
                            } else {
                                val listState = rememberLazyListState()
                                val shouldLoadMore by remember {
                                    derivedStateOf {
                                        val layoutInfo = listState.layoutInfo
                                        val totalItems = layoutInfo.totalItemsCount
                                        val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                                        totalItems > 0 && lastVisibleItem >= totalItems - 3
                                    }
                                }

                                LaunchedEffect(shouldLoadMore) {
                                    if (shouldLoadMore) viewModel.loadMoreStreamSongs()
                                }

                                ResponsiveSnapList(
                                    items = streamSongs,
                                    key = { it.id },
                                    scrollbarColor = adaptiveColor,
                                    listState = listState,
                                    topPadding = 16.dp
                                ) { song, _ ->
                                    SwipeableSongRow(song, viewModel, customOnClick = {
                                        viewModel.setQueue(streamSongs, startSong = song)
                                    }, adaptiveColor = adaptiveColor)
                                }
                            }
                        }
                    }

                    3 -> { // PLAYLISTS
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (selectedPlaylist != null) {
                                PlaylistDetailView(
                                    playlist = selectedPlaylist!!,
                                    viewModel = viewModel,
                                    adaptiveColor = adaptiveColor,
                                    minibarTextColor = minibarTextColor,
                                    onBack = { selectedPlaylistId = null },
                                    onShuffle = {
                                        val playlistSongs = viewModel.getSongsInPlaylist(selectedPlaylist!!)
                                        if (playlistSongs.isNotEmpty()) {
                                            if (!isShuffleOn) viewModel.toggleShuffle()
                                            viewModel.setQueue(playlistSongs)
                                        }
                                    }
                                )
                            } else {
                                if (playlists.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("No playlists yet. Tap + to create.", color = Color.Gray)
                                    }
                                } else {
                                    ResponsiveSnapList(
                                        items = playlists,
                                        key = { it.id },
                                        scrollbarColor = adaptiveColor,
                                        topPadding = 16.dp
                                    ) { playlist, _ ->
                                        SwipeablePlaylistCard(
                                            playlist = playlist,
                                            onClick = { selectedPlaylistId = playlist.id },
                                            onDelete = { viewModel.deletePlaylist(playlist) },
                                            onEdit = {
                                                editingPlaylist = playlist
                                                showEditDialog = true
                                            },
                                            viewModel = viewModel,
                                            accentColor = adaptiveColor
                                        )
                                    }
                                }
                                // State buat nentuin menu kebuka atau ketutup
                                FloatingActionButton(
                                    onClick = { showCreateDialog = true },
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(24.dp),
                                    containerColor = adaptiveColor,
                                    contentColor = minibarTextColor,
                                    shape = CircleShape
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = "Create Playlist")
                                }
                            }
                        }
                    }
                }
            }
        }

        // ---------- 5. MINI PLAYER BAR (Rounded per icon, no backplate) ----------
        Spacer(modifier = Modifier.height(6.dp))
        AnimatedVisibility(
            visible = currentSong != null,
            enter = expandVertically(
                expandFrom = Alignment.Bottom,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f)
            ) + fadeIn(animationSpec = tween(350, delayMillis = 100)),
            exit = shrinkVertically(
                shrinkTowards = Alignment.Bottom,
                animationSpec = tween(250)
            ) + fadeOut(animationSpec = tween(200))
        ) {
            val animatedBg by animateColorAsState(
                targetValue = controlBgColor ?: Color.Transparent,
                animationSpec = tween(300),
                label = "controlBg"
            )
            val hasBg = controlBgColor != null
            val btnBg = if (hasBg) Color.Transparent else adaptiveColor
            val btnTint = if (hasBg) {
                if (controlBgColor!!.luminance() > 0.5f) Color.Black else Color.White
            } else minibarTextColor
            val inactiveBg = if (hasBg) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant
            val inactiveTint = if (hasBg) btnTint.copy(alpha = 0.35f) else MaterialTheme.colorScheme.onSurfaceVariant
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(animatedBg)
                    .pointerInput(Unit) {
                        var dragSum = 0f
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (dragSum < -60f && paletteColors.isNotEmpty()) {
                                    currentPaletteIndex = (currentPaletteIndex + 1) % paletteColors.size
                                    controlBgColor = paletteColors[currentPaletteIndex].copy(alpha = 0.15f)
                                }
                                if (dragSum > 60f) {
                                    controlBgColor = null
                                    currentPaletteIndex = 0
                                }
                                dragSum = 0f
                            }
                        ) { _, dragAmount ->
                            dragSum += dragAmount
                        }
                    }
                    .padding(vertical = 6.dp)
            ) {
                MiniControlButton(
                    icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    onClick = { viewModel.togglePlayPause() },
                    bg = btnBg,
                    tint = btnTint,
                    size = 40.dp
                )
                MiniControlButton(Icons.Rounded.SkipPrevious, { viewModel.playPrevious() }, btnBg, btnTint, 40.dp)
                MiniControlButton(Icons.Rounded.SkipNext, { viewModel.playNext() }, btnBg, btnTint, 40.dp)
                MiniControlButton(
                    Icons.Rounded.Shuffle,
                    { viewModel.toggleShuffle() },
                    if (isShuffleOn) btnBg else inactiveBg,
                    if (isShuffleOn) btnTint else inactiveTint,
                    40.dp
                )
                MiniControlButton(
                    if (repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    { viewModel.toggleRepeat() },
                    if (repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) btnBg else inactiveBg,
                    if (repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) btnTint else inactiveTint,
                    40.dp
                )
            }
        }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            songs = songs,
            onDismiss = { showCreateDialog = false },
            onCreate = { name, selectedIds ->
                viewModel.createPlaylist(name, selectedIds)
                showCreateDialog = false
            }
        )
    }

    if (showEditDialog && editingPlaylist != null) {
        EditPlaylistDialog(
            playlist = editingPlaylist!!,
            onDismiss = {
                showEditDialog = false
                editingPlaylist = null
            },
            onUpdate = { name, imageUri ->
                viewModel.updatePlaylist(editingPlaylist!!.id, newName = name, newImageUri = imageUri)
                showEditDialog = false
                editingPlaylist = null
            }
        )
    }

    if (isUpdateAvailable && updateInfo != null) {
        UpdateDialog(
            updateInfo = updateInfo!!,
            isDownloading = isDownloading,
            onUpdate = { viewModel.downloadUpdate() },
            onDismiss = { viewModel.dismissUpdate() }
        )
    }

    if (showRecapCard && monthlyRecap != null) {
        com.ianocent.musicplayer.ui.RecapCardSheet(
            recap = monthlyRecap!!,
            accentColor = adaptiveColor,
            onDismiss = { viewModel.closeRecapCard() }
        )
    }
}
@Composable
fun UpdateDialog(
    updateInfo: com.ianocent.musicplayer.data.UpdateInfo,
    isDownloading: Boolean,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(28.dp),
        title = {
            Text("Update Available", fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    "Version ${updateInfo.versionName} is available!",
                    style = MaterialTheme.typography.bodyLarge
                )
                if (updateInfo.releaseNotes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        updateInfo.releaseNotes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onUpdate,
                enabled = !isDownloading,
                shape = RoundedCornerShape(16.dp)
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Downloading...")
                } else {
                    Text("Update")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isDownloading
            ) { Text("Later") }
        }
    )
}
@Composable
fun TabItem(text: String, isSelected: Boolean, adaptiveColor: Color, onClick: () -> Unit) {
    val bg by animateColorAsState(
        targetValue = if (isSelected) adaptiveColor.copy(alpha = 0.2f) else Color.Transparent,
        label = "tab_bg_$text"
    )
    Text(
        text = text,
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
        color = if (isSelected) adaptiveColor else Color.Gray,
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 10.dp)
    )
}
@Composable
fun RowScope.TabItem(text: String, isSelected: Boolean, adaptiveColor: Color, onClick: () -> Unit) {
    val bg by animateColorAsState(
        targetValue = if (isSelected) adaptiveColor.copy(alpha = 0.2f) else Color.Transparent,
        label = "tab_bg_$text"
    )
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .clickable { onClick() }
            .animateContentSize() // Bikin animasi pas ngelebar/ngecil
            .then(if (isSelected) Modifier.weight(1f) else Modifier) // Otomatis ngisi ruang kosong kalo aktif
            .padding(horizontal = 24.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (isSelected) adaptiveColor else Color.Gray,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
@Composable
fun MiniControlButton(icon: ImageVector, onClick: () -> Unit, bg: Color, tint: Color, size: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size) // Size sekarang dinamis
            .clip(CircleShape)
            .background(bg)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.6f))
    }
}
@Composable
fun SongRow(
    song: Song,
    viewModel: MusicViewModel,
    customOnClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onShowMenu: ((Song) -> Unit)? = null
) {
    var art by remember(song.id) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(song.id) {
        viewModel.getCachedArt(song) { bitmap -> art = bitmap?.asImageBitmap() }
    }

    val animatedTitleColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(500),
        label = "titleColor"
    )
    val animatedArtistColor by animateColorAsState(
        targetValue = Color.Gray,
        animationSpec = tween(500),
        label = "artistColor"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { customOnClick?.invoke() ?: viewModel.playSong(song) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (art != null) {
                Image(
                    bitmap = art!!,
                    contentDescription = "Album Art",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Rounded.MusicNote, contentDescription = null)
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = animatedTitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = animatedArtistColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun PlaylistCard(
    playlist: com.ianocent.musicplayer.data.Playlist,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    viewModel: MusicViewModel,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    var art by remember(playlist.id, playlist.imageUri) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(playlist.id, playlist.imageUri) {
        if (playlist.imageUri != null) {
            try {
                val uri = android.net.Uri.parse(playlist.imageUri)
                val inputStream = viewModel.getApplication<android.app.Application>().contentResolver.openInputStream(uri)
                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                art = bitmap?.asImageBitmap()
            } catch (e: Exception) {
                art = null
            }
        } else if (playlist.songIds.isNotEmpty()) {
            val firstSong = viewModel.getSongsInPlaylist(playlist).firstOrNull()
            firstSong?.let { song ->
                viewModel.getCachedArt(song) { bitmap -> art = bitmap?.asImageBitmap() }
            }
        }
    }

    val animatedTitleColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(500),
        label = "titleColor"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (art != null) {
                Image(
                    bitmap = art!!,
                    contentDescription = "Playlist Art",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = animatedTitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${playlist.songIds.size} Songs",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun SwipeablePlaylistCard(
    playlist: com.ianocent.musicplayer.data.Playlist,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    viewModel: MusicViewModel,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val swipeThresholdPx = with(density) { 72.dp.toPx() }
    val maxSwipePx = with(density) { 88.dp.toPx() }

    var offsetX by remember { mutableStateOf(0f) }
    var isSwipedOpen by remember { mutableStateOf(false) }
    var showMenuDialog by remember { mutableStateOf(false) }
    var showPlaylistCardSheet by remember { mutableStateOf(false) }

    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "swipe_offset"
    )

    val revealProgress = (offsetX / swipeThresholdPx).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
    ) {
        if (offsetX > 4f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp)
                    .size(40.dp)
                    .graphicsLayer {
                        alpha = revealProgress
                        scaleX = 0.6f + 0.4f * revealProgress
                        scaleY = 0.6f + 0.4f * revealProgress
                    }
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.2f))
                    .clickable(enabled = isSwipedOpen) {
                        showMenuDialog = true
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.MoreVert,
                    contentDescription = "Menu",
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
                .fillMaxWidth()
                .pointerInput(playlist.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            offsetX = if (offsetX >= swipeThresholdPx) {
                                isSwipedOpen = true
                                swipeThresholdPx
                            } else {
                                isSwipedOpen = false
                                0f
                            }
                        },
                        onDragCancel = {
                            offsetX = if (isSwipedOpen) swipeThresholdPx else 0f
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount).coerceIn(0f, maxSwipePx)
                    }
                }
        ) {
            PlaylistCard(
                playlist = playlist,
                onClick = {
                    if (isSwipedOpen) {
                        offsetX = 0f
                        isSwipedOpen = false
                    } else {
                        onClick()
                    }
                },
                onDelete = {},
                onEdit = {},
                viewModel = viewModel,
                accentColor = accentColor
            )
        }
    }

    if (showMenuDialog) {
        AlertDialog(
            onDismissRequest = {
                showMenuDialog = false
                offsetX = 0f
                isSwipedOpen = false
            },
            containerColor = accentColor.copy(alpha = 0.15f).compositeOver(
                MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    playlist.name,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Column {
                    Text(
                        "${playlist.songIds.size} songs",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    RoundedClickableRow(
                        onClick = {
                            showMenuDialog = false
                            showPlaylistCardSheet = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Rounded.Photo,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("Playlist Card", fontWeight = FontWeight.SemiBold)
                    }

                    RoundedClickableRow(
                        onClick = {
                            showMenuDialog = false
                            onEdit()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("Edit Playlist", fontWeight = FontWeight.SemiBold)
                    }

                    RoundedClickableRow(
                        onClick = {
                            showMenuDialog = false
                            onDelete()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = null,
                            tint = Color.Red.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("Delete Playlist", fontWeight = FontWeight.SemiBold, color = Color.Red.copy(alpha = 0.8f))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {}
        )
    }

    if (showPlaylistCardSheet) {
        com.ianocent.musicplayer.ui.PlaylistCardSheet(
            playlist = playlist,
            viewModel = viewModel,
            accentColor = accentColor,
            onDismiss = { showPlaylistCardSheet = false }
        )
    }
}

@Composable
fun GroupRow(name: String, count: Int) {
    val animatedTitleColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(500),
        label = "titleColor"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = animatedTitleColor // <- Pake warna yang dianimasikan
            )
            Text("$count songs", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}

@Composable
fun AlbumRow(album: String, songs: List<Song>, viewModel: MusicViewModel, count: Int, onClick: () -> Unit = {}) {
    var art by remember(album) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(album, songs) {
        val firstSong = songs.firstOrNull()
        firstSong?.let { song ->
            viewModel.getCachedArt(song) { bitmap -> art = bitmap?.asImageBitmap() }
        }
    }

    val animatedTitleColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(500),
        label = "titleColor"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (art != null) {
                Image(
                    bitmap = art!!,
                    contentDescription = "Album Art",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Rounded.Album, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                album,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = animatedTitleColor
            )
            Text("$count songs", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}

@Composable
fun AlbumDetailView(
    album: String,
    songs: List<Song>,
    viewModel: MusicViewModel,
    adaptiveColor: Color,
    minibarTextColor: Color,
    onBack: () -> Unit,
    onShuffle: () -> Unit
) {
    val lazyListState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = album,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 16.dp),
                textAlign = TextAlign.Center
            )

            if (songs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No songs in this album", color = Color.Gray)
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        contentPadding = PaddingValues(bottom = 120.dp, end = 20.dp)
                    ) {
                        items(songs, key = { it.id }) { song ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                                        alpha = 0.3f
                                    )
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                SongRow(
                                    song = song,
                                    viewModel = viewModel,
                                    customOnClick = {
                                        viewModel.setQueue(songs, startSong = song)
                                    }
                                )
                            }
                        }
                    }
                    DraggableScrollbar(lazyListState, adaptiveColor)
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            var isMenuExpanded by remember { mutableStateOf(false) }

            AnimatedVisibility(
                visible = isMenuExpanded,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SmallFloatingActionButton(
                        onClick = onBack,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ) { Icon(Icons.Rounded.ArrowBackIosNew, contentDescription = "Back") }

                    SmallFloatingActionButton(
                        onClick = {
                            if (songs.isNotEmpty()) {
                                viewModel.setQueue(songs)
                            }
                            isMenuExpanded = false
                        },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ) { Icon(Icons.Rounded.PlayArrow, contentDescription = "Play All") }

                    SmallFloatingActionButton(
                        onClick = { onShuffle(); isMenuExpanded = false },
                        containerColor = adaptiveColor,
                        contentColor = minibarTextColor
                    ) { Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle") }
                }
            }

            FloatingActionButton(
                onClick = { isMenuExpanded = !isMenuExpanded },
                containerColor = if (isMenuExpanded) MaterialTheme.colorScheme.surfaceVariant else adaptiveColor,
                contentColor = if (isMenuExpanded) MaterialTheme.colorScheme.onSurfaceVariant else minibarTextColor,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = if (isMenuExpanded) Icons.Rounded.Close else Icons.Rounded.Menu,
                    contentDescription = "Menu"
                )
            }
        }
    }
}

@Composable
fun PlaylistDetailView(
    playlist: com.ianocent.musicplayer.data.Playlist,
    viewModel: MusicViewModel,
    adaptiveColor: Color,
    minibarTextColor: Color,
    onBack: () -> Unit,
    onShuffle: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showCardSheet by remember { mutableStateOf(false) }

    // Gunakan mutableStateList agar UI tahu kapan harus update saat item digeser
    val playlistSongs = remember(playlist.songIds) {
        viewModel.getSongsInPlaylist(playlist).toMutableStateList()
    }

    val lazyListState = rememberLazyListState()

    // Perbaikan: Hapus 'onDragEnd' yang error, gunakan logic update di 'onMove'
    val reorderableState = rememberReorderableLazyListState(
        lazyListState = lazyListState,
        onMove = { from, to ->
            // 1. Update UI secara instan
            playlistSongs.add(to.index, playlistSongs.removeAt(from.index))

            // 2. Kirim update ke ViewModel secara real-time
            val newIds = playlistSongs.map { it.id }
            viewModel.savePlaylistOrder(playlist, newIds)
        }
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 16.dp),
                textAlign = TextAlign.Center
            )

            if (playlistSongs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("This playlist is empty", color = Color.Gray)
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        contentPadding = PaddingValues(bottom = 120.dp, end = 20.dp)
                    ) {
                        items(playlistSongs, key = { it.id }) { song ->
                            ReorderableItem(reorderableState, key = song.id) { isDragging ->
                                val elevation by animateDpAsState(
                                    if (isDragging) 8.dp else 0.dp,
                                    label = "drag_elevation"
                                )
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .shadow(elevation, RoundedCornerShape(12.dp)),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                                            alpha = 0.3f
                                        )
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
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
                                        SongRow(
                                            song = song,
                                            viewModel = viewModel,
                                            customOnClick = {
                                                viewModel.setQueue(
                                                    playlistSongs,
                                                    startSong = song
                                                )
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    DraggableScrollbar(lazyListState, adaptiveColor)
                }
            }
        }
        // Small floating action buttons (symmetric 42.dp like minibar)
        // State buat nentuin menu kebuka atau ketutup
        var isMenuExpanded by remember { mutableStateOf(false) }

        // Floating Buttons dikelompokkin di kanan bawah
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Tombol-tombol yang muncul pas Hamburger di-klik
            AnimatedVisibility(
                visible = isMenuExpanded,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SmallFloatingActionButton(
                        onClick = onBack,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ) { Icon(Icons.Rounded.ArrowBackIosNew, contentDescription = "Back") }

                    SmallFloatingActionButton(
                        onClick = { showAddDialog = true; isMenuExpanded = false },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ) { Icon(Icons.Rounded.PlaylistAdd, contentDescription = "Add Songs") }

                    SmallFloatingActionButton(
                        onClick = { showCardSheet = true; isMenuExpanded = false },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ) { Icon(Icons.Rounded.Share, contentDescription = "Share") }

                    SmallFloatingActionButton(
                        onClick = { onShuffle(); isMenuExpanded = false },
                        containerColor = adaptiveColor,
                        contentColor = minibarTextColor
                    ) { Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle") }
                }
            }

            // Tombol Utama (Hamburger)
            FloatingActionButton(
                onClick = { isMenuExpanded = !isMenuExpanded },
                containerColor = if (isMenuExpanded) MaterialTheme.colorScheme.surfaceVariant else adaptiveColor,
                contentColor = if (isMenuExpanded) MaterialTheme.colorScheme.onSurfaceVariant else minibarTextColor,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = if (isMenuExpanded) Icons.Rounded.Close else Icons.Rounded.Menu,
                    contentDescription = "Menu"
                )
            }
        }
    }

    if (showAddDialog) {
        AddSongsToPlaylistDialog(
            playlist = playlist,
            allSongs = viewModel.songs.collectAsState().value,
            onDismiss = { showAddDialog = false },
            onAdd = { ids ->
                viewModel.addSongsToPlaylist(playlist, ids)
                showAddDialog = false
            }
        )
    }

    if (showCardSheet) {
        com.ianocent.musicplayer.ui.PlaylistCardSheet(
            playlist = playlist,
            viewModel = viewModel,
            accentColor = adaptiveColor,
            onDismiss = { showCardSheet = false }
        )
    }
}
@Composable
fun EditPlaylistDialog(
    playlist: com.ianocent.musicplayer.data.Playlist,
    onDismiss: () -> Unit,
    onUpdate: (String, String?) -> Unit  // <-- tambahin parameter imageUri
) {
    var name by remember { mutableStateOf(playlist.name) }
    var pickedImageUri by remember { mutableStateOf(playlist.imageUri) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val imagePicker = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) { /* beberapa provider gak support, aman diabaikan */ }
            pickedImageUri = it.toString()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Edit Playlist", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .padding(6.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            .clickable { imagePicker.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (pickedImageUri != null) {
                            coil.compose.AsyncImage(
                                model = pickedImageUri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = "Pilih gambar", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                    }
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            if (name.isEmpty()) Text("Playlist name...", color = Color.Gray)
                            innerTextField()
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onUpdate(name, pickedImageUri) },
                enabled = name.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AddSongsToPlaylistDialog(
    playlist: com.ianocent.musicplayer.data.Playlist,
    allSongs: List<Song>,
    onDismiss: () -> Unit,
    onAdd: (List<Long>) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val selectedIds = remember { mutableStateListOf<Long>() }
    val existingIds = playlist.songIds

    val filteredSongs = remember(allSongs, searchQuery) {
        val available = allSongs.filter { it.id !in existingIds }
        if (searchQuery.isBlank()) available
        else available.filter { it.title.contains(searchQuery, true) || it.artist.contains(searchQuery, true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Add Songs", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                // Cute search songs field
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .padding(6.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) Text("Search songs...", color = Color.Gray)
                            innerTextField()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("${selectedIds.size} selected", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                    items(filteredSongs, key = { it.id }) { song ->
                        RoundedClickableRow(
                            onClick = {
                                if (selectedIds.contains(song.id)) selectedIds.remove(song.id)
                                else selectedIds.add(song.id)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RoundedCheckbox(
                                checked = selectedIds.contains(song.id),
                                onCheckedChange = {
                                    if (it) selectedIds.add(song.id) else selectedIds.remove(song.id)
                                }
                            )
                            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(song.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(selectedIds.toList()) },
                enabled = selectedIds.isNotEmpty()
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun BoxScope.DraggableScrollbar(
    listState: androidx.compose.foundation.lazy.LazyListState,
    color: Color,
    thumbWidth: Dp = 6.dp
) {
    val coroutineScope = rememberCoroutineScope()
    var containerHeightPx by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    val density = androidx.compose.ui.platform.LocalDensity.current

    val totalItems = listState.layoutInfo.totalItemsCount
    if (totalItems <= 0) return

    val visibleCount = listState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
    val thumbFraction = (visibleCount.toFloat() / totalItems).coerceIn(0.08f, 1f)
    val firstVisible = listState.firstVisibleItemIndex
    val maxScrollableIndex = (totalItems - 1).coerceAtLeast(1)
    val scrollFraction = firstVisible.toFloat() / maxScrollableIndex

    Box(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(28.dp) // area sentuh lebih lebar dari thumb, biar gampang di-drag
            .onGloballyPositioned { containerHeightPx = it.size.height.toFloat() }
            .pointerInput(totalItems) {
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                    onDrag = { change, _ ->
                        change.consume()
                        if (containerHeightPx > 0) {
                            val fraction = (change.position.y / containerHeightPx).coerceIn(0f, 1f)
                            val targetIndex = (fraction * maxScrollableIndex).toInt().coerceIn(0, maxScrollableIndex)
                            coroutineScope.launch { listState.scrollToItem(targetIndex) }
                        }
                    }
                )
            }
    ) {
        val thumbHeightDp = with(density) { (containerHeightPx * thumbFraction).toDp() }
        val offsetYDp = with(density) { (containerHeightPx * scrollFraction * (1 - thumbFraction)).toDp() }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 4.dp)
                .offset(y = offsetYDp)
                .width(thumbWidth)
                .height(thumbHeightDp.coerceAtLeast(24.dp))
                .clip(RoundedCornerShape(thumbWidth / 2))
                .background(color.copy(alpha = if (isDragging) 0.9f else 0.4f))
        )
    }
}

@Composable
fun RoundedClickableRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
fun CreatePlaylistDialog(
    songs: List<Song>,
    onDismiss: () -> Unit,
    onCreate: (String, List<Long>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    val selectedIds = remember { mutableStateListOf<Long>() }

    val filteredSongs = remember(songs, searchQuery) {
        if (searchQuery.isBlank()) songs
        else songs.filter { it.title.contains(searchQuery, true) || it.artist.contains(searchQuery, true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Create New Playlist", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                // Cute search-bar-style playlist name field
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .padding(6.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.PlaylistPlay, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            if (name.isEmpty()) Text("Playlist name...", color = Color.Gray)
                            innerTextField()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Cute search songs field
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .padding(6.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) Text("Search songs to add...", color = Color.Gray)
                            innerTextField()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("${selectedIds.size} selected", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                    items(filteredSongs, key = { it.id }) { song ->
                        RoundedClickableRow(
                            onClick = {
                                if (selectedIds.contains(song.id)) selectedIds.remove(song.id)
                                else selectedIds.add(song.id)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RoundedCheckbox(
                                checked = selectedIds.contains(song.id),
                                onCheckedChange = {
                                    if (it) selectedIds.add(song.id) else selectedIds.remove(song.id)
                                }
                            )
                            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(song.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name, selectedIds.toList()) },
                enabled = name.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
@Composable
fun SwipeableSongRow(
    song: Song,
    viewModel: MusicViewModel,
    customOnClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    adaptiveColor: Color
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val swipeThresholdPx = with(density) { 72.dp.toPx() }
    val maxSwipePx = with(density) { 88.dp.toPx() }

    var offsetX by remember { mutableStateOf(0f) }
    var isSwipedOpen by remember { mutableStateOf(false) }
    var showActionDialog by remember { mutableStateOf(false) }
    var showSongCardSheet by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var downloadFormats by remember { mutableStateOf<List<com.ianocent.musicplayer.data.AudioFormat>>(emptyList()) }
    var isLoadingFormats by remember { mutableStateOf(false) }
    
    // Low-res for the list
    var songArtLowRes by remember(song.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
    // High-res for the card
    var songArtHighRes by remember(song.id) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(song.id) {
        viewModel.getCachedArt(song) { bitmap -> songArtLowRes = bitmap }
    }

    // Load high-res only when card is about to be shown
    LaunchedEffect(showSongCardSheet) {
        if (showSongCardSheet && songArtHighRes == null) {
            viewModel.getHighResArt(song) { bitmap -> songArtHighRes = bitmap }
        }
    }

    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "swipe_offset"
    )

    val revealProgress = (offsetX / swipeThresholdPx).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
    ) {
        if (offsetX > 4f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp) // Symmetrical center in 72dp threshold
                    .size(40.dp)
                    .graphicsLayer {
                        alpha = revealProgress
                        scaleX = 0.6f + 0.4f * revealProgress
                        scaleY = 0.6f + 0.4f * revealProgress
                    }
                    .clip(RoundedCornerShape(12.dp))
                    .background(adaptiveColor.copy(alpha = 0.2f))
                    .clickable(enabled = isSwipedOpen) {
                        showActionDialog = true
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.MoreVert,
                    contentDescription = "Menu",
                    tint = adaptiveColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
                .fillMaxWidth()
                .pointerInput(song.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            offsetX = if (offsetX >= swipeThresholdPx) {
                                isSwipedOpen = true
                                swipeThresholdPx
                            } else {
                                isSwipedOpen = false
                                0f
                            }
                        },
                        onDragCancel = {
                            offsetX = if (isSwipedOpen) swipeThresholdPx else 0f
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount).coerceIn(0f, maxSwipePx)
                    }
                }
        ) {
            SongRow(
                song = song,
                viewModel = viewModel,
                customOnClick = {
                    if (isSwipedOpen) {
                        offsetX = 0f
                        isSwipedOpen = false
                    } else {
                        customOnClick?.invoke()
                    }
                }
            )
        }
    }

    if (showActionDialog) {
        AlertDialog(
            onDismissRequest = {
                showActionDialog = false
                offsetX = 0f
                isSwipedOpen = false
            },
            containerColor = adaptiveColor.copy(alpha = 0.15f).compositeOver(
                MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    song.title,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Column {
                    Text(
                        song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    RoundedClickableRow(
                        onClick = {
                            showActionDialog = false
                            showSongCardSheet = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Photo, contentDescription = null, tint = adaptiveColor, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Song Card", fontWeight = FontWeight.SemiBold)
                    }

                    if (song.isStream) {
                        RoundedClickableRow(
                            onClick = {
                                showActionDialog = false
                                isLoadingFormats = true
                                viewModel.getAudioFormats(song) { formats ->
                                    downloadFormats = formats
                                    isLoadingFormats = false
                                    showDownloadDialog = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null, tint = adaptiveColor, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("Download", fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        RoundedClickableRow(
                            onClick = {
                                showActionDialog = false
                                showEditDialog = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = null, tint = adaptiveColor, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("Edit Song Info", fontWeight = FontWeight.SemiBold)
                        }

                        RoundedClickableRow(
                            onClick = {
                                showActionDialog = false
                                showDeleteConfirm = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Delete, contentDescription = null, tint = Color.Red.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("Delete Song", fontWeight = FontWeight.SemiBold, color = Color.Red.copy(alpha = 0.8f))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {}
        )
    }

    if (showSongCardSheet) {
        com.ianocent.musicplayer.ui.SongCardSheet(
            song = song,
            albumArt = songArtHighRes ?: songArtLowRes, // Use high-res if available
            accentColor = adaptiveColor,
            onDismiss = { showSongCardSheet = false }
        )
    }

    if (showEditDialog) {
        EditSongDialog(
            song = song,
            onDismiss = { showEditDialog = false },
            onUpdate = { newTitle, newArtist ->
                viewModel.updateSongInfo(song.id, newTitle, newArtist)
                showEditDialog = false
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Delete Song", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete \"${song.title}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSong(song)
                        showDeleteConfirm = false
                    }
                ) { Text("Delete", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (isLoadingFormats) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp),
            title = { Text("Loading formats...", fontWeight = FontWeight.Bold) },
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = adaptiveColor)
                }
            },
            confirmButton = {},
            dismissButton = {}
        )
    }

    if (showDownloadDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadDialog = false },
            containerColor = adaptiveColor.copy(alpha = 0.15f).compositeOver(
                MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(24.dp),
            title = { Text("Download Quality", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    downloadFormats.forEach { format ->
                        RoundedClickableRow(
                            onClick = {
                                viewModel.downloadSong(song, format)
                                showDownloadDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null, tint = adaptiveColor, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("${format.qualityLabel} (${format.bitrate}kbps)", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDownloadDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun EditSongDialog(
    song: Song,
    onDismiss: () -> Unit,
    onUpdate: (String, String) -> Unit
) {
    var title by remember { mutableStateOf(song.title) }
    var artist by remember { mutableStateOf(song.artist) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Edit Song Info", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Artist") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onUpdate(title, artist) },
                enabled = title.isNotBlank() && artist.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun RoundedCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (checked) primary else Color.Transparent)
            .then(
                if (!checked) Modifier.border(2.dp, Color.Gray, RoundedCornerShape(8.dp))
                else Modifier
            )
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun MusicRecapBanner(
    recap: com.ianocent.musicplayer.data.MonthlyRecap,
    accentColor: Color,
    onGenerateCard: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = accentColor.copy(alpha = 0.08f)
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = recap.monthLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = accentColor
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Your Recap",
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Dismiss",
                            tint = accentColor.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                RecapStat(label = "Plays", value = "${recap.totalPlays}", accentColor = accentColor)
                RecapStat(label = "Minutes", value = "${recap.totalMinutes}", accentColor = accentColor)
                RecapStat(label = "Artists", value = "${recap.topArtists.size}", accentColor = accentColor)
            }

            Spacer(Modifier.height(14.dp))

            if (recap.topSongs.isNotEmpty()) {
                Text(
                    "Top Songs",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
                Spacer(Modifier.height(6.dp))
                recap.topSongs.take(3).forEach { song ->
                    Row(
                        modifier = Modifier.padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = accentColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${song.title} — ${song.artist}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (recap.topArtists.isNotEmpty()) {
                Text(
                    "Top Artists",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    recap.topArtists.take(4).forEach { (artist, _) ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = accentColor.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = artist,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                color = accentColor
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = accentColor.copy(alpha = 0.06f)
            ) {
                Text(
                    text = recap.tasteComment,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    fontStyle = FontStyle.Italic
                )
            }

            Spacer(Modifier.height(14.dp))

            Button(
                onClick = onGenerateCard,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Generate Recap Card")
            }
        }
    }
}

@Composable
fun RecapStat(label: String, value: String, accentColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = accentColor
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}
