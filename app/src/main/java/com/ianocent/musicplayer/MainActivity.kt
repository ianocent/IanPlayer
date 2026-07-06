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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.ianocent.musicplayer.data.Song
import com.ianocent.musicplayer.ui.NowPlayingScreen
import com.ianocent.musicplayer.ui.theme.IanPlayerTheme
import com.ianocent.musicplayer.viewmodel.MusicViewModel
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.scaleIn
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Person
import java.util.concurrent.TimeUnit
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.*
import androidx.compose.animation.animateContentSize

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
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppNavHost(viewModel = viewModel, innerPadding = innerPadding)
                }
            }
        }
    }
}

@Composable
fun AppNavHost(viewModel: MusicViewModel, innerPadding: PaddingValues) {
    var showNowPlaying by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        ListingScreen(
            viewModel = viewModel,
            modifier = Modifier.padding(innerPadding),
            onNowPlayingClick = { showNowPlaying = true }
        )

        AnimatedVisibility(
            visible = showNowPlaying,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(350, easing = androidx.compose.animation.core.FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(300)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(250)),
            modifier = Modifier.zIndex(1f)
        ) {
            NowPlayingScreen(
                viewModel = viewModel,
                onBack = { showNowPlaying = false }
            )
        }
    }
}

@Composable
fun ListingScreen(
    viewModel: MusicViewModel,
    modifier: Modifier = Modifier,
    onNowPlayingClick: () -> Unit
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
    var selectedPlaylist by remember { mutableStateOf<com.ianocent.musicplayer.data.Playlist?>(null) }

    val playlists by viewModel.playlists.collectAsState()
    val isShuffleOn by viewModel.isShuffleOn.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        placeholder = { Text("Search songs...", color = Color.Gray) },
                        singleLine = true,
                        shape = CircleShape,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = Color.Gray) },
                        trailingIcon = {
                            IconButton(onClick = { isSearchActive = false; searchQuery = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.Gray)
                            }
                        }
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "IanPlayer",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Row {
                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(Icons.Rounded.Search, contentDescription = "Search")
                            }
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
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ---------- 2. LISTENING TO PILL ----------
        AnimatedVisibility(
            visible = currentSong != null,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
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
                        .clickable { onNowPlayingClick() }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
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
                                        .background(Color.Red)
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
                    .padding(horizontal = 26.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (page == 0) {
                    TabItem("Songs", selectedTab == 0, adaptiveColor) { selectedTab = 0 }
                    TabItem("Playlists", selectedTab == 1, adaptiveColor) { selectedTab = 1 }
                    IconButton(
                        onClick = { tabPage = 1 },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) // Rounded background
                    ) {
                        Icon(Icons.Rounded.ChevronRight, contentDescription = "More Tabs", tint = Color.Gray)
                    }
                } else {
                    IconButton(
                        onClick = { tabPage = 0 },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) // Rounded background
                    ) {
                        Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous Tabs", tint = Color.Gray)
                    }
                    TabItem("Albums", selectedTab == 2, adaptiveColor) { selectedTab = 2 }
                    TabItem("Artists", selectedTab == 3, adaptiveColor) { selectedTab = 3 }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ---------- 4. MAIN FLOATING LIST CONTAINER ----------
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(if (isDarkMode) Color(0xFF1C1C1E) else Color(0xFFF2F2F7))
        ) {
            Crossfade(targetState = selectedTab) { tab ->
                when (tab) {
                    0 -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 90.dp)
                    ) {
                        items(filteredSongs, key = { it.id }) { song ->
                            SongRow(song, viewModel, customOnClick = {
                                // Reset antrean ke daftar lagu utama pas di-klik
                                viewModel.setQueue(filteredSongs, startSong = song)
                            })
                        }
                    }

                    1 -> Box(modifier = Modifier.fillMaxSize()) {
                        if (selectedPlaylist != null) {
                            PlaylistDetailView(
                                playlist = selectedPlaylist!!,
                                viewModel = viewModel,
                                adaptiveColor = adaptiveColor,
                                minibarTextColor = minibarTextColor,
                                onBack = { selectedPlaylist = null },
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
                                LazyColumn(contentPadding = PaddingValues(top = 16.dp, bottom = 90.dp)) {
                                    items(playlists, key = { it.id }) { playlist ->
                                        PlaylistCard(
                                            playlist = playlist,
                                            onClick = { selectedPlaylist = playlist },
                                            onDelete = { viewModel.deletePlaylist(playlist) }
                                        )
                                    }
                                }
                            }
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

                    2 -> {
                        val albumGroups = remember(songs) {
                            songs.groupBy { it.artist }.entries.toList() // fallback grouping, MediaStore Song belum ada field album
                        }
                        LazyColumn(contentPadding = PaddingValues(top = 16.dp, bottom = 90.dp)) {
                            items(albumGroups) { (name, groupSongs) ->
                                GroupRow(name = name, count = groupSongs.size)
                            }
                        }
                    }

                    3 -> {
                        val artistGroups = remember(songs) {
                            songs.groupBy { it.artist }.entries.toList()
                        }
                        LazyColumn(contentPadding = PaddingValues(top = 16.dp, bottom = 90.dp)) {
                            items(artistGroups) { (name, groupSongs) ->
                                GroupRow(name = name, count = groupSongs.size)
                            }
                        }
                    }
                }
            }
        }

        // ---------- 5. MINI PLAYER BAR (Rounded per icon, no backplate) ----------
        AnimatedVisibility(
            visible = currentSong != null,
            enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center, // Biar posisi minibar di tengah
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp) // Dikasih margin biar width-nya lebih ramping dari listing
                    .padding(top = 16.dp, bottom = 20.dp)
            ) {
                // Gunakan MiniControlButton yang ukurannya lebih kecil (42.dp)
                MiniControlButton(
                    icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    onClick = { viewModel.togglePlayPause() },
                    bg = adaptiveColor,
                    tint = minibarTextColor,
                    size = 42.dp // Size lebih kecil
                )
                Spacer(Modifier.width(8.dp))
                MiniControlButton(Icons.Rounded.SkipPrevious, { viewModel.playPrevious() }, adaptiveColor, minibarTextColor, 42.dp)
                Spacer(Modifier.width(8.dp))
                MiniControlButton(Icons.Rounded.SkipNext, { viewModel.playNext() }, adaptiveColor, minibarTextColor, 42.dp)
                Spacer(Modifier.width(8.dp))
                MiniControlButton(
                    Icons.Rounded.Shuffle,
                    { viewModel.toggleShuffle() },
                    if (isShuffleOn) adaptiveColor else MaterialTheme.colorScheme.surfaceVariant,
                    if (isShuffleOn) minibarTextColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    42.dp
                )
                Spacer(Modifier.width(8.dp))
                MiniControlButton(
                    if (repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    { viewModel.toggleRepeat() },
                    if (repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) adaptiveColor else MaterialTheme.colorScheme.surfaceVariant,
                    if (repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) minibarTextColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    42.dp
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
fun SongRow(song: Song, viewModel: MusicViewModel, customOnClick: (() -> Unit)? = null) {
    var art by remember(song.id) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(song.id) {
        viewModel.getCachedArt(song) { bitmap -> art = bitmap?.asImageBitmap() }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { customOnClick?.invoke() ?: viewModel.playSong(song) }
            .padding(horizontal = 20.dp, vertical = 8.dp),
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
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
    onDelete: () -> Unit
) {
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
            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${playlist.songIds.size} Songs",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Rounded.Delete, contentDescription = "Delete playlist", tint = Color.Gray)
        }
    }
}
@Composable
fun GroupRow(name: String, count: Int) {
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
            Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Text("$count lagu", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
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

            val playlistSongs = viewModel.getSongsInPlaylist(playlist)
            if (playlistSongs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    Text("Playlist ini kosong", color = Color.Gray)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 90.dp)) {
                    items(playlistSongs, key = { it.id }) { song ->
                        SongRow(song, viewModel, customOnClick = { viewModel.setQueue(playlistSongs, startSong = song) })
                    }
                }
            }
        }

        // Floating Buttons ala figma (Back & Shuffle) di kanan bawah
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FloatingActionButton(
                onClick = onBack,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                shape = CircleShape
            ) {
                Icon(Icons.Rounded.ArrowBackIosNew, contentDescription = "Back")
            }
            FloatingActionButton(
                onClick = onShuffle, // Ganti jadi Shuffle persis figma lu
                containerColor = adaptiveColor,
                contentColor = minibarTextColor,
                shape = CircleShape
            ) {
                Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle Playlist")
            }
        }
    }
}

@Composable
fun CreatePlaylistDialog(songs: List<Song>, onDismiss: () -> Unit, onCreate: (String, List<Long>) -> Unit) {
    var name by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    val selectedIds = remember { mutableStateListOf<Long>() }
    val filteredSongs = remember(songs, searchQuery) {
        if (searchQuery.isBlank()) songs
        else songs.filter { it.title.contains(searchQuery, true) || it.artist.contains(searchQuery, true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buat Playlist Baru") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Nama Playlist") }, modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = searchQuery, onValueChange = { searchQuery = it },
                    label = { Text("Cari lagu...") }, modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 250.dp)) {
                    items(filteredSongs, key = { it.id }) { song ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (selectedIds.contains(song.id)) selectedIds.remove(song.id)
                                    else selectedIds.add(song.id)
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selectedIds.contains(song.id),
                                onCheckedChange = {
                                    if (it) selectedIds.add(song.id) else selectedIds.remove(song.id)
                                }
                            )
                            Column {
                                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(song.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onCreate(name, selectedIds.toList()) },
                enabled = name.isNotBlank() && selectedIds.isNotEmpty()
            ) { Text("Buat") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}