package com.ianocent.musicplayer.data

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File

class DownloadCompletionReceiver(
    private val downloadId: Long,
    private val song: Song,
    private val onComplete: () -> Unit
) : BroadcastReceiver() {
    
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        
        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (id != downloadId) return

        val ctx = context ?: return
        
        // Unregister immediately to avoid multiple triggers and leaks
        try {
            ctx.unregisterReceiver(this)
        } catch (e: Exception) {
            Log.w("DownloadCompletion", "Failed to unregister receiver: ${e.message}")
        }

        val pendingResult = goAsync()
        
        val downloadManager = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        
        try {
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = downloadManager.query(query)
            
            if (cursor?.moveToFirst() == true) {
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val status = cursor.getInt(statusIndex)
                
                var filePath: String? = null
                
                // Try multiple ways to get the file path
                val fileNameIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_FILENAME)
                if (fileNameIdx >= 0) {
                    filePath = cursor.getString(fileNameIdx)
                }
                
                if (filePath.isNullOrEmpty()) {
                    val uriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                    if (uriIdx >= 0) {
                        val uriStr = cursor.getString(uriIdx)
                        if (!uriStr.isNullOrEmpty()) {
                            filePath = uriToFilePath(ctx, Uri.parse(uriStr))
                        }
                    }
                }
                
                if (filePath.isNullOrEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val mediaUriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_MEDIAPROVIDER_URI)
                    if (mediaUriIdx >= 0) {
                        val uriStr = cursor.getString(mediaUriIdx)
                        if (!uriStr.isNullOrEmpty()) {
                            filePath = uriToFilePath(ctx, Uri.parse(uriStr))
                        }
                    }
                }
                
                if (filePath.isNullOrEmpty()) {
                    filePath = reconstructPath(ctx, cursor)
                }
                
                cursor.close()
                
                if (status == DownloadManager.STATUS_SUCCESSFUL && !filePath.isNullOrEmpty()) {
                    GlobalScope.launch(Dispatchers.IO) {
                        try {
                            // 1. Write ID3 tags directly to the file
                            MetadataWriter.writeMetadata(ctx, filePath!!, song)

                            // 2. Scan the file so MediaStore picks up the new tags
                            kotlinx.coroutines.suspendCancellableCoroutine<Unit> { cont ->
                                MediaScannerConnection.scanFile(
                                    ctx,
                                    arrayOf(filePath),
                                    arrayOf("audio/mpeg")
                                ) { _, _ ->
                                    if (cont.isActive) cont.resume(Unit) {}
                                }
                            }

                            // 3. Optional: Direct MediaStore update for immediate result
                            updateMediaStoreEntry(ctx, filePath!!, song)

                            onComplete()
                        } catch (e: Exception) {
                            Log.e("DownloadCompletion", "Error processing download: ${e.message}")
                            onComplete()
                        } finally {
                            pendingResult.finish()
                        }
                    }
                } else {
                    pendingResult.finish()
                    onComplete()
                }
            } else {
                cursor?.close()
                pendingResult.finish()
                onComplete()
            }
        } catch (e: Exception) {
            Log.e("DownloadCompletion", "Error in onReceive: ${e.message}")
            pendingResult.finish()
        }
    }

    private fun uriToFilePath(context: Context, uri: Uri): String? {
        return try {
            if (uri.scheme == "file") {
                uri.path
            } else {
                context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val displayNameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (displayNameIdx >= 0) {
                            val name = c.getString(displayNameIdx)
                            val musicDir = File(
                                android.os.Environment.getExternalStoragePublicDirectory(
                                    android.os.Environment.DIRECTORY_MUSIC
                                ), "IanPlayer"
                            )
                            if (!musicDir.exists()) musicDir.mkdirs()
                            File(musicDir, name).absolutePath
                        } else null
                    } else null
                }
            }
        } catch (e: Exception) {
            Log.w("DownloadCompletion", "uriToFilePath failed: ${e.message}")
            null
        }
    }

    private fun reconstructPath(context: Context, cursor: android.database.Cursor): String? {
        try {
            val titleIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
            val title = if (titleIdx >= 0) cursor.getString(titleIdx) else null
            if (!title.isNullOrEmpty()) {
                val musicDir = File(
                    android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_MUSIC
                    ), "IanPlayer"
                )
                val file = File(musicDir, "$title.mp3")
                if (file.exists()) return file.absolutePath
            }
            
            // Broad search in IanPlayer dir
            val musicDir = File(
                android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_MUSIC
                ), "IanPlayer"
            )
            if (musicDir.exists()) {
                val files = musicDir.listFiles { f -> f.extension.equals("mp3", ignoreCase = true) }
                if (files != null && files.isNotEmpty()) {
                    return files.maxByOrNull { it.lastModified() }?.absolutePath
                }
            }
        } catch (e: Exception) {
            Log.w("DownloadCompletion", "reconstructPath failed: ${e.message}")
        }
        return null
    }

    private fun updateMediaStoreEntry(context: Context, filePath: String, song: Song) {
        try {
            val file = File(filePath)
            if (!file.exists()) return
            
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.TITLE, song.title)
                put(MediaStore.Audio.Media.ARTIST, song.artist)
                put(MediaStore.Audio.Media.ALBUM, song.album)
                put(MediaStore.Audio.Media.IS_MUSIC, 1)
            }
            
            val selection = "${MediaStore.Audio.Media.DATA}=?"
            val updated = context.contentResolver.update(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                values,
                selection,
                arrayOf(filePath)
            )
            Log.d("DownloadCompletion", "MediaStore update: $updated rows affected")
        } catch (e: Exception) {
            Log.w("DownloadCompletion", "MediaStore entry update failed: ${e.message}")
        }
    }
}
