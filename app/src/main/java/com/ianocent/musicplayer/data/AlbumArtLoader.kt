package com.ianocent.musicplayer.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.palette.graphics.Palette
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.toArgb

object AlbumArtLoader {
    fun extractDominantColor(bitmap: Bitmap): androidx.compose.ui.graphics.Color {
        val palette = Palette.from(bitmap).generate()
        val dominant = palette.getDominantColor(0xFF333333.toInt())
        return androidx.compose.ui.graphics.Color(dominant)
    }
    fun getEmbeddedArt(context: Context, uri: Uri, targetSize: Int = 150): Bitmap? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val art = retriever.embeddedPicture
            retriever.release()

            art?.let { bytes ->
                // Decode cuma bounds dulu buat tau ukuran asli
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

                // Hitung sample size biar di-downscale pas decode (hemat memory)
                var sampleSize = 1
                while (options.outWidth / sampleSize > targetSize * 2) {
                    sampleSize *= 2
                }

                val finalOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, finalOptions)
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

