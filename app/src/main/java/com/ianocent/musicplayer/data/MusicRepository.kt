package com.ianocent.musicplayer.data

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

class MusicRepository(private val context: Context) {

    fun getAllSongs(): List<Song> {
        val songs = mutableListOf<Song>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATA
        )
        val selection = buildString {
            append("${MediaStore.Audio.Media.IS_MUSIC} != 0")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                append(" AND ${MediaStore.Audio.Media.IS_TRASHED} = 0")
            }
        }

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                )
                songs.add(
                    Song(
                        id = id,
                        title = cursor.getString(titleCol) ?: "Unknown",
                        artist = cursor.getString(artistCol) ?: "Unknown Artist",
                        duration = cursor.getLong(durationCol),
                        uri = uri,
                        album = cursor.getString(albumCol) ?: "Unknown Album",
                        dateAdded = cursor.getLong(dateCol)
                    )
                )
            }
        }
        return songs
    }

    fun deleteSong(song: Song): Boolean {
        return try {
            // 1) Try quick IS_PENDING tombstone trick so MediaStore marks it gone
            val pendingUri = tryMarkPending(song)
            if (pendingUri != null) {
                // If pending marking succeeded and file is not yet visible, skip physical
            }

            // 2) Normal MediaStore delete
            val deletedRows = context.contentResolver.delete(song.uri, null, null)

            // 3) Physical file deletion regardless of MediaStore result
            deleteSongPhysical(song)

            deletedRows > 0
        } catch (e: RecoverableSecurityException) {
            // NOT handled here — let ViewModel emit the IntentSender
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            // Try physical delete as fallback even if MediaStore delete failed
            deleteSongPhysical(song)
            false
        }
    }

    private fun tryMarkPending(song: Song): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
                context.contentResolver.update(song.uri, values, null, null)
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    fun deleteSongPhysical(song: Song) {
        try {
            // Get real path from song URI
            val realPath = getRealPath(song.uri)
            if (realPath != null) {
                val file = File(realPath)
                if (file.exists()) {
                    val deleted = file.delete()
                    Timber.d("Physical file delete: $realPath → $deleted")
                    if (!deleted) {
                        // Fallback: truncate file to 0 bytes so it's effectively empty
                        try {
                            FileOutputStream(realPath).use { it.channel.truncate(0) }
                            Timber.d("Truncated file as fallback: $realPath")
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to truncate file: $realPath")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete physical file for song: ${song.title}")
        }
    }

    fun getRealPath(uri: Uri): String? {
        if (uri.scheme != "content") return uri.path
        val projection = arrayOf(MediaStore.Audio.Media.DATA)
        return context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA))
            } else null
        }
    }

    fun updateSongInfo(songId: Long, newTitle: String, newArtist: String) {
        try {
            val uri = ContentUris.withAppendedId(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                songId
            )
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.TITLE, newTitle)
                put(MediaStore.Audio.Media.ARTIST, newArtist)
            }
            context.contentResolver.update(uri, values, null, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
