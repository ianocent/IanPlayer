package com.ianocent.musicplayer.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.ianocent.musicplayer.data.Playlist
import com.ianocent.musicplayer.data.Song
import com.ianocent.musicplayer.viewmodel.MusicViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import androidx.compose.foundation.clickable

@Composable
fun PlaylistCardContent(
    playlist: Playlist,
    songs: List<Song>,
    albumArts: List<Bitmap?>,
    accentColor: Color
) {
    Box(
        modifier = Modifier
            .width(360.dp)
            .height(480.dp)
            .clip(RoundedCornerShape(24.dp))
    ) {
        // Background: gambar custom playlist kalau ada, fallback ke collage album art
        Box(modifier = Modifier.fillMaxSize()) {
            if (!playlist.imageUri.isNullOrBlank()) {
                coil.compose.AsyncImage(
                    model = playlist.imageUri,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(8.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                val validArts = albumArts.filterNotNull().take(4)
                if (validArts.isNotEmpty()) {
                    validArts.forEachIndexed { index, art ->
                        Image(
                            bitmap = art.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .offset(x = (index * 8).dp, y = (index * 8).dp)
                                .blur(8.dp)
                                .alpha(0.5f),
                            contentScale = ContentScale.Crop
                        )
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(accentColor))
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            accentColor.copy(alpha = 0.45f),
                            Color.Black.copy(alpha = 0.85f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "PLAYLIST",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White.copy(alpha = 0.5f),
                    letterSpacing = 2.sp
                )
                Text(
                    text = playlist.name,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 36.sp
                )
                Text(
                    text = "${songs.size} songs",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                songs.take(5).forEachIndexed { index, song ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${index + 1}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.width(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = song.artist,
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (songs.size > 5) {
                    Text(
                        text = "+${songs.size - 5} more",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.padding(start = 24.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "IanPlayer",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistCardSheet(
    playlist: Playlist,
    viewModel: MusicViewModel,
    accentColor: Color,
    onDismiss: () -> Unit
) {
    val graphicsLayer = rememberGraphicsLayer()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val songs = viewModel.getSongsInPlaylist(playlist)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.drawWithContent {
                    graphicsLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(graphicsLayer)
                }
            ) {
                PlaylistCardContent(
                    playlist = playlist,
                    songs = songs,
                    albumArts = songs.map { viewModel.albumArt.value },
                    accentColor = accentColor
                )
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    coroutineScope.launch {
                        val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
                        // 1. Simpan ke Galeri permanen (Pakai nama fungsi yang udah di-rename)
                        savePlaylistBitmapToGallery(context, bitmap)
                        // 2. Simpan ke Cache untuk di-share
                        val uri = savePlaylistBitmapToCache(context, bitmap)
                        uri?.let { sharePlaylistImage(context, it, "Check out my playlist: ${playlist.name}") }
                        onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save to Gallery")
            }
        }
    }
}

// Tambahin "private" biar fungsinya cuma hidup dan bisa dipanggil di dalem file ini aja
private fun savePlaylistBitmapToCache(context: Context, bitmap: Bitmap): android.net.Uri? {
    return try {
        val file = File(context.cacheDir, "playlist_card_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    } catch (e: Exception) {
        null
    }
}

// Ganti nama fungsi & tambahin "private"
private fun savePlaylistBitmapToGallery(context: Context, bitmap: Bitmap) {
    val filename = "playlist_card_${System.currentTimeMillis()}.png"
    val resolver = context.contentResolver
    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/IanPlayer")
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    uri?.let {
        resolver.openOutputStream(it)?.use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
    }
}

// Tambahin "private" & rename
private fun sharePlaylistImage(context: Context, uri: android.net.Uri, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share Playlist Card"))
}