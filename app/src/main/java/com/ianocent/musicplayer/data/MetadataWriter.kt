package com.ianocent.musicplayer.data

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import timber.log.Timber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.mpatric.mp3agic.Mp3File
import com.mpatric.mp3agic.ID3v2
import com.mpatric.mp3agic.ID3v24Tag
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.AndroidArtwork
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Single entry point for embedding ID3-style metadata into downloaded audio
 * files. The container is sniffed from magic bytes (download filenames can
 * lie), then dispatched to the matching tag writer: mp3agic for MP3,
 * jaudiotagger for AAC-in-MP4 (.m4a). WebM/Opus has no writable standard tag,
 * so those files rely on the caller updating MediaStore columns instead.
 */
object MetadataWriter {
    suspend fun writeMetadata(
        context: Context,
        filePath: String,
        song: Song,
        newArt: Bitmap? = null
    ) = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                Timber.e("File does not exist: $filePath")
                return@withContext false
            }

            // Provider titles/channels are messy ("Ed Sheeran - Topic",
            // "(Official Music Video)") — normalise before they land in tags.
            val (title, artist) = SongTags.resolve(song.title, song.artist)

            val header = ByteArray(16).let { buf ->
                file.inputStream().use { input ->
                    var off = 0
                    while (off < buf.size) {
                        val n = input.read(buf, off, buf.size - off)
                        if (n < 0) break
                        off += n
                    }
                }
                buf
            }
            val container = SongTags.detectContainer(header)
            Timber.d("Tagging $container file: title=\"$title\" artist=\"$artist\" album=\"${song.album}\"")

            val artBytes = resolveAlbumArtBytes(song, newArt)

            val success = when (container) {
                SongTags.Container.MP3 -> writeMp3Tags(file, title, artist, song.album, artBytes)
                SongTags.Container.MP4 -> writeMp4Tags(file, title, artist, song.album, artBytes)
                SongTags.Container.WEBM, SongTags.Container.UNKNOWN -> {
                    Timber.w("No embeddable tag standard for $container; relying on MediaStore columns")
                    true
                }
            }
            Timber.d("Metadata writing success: $success for $filePath")
            success
        } catch (e: Exception) {
            Timber.e(e, "Failed to write metadata: ${e.message}")
            false
        }
    }

    /** Returns JPEG bytes for the artwork to embed, downloading remote art if needed. */
    private suspend fun resolveAlbumArtBytes(song: Song, newArt: Bitmap?): ByteArray? {
        newArt?.let { return bitmapToJpeg(it) }
        val url = song.remoteArtUrl?.takeIf { it.isNotBlank() } ?: return null
        return try {
            Timber.d("Downloading album art from: $url")
            downloadBitmap(url)?.let { bitmap ->
                Timber.d("Album art downloaded successfully, size: ${bitmap.width}x${bitmap.height}")
                bitmapToJpeg(bitmap)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to download album art: ${e.message}")
            null
        }
    }

    private fun bitmapToJpeg(bitmap: Bitmap): ByteArray =
        ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            stream.toByteArray()
        }

    /** MP3 tagging via mp3agic: rewrite through a temp file (mp3agic cannot overwrite its input). */
    private fun writeMp3Tags(
        file: File,
        title: String,
        artist: String,
        album: String,
        artBytes: ByteArray?
    ): Boolean {
        return try {
            val mp3file = Mp3File(file)
            val id3v2tag: ID3v2 = if (mp3file.hasId3v2Tag()) mp3file.id3v2Tag else ID3v24Tag()

            id3v2tag.artist = artist
            id3v2tag.title = title
            id3v2tag.album = album

            if (artBytes != null) {
                id3v2tag.setAlbumImage(artBytes, "image/jpeg")
                Timber.d("Album art embedded into MP3")
            }

            mp3file.id3v2Tag = id3v2tag

            val tempFile = File(file.parentFile, "${file.nameWithoutExtension}_temp_${System.currentTimeMillis()}.mp3")
            mp3file.save(tempFile.absolutePath)

            if (!tempFile.exists() || tempFile.length() == 0L) {
                Timber.e("Temp file invalid after save, aborting")
                tempFile.delete()
                return false
            }

            if (file.exists()) file.delete()
            val renamed = tempFile.renameTo(file)
            if (!renamed) {
                Timber.e("Failed to rename temp file to original path")
                return false
            }
            true
        } catch (e: Exception) {
            Timber.e(e, "MP3 tagging failed: ${e.message}")
            false
        }
    }

    /** MP4/M4A tagging via jaudiotagger: commits in place. */
    private fun writeMp4Tags(
        file: File,
        title: String,
        artist: String,
        album: String,
        artBytes: ByteArray?
    ): Boolean {
        return try {
            val audioFile = AudioFileIO.read(file)
            val tag = audioFile.tagOrCreateDefault

            tag.setField(FieldKey.TITLE, title)
            tag.setField(FieldKey.ARTIST, artist)
            if (album.isNotBlank()) tag.setField(FieldKey.ALBUM, album)

            if (artBytes != null) {
                try {
                    tag.deleteArtworkField()
                } catch (_: Exception) {
                }
                val artwork = AndroidArtwork()
                artwork.binaryData = artBytes
                artwork.mimeType = "image/jpeg"
                tag.setField(artwork)
                Timber.d("Album art embedded into M4A")
            }

            audioFile.commit()
            true
        } catch (e: Exception) {
            Timber.e(e, "M4A tagging failed: ${e.message}")
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
            Timber.e(e, "Failed to download bitmap: ${e.message}")
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
                Timber.d("HTTP ${conn.responseCode} for URL: $url")
                return null
            }
            BitmapFactory.decodeStream(conn.inputStream)
        } catch (e: Exception) {
            Timber.d("Failed to download from $url: ${e.message}")
            null
        }
    }

    suspend fun writeMetadataFromFile(
        context: Context,
        songId: Long,
        newTitle: String,
        newArtist: String,
        newImageUri: Uri? = null
    ) = withContext(Dispatchers.IO) {
        try {
            val uri = ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId
            )

            // Get real file path
            val dataCol = MediaStore.Audio.Media.DATA
            val projection = arrayOf(dataCol)
            val filePath = context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(cursor.getColumnIndexOrThrow(dataCol)) else null
            }

            if (filePath == null) {
                Timber.e("Cannot get file path for URI: $uri")
                return@withContext false
            }

            val file = File(filePath)
            if (!file.exists()) {
                Timber.e("File does not exist: $filePath")
                return@withContext false
            }

            // Detect container from magic bytes
            val header = ByteArray(16).let { buf ->
                file.inputStream().use { input ->
                    var off = 0
                    while (off < buf.size) {
                        val n = input.read(buf, off, buf.size - off)
                        if (n < 0) break
                        off += n
                    }
                }
                buf
            }
            val container = SongTags.detectContainer(header)
            Timber.d("writeMetadataFromFile: container=$container for $filePath")

            val artBytes = if (newImageUri != null) {
                try {
                    val inputStream = context.contentResolver.openInputStream(newImageUri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    bitmap?.let { bitmapToJpeg(it) }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to decode album art from URI")
                    null
                }
            } else null

            val success = when (container) {
                SongTags.Container.MP3 -> writeMp3Tags(file, newTitle, newArtist, "", artBytes)
                SongTags.Container.MP4 -> writeMp4Tags(file, newTitle, newArtist, "", artBytes)
                SongTags.Container.WEBM, SongTags.Container.UNKNOWN -> {
                    // WebM/Opus has no standard writable tag; rely on MediaStore columns only
                    Timber.w("No embeddable tag for $container, updating MediaStore columns only")
                    true
                }
            }

            if (!success) {
                Timber.e("Tag write failed for $filePath")
                return@withContext false
            }

            // Also update MediaStore metadata
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.TITLE, newTitle)
                put(MediaStore.Audio.Media.ARTIST, newArtist)
            }
            context.contentResolver.update(uri, values, null, null)

            // Trigger scan for broad compatibility
            @Suppress("DEPRECATION")
            context.sendBroadcast(android.content.Intent(android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, android.net.Uri.fromFile(file)))

            Timber.d("Metadata written successfully for song $songId ($container)")
            true
        } catch (e: RecoverableSecurityException) {
            Timber.w(e, "RecoverableSecurityException for song $songId — rethrowing for SAF")
            throw e
        } catch (e: java.io.FileNotFoundException) {
            Timber.e(e, "File not found for song $songId — may need SAF permission")
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to write metadata from file: ${e.message}")
            false
        }
    }
}
