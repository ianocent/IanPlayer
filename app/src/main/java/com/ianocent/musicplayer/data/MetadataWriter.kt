package com.ianocent.musicplayer.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.mpatric.mp3agic.Mp3File
import com.mpatric.mp3agic.ID3v2
import com.mpatric.mp3agic.ID3v24Tag
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object MetadataWriter {
    suspend fun writeMetadata(
        context: Context,
        filePath: String,
        song: Song
    ) = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                Log.e("MetadataWriter", "File does not exist: $filePath")
                return@withContext false
            }

            val mp3file = Mp3File(filePath)
            
            // Remove existing ID3 tags
            if (mp3file.hasId3v1Tag()) {
                mp3file.removeId3v1Tag()
            }
            if (mp3file.hasId3v2Tag()) {
                mp3file.removeId3v2Tag()
            }
            
            // Create ID3v2.4 tag
            val id3v2tag = ID3v24Tag().apply {
                artist = song.artist
                title = song.title
                album = song.album
            }
            
            // Download and embed album art if available
            if (!song.remoteArtUrl.isNullOrEmpty()) {
                try {
                    val bitmap = downloadBitmap(song.remoteArtUrl)
                    if (bitmap != null) {
                        embedAlbumArt(id3v2tag, bitmap)
                    }
                } catch (e: Exception) {
                    Log.e("MetadataWriter", "Failed to download album art: ${e.message}")
                }
            }
            
            mp3file.id3v2Tag = id3v2tag
            mp3file.save(filePath)
            
            Log.d("MetadataWriter", "Metadata written successfully for: ${song.title}")
            true
        } catch (e: Exception) {
            Log.e("MetadataWriter", "Failed to write metadata: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    private suspend fun downloadBitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
        return@withContext try {
            val urlObj = URL(url)
            val conn = urlObj.openConnection() as HttpURLConnection
            conn.doInput = true
            conn.connect()
            BitmapFactory.decodeStream(conn.inputStream)
        } catch (e: Exception) {
            Log.e("MetadataWriter", "Failed to download bitmap: ${e.message}")
            null
        }
    }

    private fun embedAlbumArt(tag: ID3v2, bitmap: Bitmap) {
        try {
            // Convert bitmap to byte array (JPEG format)
            val byteArray = ByteArrayOutputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                stream.toByteArray()
            }

            // Set the album art as APIC frame (attached picture)
            tag.setAlbumImage(byteArray, "image/jpeg")
            
            Log.d("MetadataWriter", "Album art embedded successfully")
        } catch (e: Exception) {
            Log.e("MetadataWriter", "Failed to embed album art: ${e.message}")
        }
    }
}

