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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import kotlinx.coroutines.launch

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

    var selectedTab by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editingPlaylist by remember { mutableStateOf<com.ianocent.musicplayer.data.Playlist?>(null) }
    var selectedPlaylistId by remember { mutableStateOf<Long?>(null) }
    val playlists by viewModel.playlists.collectAsState()
    val selectedPlaylist = remember(playlists, selectedPlaylistId) {
        playlists.find { it.id == selectedPlaylistId }
    }
    val isShuffleOn by viewModel.isShuffleOn.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()

    val isUpdateAvailable by viewModel.isUpdateAvailable.collectAsState()
    val updateInfo by viewModel.updateInfo.collectAsState()
    val isDownloading by viewModel.isDownloading.collectAsState()

    val paletteColors by viewModel.paletteColors.collectAsState()
    var controlBgColor by remember { mutableStateOf<Color?>(null) }
    var currentPaletteIndex by remember { mutableStateOf(0) }
    var showVolumeControl by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val audioManager = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager }
    val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
    var currentVolume by remember { mutableStateOf(audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)) }

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
                            "ıanplayer",
                            style = MaterialTheme.typography.titleLarge,
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
                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(top = 16.dp, bottom = 90.dp, end = 20.dp)
                            ) {
                                items(filteredSongs, key = { it.id }) { song ->
                                    SongRow(song, viewModel, customOnClick = {
                                        viewModel.setQueue(filteredSongs, startSong = song)
                                    })
                                }
                            }
                            DraggableScrollbar(listState, adaptiveColor)
                        }
                    }

                    1 -> { // ALBUMS
                        val albumGroups = remember(songs) {
                            songs.groupBy { it.album }.entries.toList()
                        }
                        val listState = rememberLazyListState()
                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(top = 16.dp, bottom = 90.dp, end = 20.dp)
                            ) {
                                items(albumGroups) { (albumName, groupSongs) ->
                                    AlbumRow(album = albumName, songs = groupSongs, viewModel = viewModel, count = groupSongs.size)
                                }
                            }
                            DraggableScrollbar(listState, adaptiveColor)
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

                                Box(modifier = Modifier.fillMaxSize()) {
                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(top = 16.dp, bottom = 90.dp, end = 20.dp)
                                    ) {
                                        items(streamSongs, key = { it.id }) { song ->
                                            SongRow(song, viewModel, customOnClick = {
                                                viewModel.setQueue(streamSongs, startSong = song)
                                            })
                                        }
                                    }
                                    DraggableScrollbar(listState, adaptiveColor)
                                }
                            }
                        }
                    }

                    3 -> { // PLAYLISTS
                        val listState = rememberLazyListState()
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
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        LazyColumn(
                                            state = listState,
                                            modifier = Modifier.fillMaxSize(),
                                            contentPadding = PaddingValues(top = 16.dp, bottom = 90.dp, end = 20.dp)
                                        ) {
                                            items(playlists, key = { it.id }) { playlist ->
                                                PlaylistCard(
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
                                        DraggableScrollbar(listState, adaptiveColor)
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
    modifier: Modifier = Modifier
) {
    var art by remember(song.id) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(song.id) {
        viewModel.getCachedArt(song) { bitmap -> art = bitmap?.asImageBitmap() }
    }

    // Animasi transisi warna teks biar sinkron sama background
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
        Column {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = animatedTitleColor, // <- Pake warna yang dianimasikan
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = animatedArtistColor, // <- Pake warna yang dianimasikan
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
        if (playlist.imageUri != null && playlist.songIds.isNotEmpty()) {
            val firstSong = viewModel.getSongsInPlaylist(playlist).firstOrNull()
            firstSong?.let { song ->
                viewModel.getCachedArt(song) { bitmap -> art = bitmap?.asImageBitmap() }
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
                color = animatedTitleColor, // <- Pake warna yang dianimasikan
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${playlist.songIds.size} Songs",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Rounded.Edit, contentDescription = "Edit playlist", tint = accentColor)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Rounded.Delete, contentDescription = "Delete playlist", tint = accentColor.copy(alpha = 0.7f))
        }
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
fun AlbumRow(album: String, songs: List<Song>, viewModel: MusicViewModel, count: Int) {
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
                color = animatedTitleColor // <- Pake warna yang dianimasikan
            )
            Text("$count songs", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
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
