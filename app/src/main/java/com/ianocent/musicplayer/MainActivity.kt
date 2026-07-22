package com.ianocent.musicplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size as GeomSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.compositeOver
import kotlin.math.roundToInt
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Favorite
import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.ui.tooling.preview.Preview

private data class SocialMenuItem(val label: String, val url: String, val bgColor: Color, val textColor: Color)
private val socialMenuItems = listOf(
    SocialMenuItem("Facebook", "https://web.facebook.com/ianocents", Color(0xFF1877F2), Color(0xFF1877F2)),
    SocialMenuItem("Instagram", "https://www.instagram.com/ianocent/", Color(0xFFE4405F), Color(0xFFE4405F)),
    SocialMenuItem("TikTok", "https://www.tiktok.com/@ianocent", Color(0xFF010101), Color.Transparent),
)

@Composable
private fun FacebookIcon(modifier: Modifier = Modifier, tint: Color = Color(0xFF1877F2)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val corner = w * 0.28f
        val sw = w * 0.12f
        drawRoundRect(tint, cornerRadius = CornerRadius(corner))
        drawRoundRect(Color.White, topLeft = Offset(w * 0.43f, w * 0.20f), size = GeomSize(sw, w * 0.60f), cornerRadius = CornerRadius(sw / 2))
        drawRoundRect(Color.White, topLeft = Offset(w * 0.43f, w * 0.35f), size = GeomSize(w * 0.32f, sw), cornerRadius = CornerRadius(sw / 2))
        drawRoundRect(Color.White, topLeft = Offset(w * 0.43f, w * 0.50f), size = GeomSize(w * 0.20f, sw), cornerRadius = CornerRadius(sw / 2))
    }
}

@Composable
private fun InstagramIcon(modifier: Modifier = Modifier, tint: Color = Color(0xFFE4405F)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val stroke = w * 0.08f
        val inset = stroke / 2 + w * 0.04f
        val sz = w - inset * 2
        drawRoundRect(tint, topLeft = Offset(inset, inset), size = GeomSize(sz, sz), cornerRadius = CornerRadius(w * 0.25f), style = Stroke(stroke))
        drawCircle(tint, radius = w * 0.18f, center = Offset(w / 2, w / 2), style = Stroke(stroke))
        drawCircle(tint, radius = w * 0.04f, center = Offset(w * 0.74f, w * 0.26f))
    }
}

