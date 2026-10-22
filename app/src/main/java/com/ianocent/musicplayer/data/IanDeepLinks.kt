package com.ianocent.musicplayer.data

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Deep links for IanPlayer.
 *
 * Two directions:
 *  - Inbound: [parse] turns a shared link (IanPlayer scheme or a YouTube /
 *    YouTube Music URL) into the video id the app should play.
 *  - Outbound: [shareUri] builds an `intent://` URL that opens the app when it
 *    is installed and falls back to the GitHub release page when it is not,
 *    which is exactly the "download first" flow we want in chat apps.
 */
object IanDeepLinks {
    const val SCHEME = "ianplayer"
    const val PACKAGE = "com.ianocent.musicplayer"
    const val RELEASE_URL = "https://github.com/ianocent/IanPlayer/releases/latest"

    private val VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")

    /** What an inbound link asks the player to do. */
    data class Request(val videoId: String, val preferVideo: Boolean)

    /**
     * Accepts:
     *  - `ianplayer://song?v=<videoId>[&type=video]`  (share-link skema bawaan)
     *  - `ianplayer://open/<videoId>`
     *  - `https://youtu.be/<videoId>`
     *  - `https://(www|m|music).youtube.com/watch?v=<videoId>`
     *  - `.../shorts/<id>`, `.../live/<id>`, `.../embed/<id>`
     *
     * Returns null for anything that is not a single playable video.
     */
    fun parse(uri: Uri): Request? {
        val scheme = uri.scheme?.lowercase() ?: return null

        if (scheme == SCHEME) {
            val fromQuery = uri.getQueryParameter("v") ?: uri.getQueryParameter("id")
            val fromPath = uri.pathSegments.firstOrNull { VIDEO_ID.matches(it) }
            val id = fromQuery ?: fromPath ?: return null
            if (!VIDEO_ID.matches(id)) return null
            val preferVideo = uri.getQueryParameter("type")?.equals("video", ignoreCase = true) == true
            return Request(id, preferVideo)
        }

        // YouTube app's internal share scheme: vnd.youtube:<videoId>
        if (scheme == "vnd.youtube") {
            val id = uri.schemeSpecificPart
                ?.substringBefore('?')
                ?.substringBefore('&')
                ?.trim('/')
            if (id == null || !VIDEO_ID.matches(id)) return null
            return Request(id, preferVideo = true)
        }

        val host = uri.host?.lowercase() ?: return null
        val id = when {
            host == "youtu.be" || host.endsWith(".youtu.be") ->
                uri.pathSegments.firstOrNull()

            host == "youtube.com" || host.endsWith(".youtube.com") -> {
                val segments = uri.pathSegments
                when (segments.firstOrNull()) {
                    "watch" -> uri.getQueryParameter("v")
                    "shorts", "live", "embed", "v" -> segments.getOrNull(1)
                    else -> uri.getQueryParameter("v")
                }
            }

            else -> null
        } ?: return null

        if (!VIDEO_ID.matches(id)) return null
        // Regular YouTube links are video-first; music.youtube.com is audio-first.
        return Request(id, preferVideo = !host.startsWith("music."))
    }

    /**
     * Custom-scheme link (`ianplayer://song?v=<id>`) that Android routes straight
     * to IanPlayer when installed. Works in chat apps (WA, Telegram, Discord)
     * unlike `intent://` which only Chrome understands. When app is not installed
     * the link does nothing, so the share text below still points at the GitHub
     * release page as the download fallback.
     */
    fun shareUri(videoId: String, preferVideo: Boolean = false): String {
        val type = if (preferVideo) "&type=video" else ""
        return "ianplayer://song?v=$videoId$type"
    }

    /** Human-readable payload for ACTION_SEND, or null when the song has no shareable id. */
    fun shareText(song: Song): String? {
        val videoId = song.remoteId
        if (!song.isStream || videoId.isNullOrBlank()) return null
        return buildString {
            append("🎵 ")
            append(song.title)
            if (song.artist.isNotBlank()) {
                append(" — ")
                append(song.artist)
            }
            append("\n\nBuka di IanPlayer:\n")
            append(shareUri(videoId, song.isVideo))
            append("\n\nBelum punya IanPlayer? Download dulu:\n")
            append(RELEASE_URL)
        }
    }

    /** Opens the system share sheet for a stream song. Returns false for local/unknown songs. */
    fun shareSong(context: Context, song: Song): Boolean {
        val text = shareText(song) ?: return false
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "${song.title} — ${song.artist}")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(
            Intent.createChooser(send, "Share song").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return true
    }
}
