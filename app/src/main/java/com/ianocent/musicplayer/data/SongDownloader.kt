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
        val fileName = "$cleanArtist - $cleanTitle.mp3"

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(song.title)
            .setDescription("$cleanArtist - IanPlayer")
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_MUSIC, "IanPlayer/$fileName")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadId = downloadManager.enqueue(request)

        // Receiver applies ID3 metadata, scans the file into MediaStore, then
        // unregisters itself.
        val receiver = DownloadCompletionReceiver(downloadId, song, onComplete)
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
    }
}