@Composable
private fun TikTokIcon(modifier: Modifier = Modifier, tint: Color = Color.White) {
    Canvas(modifier = modifier) {
        val w = size.width
        val path = Path().apply {
            addOval(Rect(offset = Offset(w * 0.25f, w * 0.48f), size = GeomSize(w * 0.30f, w * 0.26f)))
            moveTo(w * 0.48f, w * 0.50f)
            lineTo(w * 0.48f, w * 0.15f)
            moveTo(w * 0.48f, w * 0.15f)
            cubicTo(w * 0.65f, w * 0.18f, w * 0.72f, w * 0.32f, w * 0.54f, w * 0.40f)
        }
        drawPath(path, tint, style = Stroke(width = w * 0.10f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

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

                val snackbarHostState = remember { SnackbarHostState() }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = animatedScaffoldBg,
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { innerPadding ->
                    AppNavHost(
                        viewModel = viewModel,
                        innerPadding = innerPadding,
                        snackbarHostState = snackbarHostState
                    )
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
                            val letterIdx = (fraction * (letters.size - 1)).toInt()
                                .coerceIn(0, letters.size - 1)
                            tooltipLetter = letters[letterIdx]
                            coroutineScope.launch {
                                val targetIdx =
                                    listState.layoutInfo.totalItemsCount * fraction.toDouble()
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


@Composable
fun AppNavHost(
    viewModel: MusicViewModel,
    innerPadding: PaddingValues,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    var showNowPlaying by remember { mutableStateOf(false) }
    var miniAlbumArtRect by remember { mutableStateOf<ElementRect?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        ListingScreen(
            isNowPlayingVisible = showNowPlaying,
            snackbarHostState = snackbarHostState,
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
    onMiniPlayerLayout: (albumArtRect: ElementRect, progressRect: ElementRect) -> Unit = { _, _ -> },
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    isNowPlayingVisible: Boolean = false
) {
    var hasPermission by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current

    val storagePermission = if (Build.VERSION.SDK_INT >= 33)
        Manifest.permission.READ_MEDIA_AUDIO
    else
        Manifest.permission.READ_EXTERNAL_STORAGE

    val storageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) viewModel.loadSongs()
    }

    LaunchedEffect(Unit) {
        storageLauncher.launch(storagePermission)
    }

    val songs by viewModel.songs.collectAsState()
    val isLoadingSongs by viewModel.isLoadingSongs.collectAsState()
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
    var tabPage by remember { mutableStateOf(0) }

    val showListeningPill = viewModel.showListeningPill.collectAsState().value
    val miniLayoutIndex = viewModel.miniLayoutIndex.collectAsState().value
    val isPillAtBottom = viewModel.isPillAtBottom.collectAsState().value
    var showPlaylistSelectionDialog by remember { mutableStateOf<Song?>(null) }

    // ---- Voice Search ----
    var isListening by remember { mutableStateOf(false) }
    val voicePermission = Manifest.permission.RECORD_AUDIO
    var rmsLevel by remember { mutableFloatStateOf(0f) }
    var voiceText by remember { mutableStateOf("") }

    val isPreview = androidx.compose.ui.platform.LocalInspectionMode.current
    val voiceManager = remember {
        if (isPreview) null
        else VoiceRecognitionManager(
            context = ctx,
            onResults = { text ->
                searchQuery = text
                isSearchActive = true
                selectedTab = 2
                tabPage = 1
            },
            onPartialResults = { text -> voiceText = text },
            onError = { isListening = false },
            onRmsChanged = { level -> rmsLevel = level },
            onListeningStateChanged = { listening -> isListening = listening }
        )
    }

    val voicePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceText = ""
            rmsLevel = 0f
            voiceManager?.startListening()
        }
    }

    // 1-minute auto-stop timeout
    LaunchedEffect(isListening) {
        if (!isListening) return@LaunchedEffect
        delay(60_000)
        if (isListening) {
            voiceManager?.stopListening()
            isListening = false
        }
    }

    DisposableEffect(Unit) {
        onDispose { voiceManager?.destroy() }
    }

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

    val currentGenre by viewModel.selectedGenre.collectAsState()

    BackHandler(
        enabled = selectedAlbum != null || selectedPlaylistId != null || currentGenre != null || isSearchActive
    ) {
        when {
            selectedAlbum != null -> selectedAlbum = null
            selectedPlaylistId != null -> selectedPlaylistId = null
            currentGenre != null -> viewModel.clearGenre()
            isSearchActive -> { isSearchActive = false; searchQuery = "" }
        }
    }

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

    val lastDeletedSong by viewModel.lastDeletedSong.collectAsState()
    LaunchedEffect(lastDeletedSong) {
        val song = lastDeletedSong ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "\"${song.title}\" deleted",
            actionLabel = "Undo",
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.undoDelete()
        }
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

                        // Voice Search Button
                        if (isListening) {
                            VoiceSpectrum(
                                rmsLevel = rmsLevel,
                                accentColor = adaptiveColor,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        } else {
                            IconButton(
                                onClick = {
                                    if (ctx.checkSelfPermission(voicePermission) == PackageManager.PERMISSION_GRANTED) {
                                        voiceText = ""
                                        rmsLevel = 0f
                                        voiceManager?.startListening()
                                    } else {
                                        voicePermissionLauncher.launch(voicePermission)
                                    }
                                },
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.MicNone,
                                    contentDescription = "Voice search",
                                    tint = Color.Gray
                                )
                            }
                        }

                        // Icon Close
                        IconButton(
                            onClick = { isSearchActive = false; searchQuery = "" },
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.Gray)
                        }
                    }
                } else {
                    var showSocialMenu by remember { mutableStateOf(false) }
                    var socialMenuVisibleItems by remember { mutableIntStateOf(0) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val searchIconTint by animateColorAsState(
                            targetValue = MaterialTheme.colorScheme.onBackground,
                            animationSpec = tween(400), label = "search_tint"
                        )
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(
                                Icons.Rounded.Search,
                                contentDescription = "Search",
                                tint = searchIconTint
                            )
                            }
                            var anchorHeightPx by remember { mutableIntStateOf(0) }
                            var startShine by remember { mutableStateOf(false) }
                            LaunchedEffect(Unit) {
                                while (true) {
                                    delay(90000) // 1 menit 30 detik
                                    startShine = true
                                    delay(5000) // Durasi kilauan lewat
                                    startShine = false
                                }
                            }
                            val shineProgress by animateFloatAsState(
                                targetValue = if (startShine) 1f else 0f,
                                animationSpec = if (startShine) tween(2000) else tween(500),
                                label = "shine"
                            )

                            val animatedBlur by animateFloatAsState(
                                targetValue = if (startShine) 16f else 4f,
                                animationSpec = tween(1000),
                                label = "blur"
                            )
                            val animatedShadowColor by animateColorAsState(
                                targetValue = if (startShine) Color(0xFFFFD700).copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.2f),
                                animationSpec = tween(1000), label = "shadowColor"
                            )
                            val animatedShadowOffset by animateOffsetAsState(
                                targetValue = if (startShine) Offset(0f, 0f) else Offset(0f, 2f),
                                animationSpec = tween(1000), label = "shadowOffset"
                            )

                            Box(
                                modifier = Modifier
                                    .weight(0.5f)
                                    .onGloballyPositioned { anchorHeightPx = it.size.height },
                                contentAlignment = Alignment.Center
                            ) {
                                val baseTextColor = MaterialTheme.colorScheme.onBackground
                                Text(
                                    "ıanplayer.",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        shadow = Shadow(
                                            color = animatedShadowColor,
                                            offset = animatedShadowOffset,
                                            blurRadius = animatedBlur
                                        ),
                                        brush = if (startShine) {
                                            androidx.compose.ui.graphics.Brush.linearGradient(
                                                colors = listOf(
                                                    baseTextColor,
                                                    Color(0xFFFFD700), // Gold
                                                    Color.White,       // Sparkle
                                                    Color(0xFFFFD700), // Gold
                                                    baseTextColor
                                                ),
                                                start = Offset(shineProgress * 400f - 200f, 0f),
                                                end = Offset(shineProgress * 400f, 0f)
                                            )
                                        } else null
                                    ),
                                    color = if (startShine) Color.Unspecified else baseTextColor,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.clickable { showSocialMenu = true }
                                )
                                LaunchedEffect(showSocialMenu) {
                                    if (showSocialMenu) {
                                        socialMenuItems.forEachIndexed { index, _ ->
                                            delay(60)
                                            socialMenuVisibleItems = index + 1
                                        }
                                    } else {
                                        socialMenuVisibleItems = 0
                                    }
                                }
                                if (showSocialMenu) {
                                    val density = LocalDensity.current
                                    Popup(
                                        onDismissRequest = { showSocialMenu = false },
                                        alignment = Alignment.TopCenter,
                                        offset = IntOffset(0, anchorHeightPx + with(density) { 8.dp.roundToPx() }),
                                        properties = PopupProperties(focusable = true)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            shadowElevation = 8.dp,
                                            color = MaterialTheme.colorScheme.surface,
                                            tonalElevation = 4.dp
                                        ) {
                                            val contextPopup = androidx.compose.ui.platform.LocalContext.current
                                            Row(
                                                modifier = Modifier.padding(8.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                socialMenuItems.forEachIndexed { idx, item ->
                                                    AnimatedVisibility(
                                                        visible = idx < socialMenuVisibleItems,
                                                        enter = fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.8f)
                                                    ) {
                                                        val iconTint = if (item.label == "TikTok" && isDarkMode) Color.White else item.bgColor
                                                        Box(
                                                            modifier = Modifier
                                                                .size(40.dp)
                                                                .clip(CircleShape)
                                                                .background(item.bgColor.copy(alpha = 0.1f))
                                                                .clickable {
                                                                    showSocialMenu = false
                                                                    try {
                                                                        contextPopup.startActivity(
                                                                            android.content.Intent(
                                                                                android.content.Intent.ACTION_VIEW,
                                                                                android.net.Uri.parse(
                                                                                    item.url
                                                                                )
                                                                            )
                                                                        )
                                                                    } catch (_: Exception) {
                                                                    }
                                                                },
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            when (item.label) {
                                                                "Facebook" -> FacebookIcon(modifier = Modifier.size(24.dp), tint = iconTint)
                                                                "Instagram" -> InstagramIcon(modifier = Modifier.size(24.dp), tint = iconTint)
                                                                "TikTok" -> TikTokIcon(modifier = Modifier.size(24.dp), tint = iconTint)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            val sunMoonTint by animateColorAsState(
                            targetValue = MaterialTheme.colorScheme.onBackground,
                            animationSpec = tween(400), label = "sun_moon_tint"
                        )
                        IconButton(onClick = { viewModel.toggleDarkMode() }) {
                            Icon(
                                imageVector = if (isDarkMode) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                contentDescription = "Toggle theme",
                                tint = sunMoonTint
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val listeningPill = @Composable {
            AnimatedVisibility(
                visible = showListeningPill && currentSong != null && !isPillAtBottom,
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
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .pointerInput(Unit) {
                                detectVerticalDragGestures { change, dragAmount ->
                                    if (dragAmount > 25) {
                                        viewModel.setPillAtBottom(true)
                                        change.consume()
                                    }
                                }
                            }
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures { change, dragAmount ->
                                    if (dragAmount < -20) { // Swipe left to show volume
                                        showVolumeControl = true
                                        change.consume()
                                    }
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
                                    Box(
                                        modifier = Modifier
                                            .padding(end = 8.dp)
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(
                                                MaterialTheme.colorScheme.surfaceVariant.copy(
                                                    alpha = 0.5f
                                                )
                                            )
                                            .clickable { showVolumeControl = false },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Rounded.ChevronLeft,
                                            contentDescription = "Back",
                                            tint = Color.Gray,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
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
                                    var targetArt by remember(song.id) { mutableStateOf<ImageBitmap?>(null) }
                                    LaunchedEffect(song.id) {
                                        viewModel.getCachedArt(song) { bitmap -> targetArt = bitmap?.asImageBitmap() }
                                    }

                                    val rotationAnim = remember { androidx.compose.animation.core.Animatable(0f) }
                                    LaunchedEffect(isPlaying) {
                                        if (isPlaying) {
                                            rotationAnim.animateTo(
                                                targetValue = rotationAnim.value + 360f,
                                                animationSpec = infiniteRepeatable(
                                                    animation = tween(8000, easing = LinearEasing),
                                                    repeatMode = RepeatMode.Restart
                                                )
                                            )
                                        } else {
                                            rotationAnim.stop()
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .graphicsLayer { rotationZ = rotationAnim.value }
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer)
                                            .clickable { onNowPlayingClick() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (targetArt != null) {
                                            Image(
                                                bitmap = targetArt!!,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(20.dp))
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
                                    val minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(currentPosition)
                                    val seconds = java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(currentPosition) % 60
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(end = 8.dp)
                                    ) {
                                        Text(text = String.format("%02d", minutes), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        Text(text = String.format("%02d", seconds), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!isPillAtBottom) listeningPill()

        val topSpacerHeight by animateDpAsState(
            targetValue = if (isPillAtBottom) 0.dp else 8.dp,
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
            label = "topSpacer"
        )
        Spacer(modifier = Modifier.height(topSpacerHeight))

        // ---------- 3. TABS ----------

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
        val streamParsingFailed by viewModel.streamParsingFailed.collectAsState()
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
                        var showFavoritesOnly by remember { mutableStateOf(false) }
                        val sortLabels = listOf("A - Z", "Recently Added", "Most Played")
                        val favoriteIds by viewModel.favoriteIds.collectAsState()

                        val displaySongs = remember(sortedSongs, showFavoritesOnly, favoriteIds) {
                            if (showFavoritesOnly) sortedSongs.filter { favoriteIds.contains(it.id) }
                            else sortedSongs
                        }

                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // DEBUG: trigger recap card manual, tanpa nunggu interval 30 hari asli.
                                    IconButton(
                                        onClick = { viewModel.debugTriggerRecap() },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Analytics, "Debug Recap",
                                            tint = adaptiveColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    // Favorites filter toggle
                                    IconButton(
                                        onClick = { showFavoritesOnly = !showFavoritesOnly },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (showFavoritesOnly) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                            contentDescription = "Favorites",
                                            tint = if (showFavoritesOnly) Color(0xFFE91E63) else adaptiveColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

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
                                enter = fadeIn(tween(300)) + slideInVertically(
                                    animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f)
                                ),
                                exit = fadeOut(tween(200)) + slideOutVertically(
                                    animationSpec = tween(250)
                                ) + shrinkVertically(
                                    animationSpec = tween(250)
                                )
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

                            if (isLoadingSongs && displaySongs.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = adaptiveColor)
                                }
                            } else {
                                ResponsiveSnapList(
                                    items = displaySongs,
                                    key = { it.id },
                                    scrollbarColor = adaptiveColor,
                                    modifier = Modifier.weight(1f),
                                    listState = listState
                                ) { song, _ ->
                                    SwipeableSongRow(song, viewModel, customOnClick = {
                                        viewModel.setQueue(displaySongs, startSong = song)
                                    }, adaptiveColor = adaptiveColor)
                                }
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
                            if (searchQuery.isBlank()) {
                                val selectedGenre by viewModel.selectedGenre.collectAsState()
                                val genreSongsMap by viewModel.genreSongs.collectAsState()
                                val isGenreLoading by viewModel.isGenreLoading.collectAsState()
                                val genreFirstSong by viewModel.genreFirstSong.collectAsState()
                                val genres = viewModel.genres
                                LaunchedEffect(Unit) { viewModel.loadGenreArtworks() }

                                if (selectedGenre != null) {
                                    // Genre songs list
                                    val genreSongList = genreSongsMap[selectedGenre] ?: emptyList()
                                    val scrollState = rememberScrollState()

                                    Column(modifier = Modifier.fillMaxSize()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(onClick = { viewModel.clearGenre() }) {
                                                Icon(Icons.Rounded.ArrowBack, null, tint = adaptiveColor)
                                            }
                                            Text(
                                                selectedGenre ?: "",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = adaptiveColor
                                            )
                                            Spacer(Modifier.weight(1f))
                                            if (isGenreLoading) {
                                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = adaptiveColor)
                                            }
                                        }

                                        if (isGenreLoading && genreSongList.isEmpty()) {
                                            Box(Modifier
                                                .weight(1f)
                                                .fillMaxWidth(), contentAlignment = Alignment.Center) {
                                                CircularProgressIndicator(color = adaptiveColor)
                                            }
                                        } else if (genreSongList.isEmpty()) {
                                            Box(Modifier
                                                .weight(1f)
                                                .fillMaxWidth(), contentAlignment = Alignment.Center) {
                                                Text("No songs found", color = Color.Gray)
                                            }
                                        } else {
                                            val listState = rememberLazyListState()
                                            ResponsiveSnapList(
                                                items = genreSongList,
                                                key = { it.id },
                                                scrollbarColor = adaptiveColor,
                                                modifier = Modifier.weight(1f),
                                                listState = listState
                                            ) { song, _ ->
                                                SwipeableSongRow(song, viewModel, customOnClick = {
                                                    viewModel.setQueue(genreSongList, startSong = song)
                                                }, adaptiveColor = adaptiveColor)
                                            }
                                        }
                                    }
                                } else {
                                    // Genre grid
                                    val trendingSongs by viewModel.trendingSongs.collectAsState()
                                    val isTrendingLoading by viewModel.isTrendingLoading.collectAsState()

                                    LaunchedEffect(Unit) { viewModel.fetchTrending() }

                                    val scrollState = rememberScrollState()
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(scrollState)
                                    ) {
                                        Spacer(Modifier.height(12.dp))
                                        Text(
                                            "Browse Genre",
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = adaptiveColor
                                        )
                                        Spacer(Modifier.height(8.dp))

                                        // Genre grid - 2 columns
                                        val genreChunks = genres.chunked(2)
                                        Column(
                                            modifier = Modifier.padding(horizontal = 12.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            genreChunks.forEach { rowGenres ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    rowGenres.forEach { genre ->
                                                        GenreCard(
                                                            genre = genre,
                                                            isLoading = isGenreLoading && genreSongsMap[genre.name] == null,
                                                            accentColor = adaptiveColor,
                                                            onClick = { viewModel.selectGenre(genre.name) },
                                                            modifier = Modifier.weight(1f),
                                                            viewModel = viewModel,
                                                            artSong = genreFirstSong[genre.name]
                                                        )
                                                    }
                                                    if (rowGenres.size < 2) {
                                                        Spacer(Modifier.weight(1f))
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(Modifier.height(20.dp))

                                        // Trending section
                                        if (isTrendingLoading && trendingSongs.isEmpty()) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(120.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator(color = adaptiveColor)
                                            }
                                        } else if (trendingSongs.isNotEmpty()) {
                                            Text(
                                                "Trending Now",
                                                modifier = Modifier.padding(horizontal = 16.dp),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = adaptiveColor
                                            )
                                            Spacer(Modifier.height(8.dp))

                                            val trendingChunks = trendingSongs.take(4).chunked(2)
                                            Column(
                                                modifier = Modifier.padding(horizontal = 12.dp),
                                                verticalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                trendingChunks.forEach { rowSongs ->
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                    ) {
                                                        rowSongs.forEach { song ->
                                                            TrendingCard(
                                                                song = song,
                                                                viewModel = viewModel,
                                                                adaptiveColor = adaptiveColor,
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                        }
                                                        if (rowSongs.size < 2) {
                                                            Spacer(Modifier.weight(1f))
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(Modifier.height(16.dp))
                                        Text(
                                            "Search above to find more songs",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp),
                                            textAlign = TextAlign.Center,
                                            color = Color.Gray,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Spacer(Modifier.height(80.dp))
                                    }
                                }
                            } else {
                                // SEARCH RESULTS
                                if (isSearchingRemote) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.align(Alignment.Center),
                                        color = adaptiveColor
                                    )
                                } else if (streamParsingFailed) {
                                    Column(
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .padding(horizontal = 32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            Icons.Rounded.CloudOff,
                                            contentDescription = null,
                                            tint = Color.Gray,
                                            modifier = Modifier.size(40.dp)
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        Text(
                                            "Streaming lagi bermasalah",
                                            color = Color.Gray,
                                            fontWeight = FontWeight.SemiBold,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            "YouTube mungkin lagi update sistemnya. Coba lagi nanti ya.",
                                            color = Color.Gray.copy(alpha = 0.7f),
                                            style = MaterialTheme.typography.bodySmall,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else if (streamSongs.isEmpty()) {
                                    Text(
                                        text = "Not found.",
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

        if (isPillAtBottom) listeningPill()

        // ---------- 5. MINI PLAYER BAR (Swipeable multi-layout) ----------
        Spacer(modifier = Modifier.height(6.dp))
        AnimatedVisibility(
            visible = (showListeningPill || isPillAtBottom) && currentSong != null,
            enter = expandVertically(
                expandFrom = Alignment.Bottom,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f)
            ) + fadeIn(animationSpec = tween(350, delayMillis = 100)),
            exit = shrinkVertically(
                shrinkTowards = Alignment.Bottom,
                animationSpec = tween(250)
            ) + fadeOut(animationSpec = tween(200))
        ) {
            val maxMiniLayout = 2
            val adaptiveBgColor = remember(ambientColor, isDarkMode) {
                if (isDarkMode) {
                    ambientColor.copy(alpha = 0.25f).compositeOver(Color(0xFF121212))
                } else {
                    ambientColor.copy(alpha = 0.12f).compositeOver(Color.White)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(adaptiveBgColor)
                    .pointerInput(Unit) {
                        var accDx = 0f
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                val absDx = kotlin.math.abs(accDx)
                                val threshold = 40f

                                if (absDx > threshold) {
                                    if (accDx > 0) {
                                        // SWIPE RIGHT → next palette
                                        if (paletteColors.isNotEmpty()) {
                                            currentPaletteIndex =
                                                (currentPaletteIndex + 1) % paletteColors.size
                                            controlBgColor =
                                                paletteColors[currentPaletteIndex].copy(alpha = 0.15f)
                                        }
                                    } else {
                                        // SWIPE LEFT → reset bg
                                        controlBgColor = null
                                        currentPaletteIndex = 0
                                    }
                                }
                                accDx = 0f
                            },
                            onDragCancel = { accDx = 0f }
                        ) { change, dragAmount ->
                            change.consume()
                            accDx += dragAmount
                        }
                    }
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = 0.7f,
                            stiffness = 400f
                        )
                    )
            ) {
                val animatedControlBg by animateColorAsState(
                    targetValue = controlBgColor ?: Color.Transparent,
                    animationSpec = tween(300),
                    label = "controlBg"
                )

                Box(modifier = Modifier
                    .fillMaxWidth()
                    .background(animatedControlBg)) {
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(80.dp)
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant.copy(
                                                alpha = 0.5f
                                            )
                                        )
                                        .clickable { showVolumeControl = false },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.ChevronLeft, "Back", tint = Color.Gray, modifier = Modifier.size(20.dp))
                                }
                                Slider(
                                    value = currentVolume.toFloat(),
                                    onValueChange = { v ->
                                        currentVolume = v.toInt()
                                        audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, v.toInt(), 0)
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
                                Icon(Icons.Rounded.VolumeUp, "Vol max", tint = adaptiveColor, modifier = Modifier.size(20.dp))
                            }
                        } else {
                            AnimatedContent(
                                targetState = miniLayoutIndex,
                                transitionSpec = {
                                    (fadeIn(tween(200)) + slideInVertically(animationSpec = tween(250)) { fullHeight -> fullHeight / 4 })
                                        .togetherWith(fadeOut(tween(150)) + slideOutVertically(animationSpec = tween(150)) { fullHeight -> -fullHeight / 4 })
                                },
                                label = "miniLayout",
                                modifier = Modifier.fillMaxWidth()
                            ) { layout ->
                                val btnTint = if (controlBgColor != null) {
                                    if (controlBgColor!!.luminance() > 0.5f) Color.Black else Color.White
                                } else minibarTextColor
                                val btnBg = Color.Transparent

                                when (layout) {
                                    0 -> MiniLayoutDefault(
                                        viewModel = viewModel,
                                        isPlaying = isPlaying,
                                        isShuffleOn = isShuffleOn,
                                        repeatMode = repeatMode,
                                        btnBg = btnBg,
                                        btnTint = btnTint,
                                        hasBg = true,
                                        adaptiveColor = adaptiveColor,
                                        minibarTextColor = minibarTextColor,
                                        currentSong = currentSong,
                                        onNowPlayingClick = onNowPlayingClick,
                                        onMiniPlayerLayout = onMiniPlayerLayout,
                                        isNowPlayingVisible = isNowPlayingVisible,
                                        isBuffering = isBuffering,
                                        currentPosition = currentPosition,
                                        duration = duration,
                                        isPillAtBottom = isPillAtBottom,
                                        onToggleVolume = { showVolumeControl = true }
                                    )
                                    1 -> MiniLayoutFloating(
                                        viewModel = viewModel,
                                        isPlaying = isPlaying,
                                        btnTint = btnTint,
                                        adaptiveColor = adaptiveColor,
                                        isBuffering = isBuffering,
                                        currentSong = currentSong,
                                        onNowPlayingClick = onNowPlayingClick,
                                        isPillAtBottom = isPillAtBottom,
                                        onToggleVolume = { showVolumeControl = true }
                                    )
                                    2 -> MiniLayoutQueue(
                                        viewModel = viewModel,
                                        isPlaying = isPlaying,
                                        isShuffleOn = isShuffleOn,
                                        repeatMode = repeatMode,
                                        btnTint = btnTint,
                                        adaptiveColor = adaptiveColor,
                                        currentSong = currentSong,
                                        onNowPlayingClick = onNowPlayingClick,
                                        isPillAtBottom = isPillAtBottom,
                                        onToggleVolume = { showVolumeControl = true }
                                    )
                                }
                            }
                        }
                    }
                }
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
            viewModel = viewModel,
            onDismiss = { viewModel.closeRecapCard() }
        )
    }

    if (showPlaylistSelectionDialog != null) {
        PlaylistSelectionDialog(
            song = showPlaylistSelectionDialog!!,
            playlists = playlists,
            onDismiss = { showPlaylistSelectionDialog = null },
            onSelect = { playlist ->
                viewModel.addSongsToPlaylist(playlist, listOf(showPlaylistSelectionDialog!!.id))
                showPlaylistSelectionDialog = null
            }
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
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
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
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
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
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
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
                            Column(modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp)) {
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
                            Column(modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp)) {
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
fun PlaylistSelectionDialog(
    song: Song,
    playlists: List<com.ianocent.musicplayer.data.Playlist>,
    onDismiss: () -> Unit,
    onSelect: (com.ianocent.musicplayer.data.Playlist) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Add to Playlist", fontWeight = FontWeight.Bold) },
        text = {
            if (playlists.isEmpty()) {
                Text("No playlists found. Create one first.", color = Color.Gray)
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(playlists) { playlist ->
                        RoundedClickableRow(
                            onClick = { onSelect(playlist) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.PlaylistPlay, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text(playlist.name, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        },
        confirmButton = {},
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
                recap.topSongs.take(5).forEach { song ->
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

            Spacer(Modifier.height(8.dp))

            val commentScrollState = rememberScrollState()
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 70.dp),
                shape = RoundedCornerShape(16.dp),
                color = accentColor.copy(alpha = 0.06f)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = recap.tasteComment,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .padding(end = 12.dp)
                            .verticalScroll(commentScrollState),
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        fontStyle = FontStyle.Italic
                    )
                    
                    if (commentScrollState.maxValue > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 4.dp, top = 8.dp, bottom = 8.dp)
                                .fillMaxHeight()
                                .width(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(accentColor.copy(alpha = 0.1f))
                        ) {
                            val scrollFraction = commentScrollState.value.toFloat() / commentScrollState.maxValue.toFloat()
                            val thumbHeightFraction = 0.3f
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(thumbHeightFraction)
                                    .align(Alignment.TopCenter)
                                    .graphicsLayer {
                                        translationY =
                                            (size.height * (1f - thumbHeightFraction) * scrollFraction)
                                    }
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(accentColor.copy(alpha = 0.5f))
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Button(
                onClick = onGenerateCard,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                shape = RoundedCornerShape(12.dp)
            ) {
//                Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save to Gallery")
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

@Composable
fun MiniSongInfo(
    song: Song,
    viewModel: MusicViewModel,
    adaptiveColor: Color,
    onNowPlayingClick: () -> Unit = {},
    onMiniPlayerLayout: (ElementRect, ElementRect) -> Unit = { _, _ -> },
    isNowPlayingVisible: Boolean = false,
    isPlaying: Boolean = false,
    isBuffering: Boolean = false,
    currentPosition: Long = 0,
    duration: Long = 0,
    isPillAtBottom: Boolean,
    onToggleVolume: () -> Unit
) {
    var art by remember(song.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(song.id) {
        viewModel.getCachedArt(song) { bitmap -> art = bitmap?.asImageBitmap() }
    }

    val rotationAnim = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            rotationAnim.animateTo(
                targetValue = rotationAnim.value + 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(8000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        } else {
            rotationAnim.stop()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .pointerInput(isPillAtBottom) {
                var accDx = 0f
                var accDy = 0f
                detectDragGestures(
                    onDragEnd = {
                        val absDx = kotlin.math.abs(accDx)
                        val absDy = kotlin.math.abs(accDy)
                        val threshold = 30f

                        if (absDy > absDx && absDy > threshold) {
                            if (accDy < 0 && isPillAtBottom) {
                                // Swipe up when at bottom -> move to top
                                viewModel.setPillAtBottom(false)
                            } else if (accDy > 0 && !isPillAtBottom) {
                                // Swipe down when at top -> move to bottom
                                viewModel.setPillAtBottom(true)
                            }
                        } else if (absDx > absDy && absDx > threshold) {
                            if (accDx < -threshold) {
                                // Swipe left -> show volume
                                onToggleVolume()
                            }
                        }
                        accDx = 0f
                        accDy = 0f
                    },
                    onDragCancel = { accDx = 0f; accDy = 0f }
                ) { change, dragAmount ->
                    change.consume()
                    accDx += dragAmount.x
                    accDy += dragAmount.y
                }
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .graphicsLayer {
                    if (isNowPlayingVisible) {
                        alpha = 0f
                    } else {
                        rotationZ = rotationAnim.value
                        alpha = 1f
                    }
                }
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
        Spacer(Modifier.width(12.dp))
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
        val minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(currentPosition)
        val seconds = java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(currentPosition) % 60
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

@Composable
fun MiniLayoutDefault(
    viewModel: MusicViewModel,
    isPlaying: Boolean,
    isShuffleOn: Boolean,
    repeatMode: Int,
    btnBg: Color,
    btnTint: Color,
    hasBg: Boolean,
    adaptiveColor: Color,
    minibarTextColor: Color,
    currentSong: Song? = null,
    onNowPlayingClick: () -> Unit = {},
    onMiniPlayerLayout: (ElementRect, ElementRect) -> Unit = { _, _ -> },
    isNowPlayingVisible: Boolean = false,
    isBuffering: Boolean = false,
    currentPosition: Long = 0,
    duration: Long = 0,
    isPillAtBottom: Boolean = false,
    onToggleVolume: () -> Unit = {}
) {
    val activeBg = adaptiveColor.copy(alpha = 0.35f)
    val inactBg = adaptiveColor.copy(alpha = 0.18f)
    val inactTint = minibarTextColor.copy(alpha = 0.75f)
    Column(modifier = Modifier.fillMaxWidth()) {
        if (currentSong != null && isPillAtBottom) {
            MiniSongInfo(
                song = currentSong,
                viewModel = viewModel,
                adaptiveColor = adaptiveColor,
                onNowPlayingClick = onNowPlayingClick,
                onMiniPlayerLayout = onMiniPlayerLayout,
                isNowPlayingVisible = isNowPlayingVisible,
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                currentPosition = currentPosition,
                duration = duration,
                isPillAtBottom = true,
                onToggleVolume = onToggleVolume
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .pointerInput(Unit) {
                    var accDx = 0f
                    var accDy = 0f
                    detectDragGestures(
                        onDragEnd = {
                            val absDx = kotlin.math.abs(accDx)
                            val absDy = kotlin.math.abs(accDy)
                            val threshold = 40f

                            if (absDy > absDx && absDy > threshold) {
                                if (accDy < 0) {
                                    // SWIPE UP → NowPlayingScreen
                                    onNowPlayingClick()
                                } else {
                                    // SWIPE DOWN → cycle layout
                                    viewModel.setMiniLayoutIndex((viewModel.miniLayoutIndex.value + 1) % 3)
                                }
                            }
                            accDx = 0f
                            accDy = 0f
                        },
                        onDragCancel = { accDx = 0f; accDy = 0f }
                    ) { change, dragAmount ->
                        change.consume()
                        accDx += dragAmount.x
                        accDy += dragAmount.y
                    }
                }
        ) {
            MiniControlButton(
                icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                onClick = { viewModel.togglePlayPause() },
                bg = adaptiveColor.copy(alpha = 0.2f), tint = adaptiveColor, size = 40.dp
            )
            MiniControlButton(Icons.Rounded.SkipPrevious, { viewModel.playPrevious() },
                adaptiveColor.copy(alpha = 0.2f), adaptiveColor, 40.dp)
            MiniControlButton(Icons.Rounded.SkipNext, { viewModel.playNext() },
                adaptiveColor.copy(alpha = 0.2f), adaptiveColor, 40.dp)
            MiniControlButton(
                Icons.Rounded.Shuffle, { viewModel.toggleShuffle() },
                if (isShuffleOn) activeBg else inactBg,
                if (isShuffleOn) adaptiveColor else inactTint, 40.dp
            )
            MiniControlButton(
                if (repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                { viewModel.toggleRepeat() },
                if (repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) activeBg else inactBg,
                if (repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) adaptiveColor else inactTint, 40.dp
            )
        }
    }
}

@Composable
fun MiniLayoutFloating(
    viewModel: MusicViewModel,
    isPlaying: Boolean,
    btnTint: Color,
    adaptiveColor: Color,
    isBuffering: Boolean,
    currentSong: Song? = null,
    onNowPlayingClick: () -> Unit = {},
    isPillAtBottom: Boolean = false,
    onToggleVolume: () -> Unit = {}
) {
    val shuffleOn by viewModel.isShuffleOn.collectAsState()
    val repeat by viewModel.repeatMode.collectAsState()
    val inactTint = btnTint.copy(alpha = 0.75f)
    val inactBg = adaptiveColor.copy(alpha = 0.18f)
    val activeBg = adaptiveColor.copy(alpha = 0.35f)
    Column(modifier = Modifier.fillMaxWidth()) {
        if (currentSong != null && isPillAtBottom) {
            MiniSongInfo(
                song = currentSong,
                viewModel = viewModel,
                adaptiveColor = adaptiveColor,
                onNowPlayingClick = onNowPlayingClick,
                isPillAtBottom = true,
                onToggleVolume = onToggleVolume
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .pointerInput(Unit) {
                    var accDx = 0f
                    var accDy = 0f
                    detectDragGestures(
                        onDragEnd = {
                            val absDx = kotlin.math.abs(accDx)
                            val absDy = kotlin.math.abs(accDy)
                            val threshold = 40f

                            if (absDy > absDx && absDy > threshold) {
                                if (accDy < 0) {
                                    // SWIPE UP → NowPlayingScreen
                                    onNowPlayingClick()
                                } else {
                                    // SWIPE DOWN → cycle layout
                                    viewModel.setMiniLayoutIndex((viewModel.miniLayoutIndex.value + 1) % 3)
                                }
                            }
                            accDx = 0f
                            accDy = 0f
                        },
                        onDragCancel = { accDx = 0f; accDy = 0f }
                    ) { change, dragAmount ->
                        change.consume()
                        accDx += dragAmount.x
                        accDy += dragAmount.y
                    }
                },
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiniControlButton(Icons.Rounded.Shuffle, { viewModel.toggleShuffle() },
                bg = if (shuffleOn) activeBg else inactBg,
                tint = if (shuffleOn) adaptiveColor else inactTint, size = 36.dp)
            MiniControlButton(Icons.Rounded.SkipPrevious, { viewModel.playPrevious() },
                inactBg, btnTint, 36.dp)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(adaptiveColor.copy(alpha = 0.2f))
                    .clickable { viewModel.togglePlayPause() },
                contentAlignment = Alignment.Center
            ) {
                if (isBuffering) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp, color = adaptiveColor)
                } else {
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        null, tint = adaptiveColor, modifier = Modifier.size(28.dp)
                    )
                }
            }
            MiniControlButton(Icons.Rounded.SkipNext, { viewModel.playNext() },
                inactBg, btnTint, 36.dp)
            MiniControlButton(
                if (repeat == androidx.media3.common.Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                { viewModel.toggleRepeat() },
                bg = if (repeat != androidx.media3.common.Player.REPEAT_MODE_OFF) activeBg else inactBg,
                tint = if (repeat != androidx.media3.common.Player.REPEAT_MODE_OFF) adaptiveColor else inactTint, size = 36.dp)
        }
    }
}

@Composable
fun MiniLayoutQueue(
    viewModel: MusicViewModel,
    isPlaying: Boolean,
    isShuffleOn: Boolean,
    repeatMode: Int,
    btnTint: Color,
    adaptiveColor: Color,
    currentSong: Song? = null,
    onNowPlayingClick: () -> Unit = {},
    isPillAtBottom: Boolean = false,
    onToggleVolume: () -> Unit = {}
) {
    val inactTint = btnTint.copy(alpha = 0.75f)
    val inactBg = adaptiveColor.copy(alpha = 0.18f)
    val activeBg = adaptiveColor.copy(alpha = 0.35f)
    Column(modifier = Modifier.fillMaxWidth()) {
        if (currentSong != null && isPillAtBottom) {
            MiniSongInfo(
                song = currentSong,
                viewModel = viewModel,
                adaptiveColor = adaptiveColor,
                onNowPlayingClick = onNowPlayingClick,
                isPillAtBottom = true,
                onToggleVolume = onToggleVolume
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .pointerInput(Unit) {
                    var accDx = 0f
                    var accDy = 0f
                    detectDragGestures(
                        onDragEnd = {
                            val absDx = kotlin.math.abs(accDx)
                            val absDy = kotlin.math.abs(accDy)
                            val threshold = 40f

                            if (absDy > absDx && absDy > threshold) {
                                if (accDy < 0) {
                                    // SWIPE UP → NowPlayingScreen
                                    onNowPlayingClick()
                                } else {
                                    // SWIPE DOWN → cycle layout
                                    viewModel.setMiniLayoutIndex((viewModel.miniLayoutIndex.value + 1) % 3)
                                }
                            }
                            accDx = 0f
                            accDy = 0f
                        },
                        onDragCancel = { accDx = 0f; accDy = 0f }
                    ) { change, dragAmount ->
                        change.consume()
                        accDx += dragAmount.x
                        accDy += dragAmount.y
                    }
                },
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MiniControlButton(Icons.Rounded.SkipPrevious, { viewModel.playPrevious() },
                    inactBg, btnTint, 36.dp)
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(adaptiveColor.copy(alpha = 0.2f))
                        .clickable { viewModel.togglePlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        null, tint = adaptiveColor, modifier = Modifier.size(24.dp)
                    )
                }
                MiniControlButton(Icons.Rounded.SkipNext, { viewModel.playNext() },
                    inactBg, btnTint, 36.dp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MiniControlButton(Icons.Rounded.Shuffle, { viewModel.toggleShuffle() },
                    if (isShuffleOn) activeBg else inactBg,
                    if (isShuffleOn) adaptiveColor else inactTint, 36.dp)
                MiniControlButton(
                    if (repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    { viewModel.toggleRepeat() },
                    if (repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) activeBg else inactBg,
                    if (repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) adaptiveColor else inactTint, 36.dp)
            }
        }
    }
}

@Composable
fun TrendingCard(
    song: com.ianocent.musicplayer.data.Song,
    viewModel: MusicViewModel,
    adaptiveColor: Color,
    modifier: Modifier = Modifier
) {
    var highResArt by remember(song.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var isLoaded by remember(song.id) { mutableStateOf(false) }
    LaunchedEffect(song.id) {
        viewModel.getHighResArt(song) { b -> highResArt = b; isLoaded = true }
    }

    val isPlaceholder = song.uri.toString().startsWith("ytmusic://placeholder/")
    var showFormatDialog by remember { mutableStateOf(false) }
    var formats by remember { mutableStateOf<List<com.ianocent.musicplayer.data.AudioFormat>>(emptyList()) }
    val trendingSongs = viewModel.trendingSongs.collectAsState().value

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                if (isPlaceholder) {
                    viewModel.getAudioFormats(song) { f ->
                        formats = f
                        showFormatDialog = true
                    }
                } else {
                    val others = trendingSongs.filter { it.id != song.id }
                    viewModel.setQueue(listOf(song) + others, startSong = song)
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Crossfade(
                    targetState = isLoaded && highResArt != null,
                    animationSpec = tween(400),
                    label = "art_crossfade"
                ) { loaded ->
                    if (loaded) {
                        Image(
                            bitmap = highResArt!!.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(adaptiveColor.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.MusicNote,
                                null,
                                tint = adaptiveColor.copy(alpha = 0.3f),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }

                if (isPlaceholder) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(adaptiveColor.copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = if (adaptiveColor.luminance() > 0.5f) Color.Black else Color.White
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(adaptiveColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            null,
                            tint = if (adaptiveColor.luminance() > 0.5f) Color.Black else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    song.artist,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.Gray
                )
            }
        }
    }

    if (showFormatDialog) {
        AlertDialog(
            onDismissRequest = { showFormatDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(28.dp),
            title = { Text("Play Stream", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Choose quality:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    formats.forEach { fmt ->
                        TextButton(
                            onClick = {
                                showFormatDialog = false
                                val others = trendingSongs.filter { it.id != song.id }
                                viewModel.setQueue(listOf(song) + others, startSong = song)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(fmt.qualityLabel)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showFormatDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun GenreCard(
    genre: MusicViewModel.Genre,
    isLoading: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MusicViewModel? = null,
    artSong: Song? = null
) {
    var artBitmap by remember(artSong?.id) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(artSong?.id) {
        if (artSong != null && viewModel != null) {
            viewModel.getHighResArt(artSong) { bitmap ->
                artBitmap = bitmap?.asImageBitmap()
            }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = accentColor.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            if (artBitmap != null) {
                Image(
                    bitmap = artBitmap!!,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp,
                        color = Color.White
                    )
                } else if (artBitmap == null) {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    genre.name,
                    fontWeight = FontWeight.Bold,
                    color = if (artBitmap != null) Color.White else accentColor,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
fun VoiceSpectrum(
    rmsLevel: Float,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val barCount = 7
    val infiniteTransition = rememberInfiniteTransition(label = "spectrum")
    val barHeights = remember { List(barCount) { 0f } }

    // Each bar gets its own animated float with different phase
    val bars = barHeights.mapIndexed { index, _ ->
        infiniteTransition.animateFloat(
            initialValue = 4f,
            targetValue = (4f + rmsLevel * 28f * (0.5f + (index * 0.15f))).coerceIn(4f, 48f),
            animationSpec = infiniteRepeatable(
                animation = tween(300 + index * 50, delayMillis = index * 80),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar$index"
        )
    }

    Box(
        modifier = modifier.size(40.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            bars.forEach { barValue ->
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(barValue.value.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(accentColor.copy(alpha = 0.8f))
                )
            }
        }
    }
}
@Preview(showBackground = true, name = "Mini Player Default")
@Composable
fun MiniPlayerPreview() {
    IanPlayerTheme(darkTheme = true) {
        Box(modifier = Modifier.padding(16.dp).background(Color.Black)) {
            MiniLayoutDefault(
                viewModel = viewModel(),
                isPlaying = true,
                isShuffleOn = false,
                repeatMode = 0,
                btnBg = Color.Transparent,
                btnTint = Color.White,
                hasBg = true,
                adaptiveColor = Color(0xFF1DB954),
                minibarTextColor = Color.White,
                currentSong = Song(1, "Sample Song Title", "Sample Artist", 300000, android.net.Uri.EMPTY),
                isPillAtBottom = true
            )
        }
    }
}

@Preview(showBackground = true, name = "Mini Player Floating")
@Composable
fun PreviewMiniPlayerFloating() {
    IanPlayerTheme(darkTheme = true) {
        Box(modifier = Modifier.padding(16.dp).background(Color.Black)) {
            MiniLayoutFloating(
                viewModel = viewModel(),
                isPlaying = false,
                btnTint = Color.White,
                adaptiveColor = Color(0xFFE91E63),
                isBuffering = false,
                currentSong = Song(2, "Gerimis Mengundang", "Slam", 280000, android.net.Uri.EMPTY),
                isPillAtBottom = true
            )
        }
    }
}
