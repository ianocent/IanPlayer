package com.ianocent.musicplayer.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ianocent.musicplayer.data.MonthlyRecap
import com.ianocent.musicplayer.data.Song
import kotlinx.coroutines.launch

@Composable
fun RecapCardContent(
    recap: MonthlyRecap,
    accentColor: Color
) {
    Box(
        modifier = Modifier
            .width(360.dp)
            .height(480.dp)
            .clip(RoundedCornerShape(24.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            accentColor.copy(alpha = 0.9f),
                            Color.Black.copy(alpha = 0.95f),
                            Color.Black
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
                    text = "YOUR",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White.copy(alpha = 0.5f),
                    letterSpacing = 2.sp
                )
                Text(
                    text = "MONTHLY RECAP",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White.copy(alpha = 0.5f),
                    letterSpacing = 2.sp
                )
            }

            Column {
                Text(
                    text = recap.monthLabel,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    RecapStatItem("Plays", "${recap.totalPlays}")
                    RecapStatItem("Minutes", "${recap.totalMinutes}")
                    RecapStatItem("Artists", "${recap.topArtists.size}")
                }

                Spacer(Modifier.height(16.dp))

                if (recap.topSongs.isNotEmpty()) {
                    Text(
                        text = "TOP SONGS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White.copy(alpha = 0.4f),
                        letterSpacing = 1.5.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    recap.topSongs.take(5).forEachIndexed { index, song ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.width(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = song.artist,
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.5f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (recap.topArtists.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        recap.topArtists.take(3).forEach { (artist, _) ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = artist,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.8f),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = recap.tasteComment,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.5f),
                    fontStyle = FontStyle.Italic,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = "Ian Player",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White.copy(alpha = 0.3f),
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
fun RecapStatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White
        )
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.5f),
            letterSpacing = 1.sp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecapCardSheet(
    recap: MonthlyRecap,
    accentColor: Color,
    onDismiss: () -> Unit
) {
    val graphicsLayer = rememberGraphicsLayer()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

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
                RecapCardContent(recap, accentColor)
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    coroutineScope.launch {
                        val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
                        saveRecapCardToGallery(context, bitmap)
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

private fun saveRecapCardToGallery(context: Context, bitmap: Bitmap) {
    val filename = "recap_card_${System.currentTimeMillis()}.png"
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
