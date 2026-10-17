package com.ianocent.musicplayer.data

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import timber.log.Timber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Fires when a song download finishes. The file landed in the app-owned
 * external files dir (see [SongDownloader]) so this receiver can embed tags +
 * album art — under scoped storage the app cannot write to public
 * DownloadProvider-owned files. It then publishes a tagged copy into the public
 * `Music/IanPlayer` collection via MediaStore (the app owns that row, so the
 * metadata columns stick too) and deletes the app-private temp copy.
 */
class DownloadCompletionReceiver(
    private val downloadId: Long,
    private val song: Song,
    private val fileName: String,
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
            Timber.w("Failed to unregister receiver: ${e.message}")
        }

        val pendingResult = goAsync()

        val downloadManager = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        try {
            val cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
            var status = -1
            if (cursor?.moveToFirst() == true) {
                status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
            }
            cursor?.close()

            val sourceFile = resolveSourceFile(ctx)
            if (status != DownloadManager.STATUS_SUCCESSFUL || sourceFile == null || !sourceFile.exists()) {
                Timber.w("Download $downloadId not usable (status=$status, file=$sourceFile)")
                pendingResult.finish()
                onComplete()
                return
            }

            // Scoped to this unit of work (bounded by pendingResult.finish() in
            // finally), not to the application lifetime.
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Let DownloadManager release its file lock.
                    delay(500)

                    // Catalog enrichment (real album/cover/genre/year/track no.)
                    // so tags + MediaStore match the actual release, not the
                    // provider's video row.
                    val meta = MusicMetadataService.enrich(song)

                    // 1. Embed tags + album art — the app owns this file, so the
                    //    write actually sticks.
                    val tagged = MetadataWriter.writeMetadata(ctx, sourceFile.absolutePath, song, meta = meta)
                    Timber.d("Tagging success=$tagged for ${sourceFile.absolutePath}")

                    // 2. Publish a tagged copy into public Music/IanPlayer.
                    publishToMediaStore(ctx, sourceFile, meta)

                    // 3. Remove the app-private temp copy.
                    runCatching { sourceFile.delete() }

                    onComplete()
                } catch (e: Exception) {
                    Timber.e("Error processing download: ${e.message}")
                    onComplete()
                } finally {
                    pendingResult.finish()
                }
            }
        } catch (e: Exception) {
            Timber.e("Error in onReceive: ${e.message}")
            pendingResult.finish()
        }
    }

    /** The download's app-owned file in external files dir, matched by name. */
    private fun resolveSourceFile(context: Context): File? {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.let { File(it, "IanPlayer") }
            ?: return null
        val direct = File(dir, fileName)
        if (direct.exists()) return direct
        return dir.listFiles { f -> f.isFile && f.extension.lowercase() in AUDIO_EXTENSIONS }
            ?.maxByOrNull { it.lastModified() }
    }

    /** Catalog album wins; falls back to the provider's value, then a placeholder. */
    private fun resolveAlbum(song: Song, meta: TrackMetadata?): String =
        meta?.album?.takeIf { it.isNotBlank() } ?: song.album.ifBlank { "Unknown Album" }

    /**
     * Copies the tagged [source] into the public audio collection and writes a
     * MediaStore row carrying the same metadata. Q+ routes through RELATIVE_PATH
     * + IS_PENDING (a byte copy the app owns); pre-Q copies to the public dir
     * directly and lets the scanner index it.
     */
    private fun publishToMediaStore(context: Context, source: File, meta: TrackMetadata?): Uri? {
        val (title, artist) = SongTags.resolve(song.title, song.artist)
        val album = resolveAlbum(song, meta)
        val mime = SongTags.mimeForExtension(source.extension)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            publishQ(context, source, mime, title, artist, album, meta)
        } else {
            publishLegacy(context, source)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun publishQ(
        context: Context,
        source: File,
        mime: String,
        title: String,
        artist: String,
        album: String,
        meta: TrackMetadata?
    ): Uri? {
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, source.name)
            put(MediaStore.Audio.Media.MIME_TYPE, mime)
            put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/IanPlayer")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
            put(MediaStore.Audio.Media.TITLE, title)
            put(MediaStore.Audio.Media.ARTIST, artist)
            put(MediaStore.Audio.Media.ALBUM, album)
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
            if (song.duration > 0L) put(MediaStore.Audio.Media.DURATION, song.duration)
            meta?.year?.toIntOrNull()?.let { put(MediaStore.Audio.Media.YEAR, it) }
        }

        val destUri = context.contentResolver.insert(collection, values)
        if (destUri == null) {
            Timber.w("MediaStore insert returned null for ${source.name}")
            return null
        }

        try {
            context.contentResolver.openOutputStream(destUri)?.use { out ->
                source.inputStream().use { it.copyTo(out) }
            } ?: run {
                Timber.w("MediaStore outputStream null for $destUri")
                context.contentResolver.delete(destUri, null, null)
                return null
            }
        } catch (e: Exception) {
            Timber.e("Failed copying to MediaStore: ${e.message}")
            runCatching { context.contentResolver.delete(destUri, null, null) }
            return null
        }

        // Clear pending + re-affirm metadata so the row reflects what we embedded.
        val done = ContentValues().apply {
            put(MediaStore.Audio.Media.IS_PENDING, 0)
            put(MediaStore.Audio.Media.TITLE, title)
            put(MediaStore.Audio.Media.ARTIST, artist)
            put(MediaStore.Audio.Media.ALBUM, album)
        }
        context.contentResolver.update(destUri, done, null, null)
        Timber.d("Published to MediaStore: $destUri")
        return destUri
    }

    private fun publishLegacy(context: Context, source: File): Uri? {
        return try {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "IanPlayer"
            )
            if (!dir.exists()) dir.mkdirs()
            val dest = File(dir, source.name)
            source.copyTo(dest, overwrite = true)
            MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), null, null)
            Timber.d("Published (legacy) to ${dest.absolutePath}")
            null
        } catch (e: Exception) {
            Timber.e("Legacy publish failed: ${e.message}")
            null
        }
    }

    private companion object {
        val AUDIO_EXTENSIONS = setOf("m4a", "webm", "opus", "flac", "mp3", "mp4")
    }
}
