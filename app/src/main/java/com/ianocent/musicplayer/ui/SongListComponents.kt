package com.ianocent.musicplayer.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ianocent.musicplayer.data.AudioFormat
import com.ianocent.musicplayer.data.Playlist
import com.ianocent.musicplayer.data.Song
import com.ianocent.musicplayer.viewmodel.MusicViewModel
import kotlin.math.roundToInt

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
    playlist: Playlist,
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
    playlist: Playlist,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    viewModel: MusicViewModel,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val density = LocalDensity.current
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
        PlaylistCardSheet(
            playlist = playlist,
            viewModel = viewModel,
            accentColor = accentColor,
            onDismiss = { showPlaylistCardSheet = false }
        )
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
fun SwipeableSongRow(
    song: Song,
    viewModel: MusicViewModel,
    customOnClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    adaptiveColor: Color
) {
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 72.dp.toPx() }
    val maxSwipePx = with(density) { 88.dp.toPx() }

    var offsetX by remember { mutableStateOf(0f) }
    var isSwipedOpen by remember { mutableStateOf(false) }
    var showActionDialog by remember { mutableStateOf(false) }
    var showSongCardSheet by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showPlaylistSelector by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var downloadFormats by remember { mutableStateOf<List<AudioFormat>>(emptyList()) }
    var isLoadingFormats by remember { mutableStateOf(false) }

    // Low-res for the list
    var songArtLowRes by remember(song.id) { mutableStateOf<Bitmap?>(null) }
    // High-res for the card
    var songArtHighRes by remember(song.id) { mutableStateOf<Bitmap?>(null) }

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
                    .padding(start = 16.dp)
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

                    RoundedClickableRow(
                        onClick = {
                            showActionDialog = false
                            viewModel.playNext(song)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = null, tint = adaptiveColor, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Play Next", fontWeight = FontWeight.SemiBold)
                    }

                    RoundedClickableRow(
                        onClick = {
                            showActionDialog = false
                            viewModel.addToQueue(song)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.PlaylistAdd, contentDescription = null, tint = adaptiveColor, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Add to Queue", fontWeight = FontWeight.SemiBold)
                    }

                    if (song.isStream) {
                        RoundedClickableRow(
                            onClick = {
                                isLoadingFormats = true
                                showActionDialog = false
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
                                showPlaylistSelector = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.PlaylistPlay, contentDescription = null, tint = adaptiveColor, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("Add to Playlists", fontWeight = FontWeight.SemiBold)
                        }

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
        SongCardSheet(
            song = song,
            albumArt = songArtHighRes ?: songArtLowRes,
            accentColor = adaptiveColor,
            onDismiss = { showSongCardSheet = false }
        )
    }

    if (showEditDialog) {
        EditSongDialog(
            song = song,
            onDismiss = { showEditDialog = false },
            onUpdate = { newTitle, newArtist, newImageUri ->
                viewModel.updateSongInfo(song.id, newTitle, newArtist, newImageUri)
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

    if (showPlaylistSelector) {
        val allPlaylists by viewModel.playlists.collectAsState()
        PlaylistSelectionDialog(
            song = song,
            playlists = allPlaylists,
            onDismiss = { showPlaylistSelector = false },
            onSelect = { playlist ->
                viewModel.addSongsToPlaylist(playlist, listOf(song.id))
                showPlaylistSelector = false
            }
        )
    }
}

@Composable
fun EditSongDialog(
    song: Song,
    onDismiss: () -> Unit,
    onUpdate: (String, String, Uri?) -> Unit
) {
    var title by remember { mutableStateOf(song.title) }
    var artist by remember { mutableStateOf(song.artist) }
    var pickedImageUri by remember { mutableStateOf<Uri?>(null) }

    val context = LocalContext.current
    var songArt by remember(song.id, pickedImageUri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(song.id, pickedImageUri) {
        songArt = try {
            val uri = pickedImageUri ?: return@LaunchedEffect
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            bitmap?.asImageBitmap()
        } catch (_: Exception) { null }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        pickedImageUri = uri
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Edit Song Info", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                // --- Album Art Picker ---
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val displayArt = songArt
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .clickable { imagePicker.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (displayArt != null) {
                            Image(
                                bitmap = displayArt,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                Icons.Rounded.MusicNote,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // Overlay edit icon
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Edit,
                                contentDescription = "Change album art",
                                modifier = Modifier.size(16.dp),
                                tint = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // --- Title field (like CreatePlaylist style) ---
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
                        Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    BasicTextField(
                        value = title,
                        onValueChange = { title = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            if (title.isEmpty()) Text("Song title...", color = Color.Gray)
                            innerTextField()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // --- Artist field (same style) ---
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
                        Icon(Icons.Rounded.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    BasicTextField(
                        value = artist,
                        onValueChange = { artist = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            if (artist.isEmpty()) Text("Artist name...", color = Color.Gray)
                            innerTextField()
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onUpdate(title, artist, pickedImageUri) },
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
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onSelect: (Playlist) -> Unit
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
