package com.ianocent.musicplayer.data

import android.app.DownloadManager
import android.content.Context
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment

/**
 * Owns the song-download flow's framework plumbing: DownloadManager request,
 * completion receiver registration, and filename sanitising. Callers supply a
 * resolved URL and get notified when the file is processed.
 */
class SongDownloader(private val context: Context) {

    fun download(song: Song, url: String, onComplete: () -> Unit) {
        val cleanTitle = song.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val cleanArtist = song.artist.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        // Stream providers serve AAC-in-MP4 or WebM/Opus, not MP3 — name the
        // file after the real container (from the mime= query param) so the
        // scanner and MetadataWriter agree on what the bytes are.
        val extension = SongTags.extensionForUrl(url)
        val fileName = "$cleanArtist - $cleanTitle.$extension"

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        // Land in the app-owned external files dir first: the app owns this file,
        // so it can embed tags + album art (public DownloadProvider files are
        // read-only to us under scoped storage). The receiver then publishes a
        // copy into public Music/IanPlayer via MediaStore.
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(song.title)
            .setDescription("$cleanArtist - IanPlayer")
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_MUSIC, "IanPlayer/$fileName")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadId = downloadManager.enqueue(request)

        // Receiver applies tags + art, publishes into MediaStore, then
        // unregisters itself.
        val receiver = DownloadCompletionReceiver(downloadId, song, fileName, onComplete)
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
    }
}
