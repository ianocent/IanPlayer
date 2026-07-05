package com.ianocent.musicplayer

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ianocent.musicplayer.ui.theme.IanPlayerTheme
import com.ianocent.musicplayer.ui.NowPlayingScreen
import com.ianocent.musicplayer.viewmodel.MusicViewModel
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.ImageBitmap
import com.ianocent.musicplayer.data.Song
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.text.font.FontWeight

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MusicViewModel = viewModel()
            val isDarkMode by viewModel.isDarkMode.collectAsState()

            IanPlayerTheme(darkTheme = isDarkMode, dynamicColor = false) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppNavHost(viewModel = viewModel, modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}
@Composable
fun AppNavHost(viewModel: MusicViewModel, modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "listing") {
        composable("listing") {
            ListingScreen(
                viewModel = viewModel,
                modifier = modifier,
                onNowPlayingClick = { navController.navigate("now_playing") }
            )
        }
        composable("now_playing") {
            NowPlayingScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
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
    var selectedTab by remember { mutableStateOf(0) }

    if (hasPermission) {
        Column(modifier = modifier.fillMaxSize()) {

            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Ian Player", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                IconButton(onClick = { viewModel.toggleDarkMode() }) {
                    Icon(
                        imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "Toggle theme"
                    )
                }
            }

            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Songs") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Playlists") })
            }

            var showCreateDialog by remember { mutableStateOf(false) }
            val playlists by viewModel.playlists.collectAsState()

// ganti bagian "when (selectedTab)"
            when (selectedTab) {
                0 -> LazyColumn(modifier = Modifier.weight(1f)) {
                    items(songs, key = { it.id }) { song -> SongRow(song, viewModel) }
                }
                1 -> Box(modifier = Modifier.weight(1f)) {
                    if (playlists.isEmpty()) {
                        Text(
                            "Belum ada playlist. Tap + untuk buat baru",
                            modifier = Modifier.align(Alignment.Center),
                            color = Color.Gray
                        )
                    } else {
                        LazyColumn {
                            items(playlists, key = { it.id }) { playlist ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(playlist.name, style = MaterialTheme.typography.bodyLarge)
                                        Text("${playlist.songIds.size} lagu", style = MaterialTheme.typography.bodySmall)
                                    }
                                    IconButton(onClick = { viewModel.deletePlaylist(playlist) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                                    }
                                }
                            }
                        }
                    }

                    FloatingActionButton(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Create Playlist")
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

            currentSong?.let { song ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onNowPlayingClick() }
                ) {
                    Slider(
                        value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                        onValueChange = { fraction -> viewModel.seekTo((fraction * duration).toLong()) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(song.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                            Text(song.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                        IconButton(onClick = { viewModel.playPrevious() }) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous")
                        }
                        IconButton(onClick = { viewModel.togglePlayPause() }) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause"
                            )
                        }
                        IconButton(onClick = { viewModel.playNext() }) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Next")
                        }
                    }
                }
            }
        }
    } else {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Izin akses lagu diperlukan")
        }
    }
}

@Composable
fun SongRow(song: Song, viewModel: MusicViewModel) {
    var art by remember(song.id) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(song.id) {
        viewModel.getCachedArt(song) { bitmap -> art = bitmap?.asImageBitmap() }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { viewModel.playSong(song) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                .background(if (art == null) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            if (art != null) {
                Image(bitmap = art!!, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Default.MusicNote, contentDescription = null)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(song.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(song.artist, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
    }
}
@Composable
fun CreatePlaylistDialog(
    songs: List<Song>,
    onDismiss: () -> Unit,
    onCreate: (String, List<Long>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    val selectedIds = remember { mutableStateListOf<Long>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buat Playlist Baru") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama Playlist") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("Pilih lagu:", style = MaterialTheme.typography.bodySmall)
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(songs, key = { it.id }) { song ->
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
                                Text(song.title, maxLines = 1)
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