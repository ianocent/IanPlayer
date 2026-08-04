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

    // MediaStore can duplicate rows for one physical file after metadata rewrites
    // (OEM MediaProvider inode swap + scanner race). Delete extras, keep original id.
    fun dedupeMediaRows(songId: Long, filePath: String?) {
        if (filePath == null) return
        try {
            val ids = mutableListOf<Long>()
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Media._ID),
                "${MediaStore.Audio.Media.DATA} = ?",
                arrayOf(filePath),
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) ids.add(cursor.getLong(0))
            }
            ids.filter { it != songId }.forEach { extraId ->
                try {
                    context.contentResolver.delete(
                        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, extraId),
                        null, null
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Dedupe delete failed for row $extraId")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Dedupe failed for $filePath")
        }
    }

    // ==========================================
    // M3U PLAYLIST IMPORT/EXPORT
    // ==========================================
    data class M3uEntry(val path: String?, val title: String?, val artist: String?)

    fun buildM3u(playlistName: String, songs: List<Song>): String {
        val sb = StringBuilder()
        sb.append("#EXTM3U\n")
        sb.append("#PLAYLIST:$playlistName\n")
        songs.forEach { song ->
            sb.append("#EXTINF:${song.duration / 1000},${song.artist} - ${song.title}\n")
            if (song.isStream) {
                // Stream songs can't be resolved on another device; keep reference comment
                sb.append("#IanStream:").append(song.remoteId ?: song.title).append('\n')
            } else {
                sb.append(getRealPath(song.uri) ?: song.uri.toString()).append('\n')
            }
        }
        return sb.toString()
    }

    fun parseM3u(text: String): Pair<String?, List<M3uEntry>> {
        val entries = mutableListOf<M3uEntry>()
        var playlistName: String? = null
        var pendingInfo: String? = null
        
        fun parseInfo(info: String?): Pair<String?, String?> {
            if (info == null) return null to null
            val commaIdx = info.indexOf(',')
            if (commaIdx < 0) return null to null
            val data = info.substring(commaIdx + 1).trim()
            val dashIdx = data.indexOf(" - ")
            return if (dashIdx >= 0) {
                data.substring(0, dashIdx).trim() to data.substring(dashIdx + 3).trim()
            } else {
                null to data
            }
        }

        text.lines().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#EXTM3U")) return@forEach
            
            if (line.startsWith("#PLAYLIST:")) {
                playlistName = line.removePrefix("#PLAYLIST:").trim().takeIf { it.isNotEmpty() }
                return@forEach
            }

            when {
                line.startsWith("#EXTINF:") -> pendingInfo = line.removePrefix("#EXTINF:")
                line.startsWith("#IanStream:") -> {
                    val remoteId = line.removePrefix("#IanStream:").trim()
                    val (artist, title) = parseInfo(pendingInfo)
                    entries.add(M3uEntry("ianstream://$remoteId", title, artist))
                    pendingInfo = null
                }
                line.startsWith("#") -> { /* other comments ignored */ }
                else -> {
                    val (artist, title) = parseInfo(pendingInfo)
                    entries.add(M3uEntry(line, title, artist))
                    pendingInfo = null
                }
            }
        }
        return playlistName to entries
    }

    fun matchM3uEntries(entries: List<M3uEntry>, library: List<Song>): List<Song> {
        val matched = mutableListOf<Song>()
        val usedIds = mutableSetOf<Long>()
        
        // Cache library mappings for faster lookup
        val libraryByMeta = library.groupBy { "${it.artist.trim().lowercase()} - ${it.title.trim().lowercase()}" }
        val libraryByFilename = library.filter { !it.isStream }.groupBy { 
            (getRealPath(it.uri) ?: it.uri.toString()).substringAfterLast('/').lowercase() 
        }
        val libraryByRemoteId = library.filter { it.isStream && it.remoteId != null }.associateBy { it.remoteId }

        entries.forEach { entry ->
            // 1. Try match by Remote ID (for stream songs)
            if (entry.path?.startsWith("ianstream://") == true) {
                val rid = entry.path.removePrefix("ianstream://")
                val song = libraryByRemoteId[rid]
                if (song != null && song.id !in usedIds) {
                    matched.add(song)
                    usedIds.add(song.id)
                    return@forEach
                }
            }

            // 2. Try match by Artist + Title (most reliable across devices)
            if (entry.artist != null && entry.title != null) {
                val key = "${entry.artist.trim().lowercase()} - ${entry.title.trim().lowercase()}"
                val song = libraryByMeta[key]?.firstOrNull { it.id !in usedIds }
                if (song != null) {
                    matched.add(song)
                    usedIds.add(song.id)
                    return@forEach
                }
            }
            
            // 3. Try match by Filename
            val entryFileName = entry.path?.substringAfterLast('/')?.lowercase()
            if (entryFileName != null) {
                val song = libraryByFilename[entryFileName]?.firstOrNull { it.id !in usedIds }
                if (song != null) {
                    matched.add(song)
                    usedIds.add(song.id)
                    return@forEach
                }
            }
        }
        return matched
    }
}
