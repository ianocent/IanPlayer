package com.ianocent.musicplayer.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.ianocent.musicplayer.data.Song
import com.ianocent.musicplayer.player.PlaybackService

class PlayerManager(private val context: Context) {
    // Sekarang pakai Player bawaan interface Media3, bukan ExoPlayer langsung
    var player: Player? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    // Fungsi buat konek UI ke PlaybackService
    fun initialize(onReady: () -> Unit) {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener(
            {
                player = controllerFuture?.get()
                onReady() // Panggil listener pas player udah siap
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    fun moveQueuedItem(from: Int, to: Int) {
        try {
            val p = player ?: return
            if (from !in 0 until p.mediaItemCount || to !in 0 until p.mediaItemCount) return
            p.moveMediaItem(from, to)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun replaceQueuedItem(index: Int, song: Song) {
        try {
            val p = player ?: return
            if (index < 0 || index >= p.mediaItemCount) return
            // Cuma swap URI item di index tsb, TANPA prepare()/play() — item ini belum
            // diplay, cuma lagi di-pre-resolve. Tujuannya: pas ExoPlayer auto-advance
            // ke item ini sendiri (atau user pencet skip-next di notifikasi, yang manggil
            // player.seekToNext() LANGSUNG ke ExoPlayer, gak lewat playNext() kita),
            // URI-nya udah real, bukan ytmusic://placeholder/ lagi.
            val current = p.getMediaItemAt(index)
            val newItem = current.buildUpon()
                .setUri(song.uri)
                .build()
            p.replaceMediaItem(index, newItem)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playSong(song: Song, queueSongs: List<Song> = emptyList(), startIndex: Int = 0, startPositionMs: Long = 0) {
        try {
            val p = player ?: return

            if (p.currentMediaItem?.mediaId == song.id.toString() && 
                p.currentMediaItem?.localConfiguration?.uri?.toString()?.startsWith("ytmusic://placeholder/") == true &&
                !song.uri.toString().startsWith("ytmusic://placeholder/")) {
                
                val meta = MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .apply {
                        if (!song.remoteArtUrl.isNullOrEmpty()) {
                            setArtworkUri(android.net.Uri.parse(song.remoteArtUrl))
                        }
                    }
                    .build()
                val newItem = MediaItem.Builder()
                    .setUri(song.uri)
                    .setMediaId(song.id.toString())
                    .setMediaMetadata(meta)
                    .build()
                
                p.replaceMediaItem(p.currentMediaItemIndex, newItem)
                p.prepare()
                p.play()
                return
            }

            val mediaItems = buildMediaItems(song, queueSongs)

            p.setMediaItems(mediaItems, if (queueSongs.isNotEmpty()) startIndex else 0, startPositionMs)
            p.prepare()
            p.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setPlaylist(song: Song, queueSongs: List<Song>, startIndex: Int) {
        try {
            val p = player ?: return
            val mediaItems = buildMediaItems(song, queueSongs)
            p.setMediaItems(mediaItems, startIndex, 0)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun buildMediaItems(song: Song, queueSongs: List<Song>): List<MediaItem> {
        return if (queueSongs.isNotEmpty()) {
            queueSongs.map { s -> buildMediaItem(s) }
        } else {
            listOf(buildMediaItem(song))
        }
    }

    private fun buildMediaItem(s: Song): MediaItem {
        val meta = MediaMetadata.Builder()
            .setTitle(s.title)
            .setArtist(s.artist)
            .setAlbumTitle(s.album)
            .apply {
                if (!s.remoteArtUrl.isNullOrEmpty()) {
                    setArtworkUri(android.net.Uri.parse(s.remoteArtUrl))
                }
            }
            .build()
        return MediaItem.Builder()
            .setUri(s.uri)
            .setMediaId(s.id.toString())
            .setMediaMetadata(meta)
            .build()
    }

    fun togglePlayPause() {
        try {
            if (player?.isPlaying == true) {
                player?.pause()
            } else {
                player?.play()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun seekTo(positionMs: Long) {
        try {
            player?.seekTo(positionMs)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getCurrentPosition(): Long = player?.currentPosition ?: 0L

    fun getDuration(): Long = player?.duration ?: 0L

    private var currentRepeatMode = Player.REPEAT_MODE_OFF

    fun toggleRepeat(): Int {
        currentRepeatMode = when (currentRepeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        player?.repeatMode = currentRepeatMode
        return currentRepeatMode
    }

    fun release() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        player = null
    }
}