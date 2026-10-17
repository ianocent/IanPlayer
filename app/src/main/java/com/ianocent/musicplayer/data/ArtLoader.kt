package com.ianocent.musicplayer.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import coil.ImageLoader
import coil.request.ImageRequest
import coil.disk.DiskCache
import coil.memory.MemoryCache
import java.io.File

/**
 * Album-art loading module. Owns memory caching, disk caching, request
 * deduplication, cancellation, and the artwork URL rewriting rules.
 *
 * Remote artwork goes through Coil's shared [ImageLoader]; local files fall
 * back to embedded/thumbnail extraction via [AlbumArtLoader].
 */
class ArtLoader(context: Context) {

    private val appContext = context.applicationContext

    private val imageLoader = ImageLoader.Builder(appContext)
        .memoryCache {
            MemoryCache.Builder(appContext)
                .maxSizePercent(0.03)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(File(appContext.cacheDir, "album_art"))
                .maxSizeBytes(128L * 1024 * 1024)
                .build()
        }
        .build()

    /**
     * Loads artwork for a song. [highRes] selects the larger variant used on the
     * Now Playing screen versus row thumbnails. [embeddedSize] sizes local-file
     * extraction when the caller needs something other than the default.
     */
    suspend fun load(song: Song, highRes: Boolean, embeddedSize: Int = if (highRes) 640 else 150): Bitmap? {
        val remoteUrl = song.remoteArtUrl?.takeIf { song.isStream && it.isNotEmpty() }
        if (remoteUrl != null) {
            val bitmap = fetchRemote(if (highRes) standardArtworkUrl(remoteUrl) else remoteUrl, highRes)
            if (bitmap != null) return bitmap
        }
        return embedded(song, embeddedSize)
    }

    private suspend fun fetchRemote(url: String, highRes: Boolean): Bitmap? {
        return try {
            val size = if (highRes) 640 else 256
            val request = ImageRequest.Builder(appContext)
                .data(url)
                .size(size)
                // Downstream consumers run palette extraction / canvas work on this
                // bitmap; a hardware-backed bitmap would break those operations.
                .allowHardware(false)
                .build()
            val drawable = imageLoader.execute(request).drawable
            (drawable as? BitmapDrawable)?.bitmap
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun embedded(song: Song, targetSize: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            AlbumArtLoader.getEmbeddedArt(appContext, song.uri, targetSize = targetSize)
        }

    companion object {
        /**
         * Rewrites provider thumbnail URLs to a single standard-quality variant
         * (~640px). Kept below the old 1280px cap so decoding, palette extraction
         * and canvas work stay light on low-end devices while remaining sharp
         * on screen. Pure function of the URL; kept here so the rules have one
         * home, reused by the notification artwork in PlayerGateway.
         */
        fun standardArtworkUrl(url: String): String = when {
            url.contains("=w") && url.contains("-h") ->
                url.replace(Regex("=w\\d+-h\\d+.*"), "=w640-h640-l90-rj")
            url.contains("=s") ->
                url.replace(Regex("=s\\d+.*"), "=s640-c-rj")
            url.contains("googleusercontent.com") && !url.contains("=") ->
                "$url=w640-h640-l90-rj"
            url.contains("ytimg.com") ->
                url.replace("maxresdefault.jpg", "hqdefault.jpg")
                    .replace("default.jpg", "hqdefault.jpg")
                    .replace("mqdefault.jpg", "hqdefault.jpg")
            else -> url
        }
    }
}
