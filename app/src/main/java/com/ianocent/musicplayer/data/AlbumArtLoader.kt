package com.ianocent.musicplayer.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.palette.graphics.Palette
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.toArgb
import android.os.Build
import android.util.Size

object AlbumArtLoader {
    fun extractDominantColor(bitmap: Bitmap): androidx.compose.ui.graphics.Color {
        val palette = Palette.from(bitmap).generate()
        val dominant = palette.getDominantColor(0xFF333333.toInt())
        return androidx.compose.ui.graphics.Color(dominant)
    }

    fun extractPaletteColors(bitmap: Bitmap): List<ComposeColor> {
        val palette = Palette.from(bitmap).generate()
        val colors = mutableListOf<ComposeColor>()
        palette.vibrantSwatch?.rgb?.let { colors.add(ComposeColor(it)) }
        palette.darkVibrantSwatch?.rgb?.let { colors.add(ComposeColor(it)) }
        palette.lightVibrantSwatch?.rgb?.let { colors.add(ComposeColor(it)) }
        palette.mutedSwatch?.rgb?.let { colors.add(ComposeColor(it)) }
        palette.darkMutedSwatch?.rgb?.let { colors.add(ComposeColor(it)) }
        palette.lightMutedSwatch?.rgb?.let { colors.add(ComposeColor(it)) }
        palette.dominantSwatch?.rgb?.let { colors.add(ComposeColor(it)) }
        return colors.distinct()
    }
    fun getEmbeddedArt(context: Context, uri: Uri, targetSize: Int = 150): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(uri, Size(targetSize, targetSize), null)
            } else {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                val art = retriever.embeddedPicture
                retriever.release()

                art?.let { bytes ->
                    val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
fun getAdaptiveControlColor(baseColor: ComposeColor, isDarkMode: Boolean): ComposeColor {
    val hsl = FloatArray(3)
    android.graphics.Color.colorToHSV(baseColor.toArgb(), hsl)
    return if (isDarkMode) {
        ComposeColor.hsl(hsl[0], (hsl[1] * 0.5f).coerceIn(0f, 1f), 0.75f)
    } else {
        ComposeColor.hsl(hsl[0], hsl[1], 0.25f)
    }
}

