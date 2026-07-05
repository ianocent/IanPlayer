package com.ianocent.musicplayer.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri

object AlbumArtLoader {
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