package com.ianocent.musicplayer.data

import android.net.Uri
import android.util.Base64

/**
 * A resolvable video for a YouTube song. Either a progressive file that already
 * carries both tracks ([audioUrl] null), or a video-only stream paired with a
 * separate audio-only stream that ExoPlayer merges on the service side.
 */
data class VideoStream(
    val videoUrl: String,
    val audioUrl: String? = null
) {
    val isProgressive: Boolean get() = audioUrl.isNullOrBlank()
}

/**
 * Builds and parses the `ianvideo://` placeholder URI that carries a
 * [VideoStream] through a MediaItem to the service player, where
 * `VideoMergeMediaSourceFactory` turns it back into real media sources.
 * The URLs are Base64 (URL-safe) encoded so their own query strings survive
 * the round-trip untouched.
 */
object VideoStreamUri {
    const val SCHEME = "ianvideo"

    fun build(stream: VideoStream): Uri {
        val builder = Uri.Builder()
            .scheme(SCHEME)
            .authority("stream")
            .appendQueryParameter(KEY_VIDEO, encode(stream.videoUrl))
        if (!stream.audioUrl.isNullOrBlank()) {
            builder.appendQueryParameter(KEY_AUDIO, encode(stream.audioUrl))
        }
        return builder.build()
    }

    fun videoUrl(uri: Uri): String? = uri.getQueryParameter(KEY_VIDEO)?.let(::decode)

    fun audioUrl(uri: Uri): String? = uri.getQueryParameter(KEY_AUDIO)?.let(::decode)

    private const val KEY_VIDEO = "v"
    private const val KEY_AUDIO = "a"

    private fun encode(value: String): String =
        Base64.encodeToString(value.toByteArray(), FLAGS)

    private fun decode(value: String): String? = try {
        String(Base64.decode(value, FLAGS))
    } catch (_: IllegalArgumentException) {
        null
    }

    private const val FLAGS = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
}
