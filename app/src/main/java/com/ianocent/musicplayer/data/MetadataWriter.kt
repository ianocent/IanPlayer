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

            if (mp3file.hasId3v1Tag()) {
                mp3file.removeId3v1Tag()
            }
            if (mp3file.hasId3v2Tag()) {
                mp3file.removeId3v2Tag()
            }

            val id3v2tag = ID3v24Tag().apply {
                artist = song.artist
                title = song.title
                album = song.album
            }

            if (!song.remoteArtUrl.isNullOrEmpty()) {
                try {
                    Log.d("MetadataWriter", "Downloading album art from: ${song.remoteArtUrl}")
                    val bitmap = downloadBitmap(song.remoteArtUrl)
                    if (bitmap != null) {
                        Log.d("MetadataWriter", "Album art downloaded successfully, size: ${bitmap.width}x${bitmap.height}")
                        embedAlbumArt(id3v2tag, bitmap)
                    } else {
                        Log.w("MetadataWriter", "Failed to download album art bitmap")
                    }
                } catch (e: Exception) {
                    Log.e("MetadataWriter", "Failed to download/embed album art: ${e.message}", e)
                }
            } else {
                Log.d("MetadataWriter", "No remote art URL available for: ${song.title}")
            }

            mp3file.id3v2Tag = id3v2tag

            // Save ke temp file dulu (mp3agic gak boleh overwrite file yang lagi dibaca)
            val tempFile = File(file.parentFile, "${file.nameWithoutExtension}_temp_${System.currentTimeMillis()}.mp3")
            mp3file.save(tempFile.absolutePath)

            if (!tempFile.exists() || tempFile.length() == 0L) {
                Log.e("MetadataWriter", "Temp file invalid after save, aborting")
                tempFile.delete()
                return@withContext false
            }

            if (file.exists()) file.delete()
            val renamed = tempFile.renameTo(file)

            if (!renamed) {
                Log.e("MetadataWriter", "Failed to rename temp file to original path")
                return@withContext false
            }

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
            var artUrl = url
            
            // Try high quality first for YouTube thumbnails
            if (artUrl.contains("ytimg.com") || artUrl.contains("googleusercontent.com")) {
                artUrl = artUrl.replace("default.jpg", "maxresdefault.jpg")
                    .replace("mqdefault.jpg", "maxresdefault.jpg")
                    .replace("hqdefault.jpg", "maxresdefault.jpg")
                    .replace("sddefault.jpg", "maxresdefault.jpg")
                
                // Try maxresdefault first
                var bitmap = tryDownloadBitmap(artUrl)
                if (bitmap != null) return@withContext bitmap
                
                // Fallback to hqdefault if maxres doesn't exist
                artUrl = artUrl.replace("maxresdefault.jpg", "hqdefault.jpg")
                bitmap = tryDownloadBitmap(artUrl)
                if (bitmap != null) return@withContext bitmap
                
                // Last fallback to original URL
                tryDownloadBitmap(url)
            } else {
                tryDownloadBitmap(artUrl)
            }
        } catch (e: Exception) {
            Log.e("MetadataWriter", "Failed to download bitmap: ${e.message}", e)
            null
        }
    }
    
    private fun tryDownloadBitmap(url: String): Bitmap? {
        return try {
            val urlObj = URL(url)
            val conn = urlObj.openConnection() as HttpURLConnection
            conn.doInput = true
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.connect()
            if (conn.responseCode != 200) {
                Log.d("MetadataWriter", "HTTP ${conn.responseCode} for URL: $url")
                return null
            }
            BitmapFactory.decodeStream(conn.inputStream)
        } catch (e: Exception) {
            Log.d("MetadataWriter", "Failed to download from $url: ${e.message}")
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

