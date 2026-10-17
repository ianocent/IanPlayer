package com.ianocent.musicplayer.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.ianocent.musicplayer.data.VideoStreamUri

/**
 * Media source factory that teaches the service player to play video streams.
 *
 * YouTube no longer serves progressive (muxed) video for most videos — only
 * separate video-only and audio-only adaptive streams. A plain MediaItem can
 * only carry one URI, so video mode hands over a `ianvideo://` placeholder
 * carrying both URLs; this factory turns it back into a [MergingMediaSource]
 * (video + audio) so ExoPlayer renders both together. Every other MediaItem is
 * delegated to the default factory untouched.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class VideoMergeMediaSourceFactory(context: Context) : MediaSource.Factory {

    private val appContext: Context = context.applicationContext
    private val defaults = DefaultMediaSourceFactory(context)

    override fun setDrmSessionManagerProvider(provider: DrmSessionManagerProvider): MediaSource.Factory =
        apply { defaults.setDrmSessionManagerProvider(provider) }

    override fun setLoadErrorHandlingPolicy(policy: LoadErrorHandlingPolicy): MediaSource.Factory =
        apply { defaults.setLoadErrorHandlingPolicy(policy) }

    override fun getSupportedTypes(): IntArray = defaults.supportedTypes

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val uri = mediaItem.localConfiguration?.uri
        if (uri == null || uri.scheme != VideoStreamUri.SCHEME) {
            return defaults.createMediaSource(mediaItem)
        }

        val videoUrl = VideoStreamUri.videoUrl(uri)
        if (videoUrl.isNullOrBlank()) return defaults.createMediaSource(mediaItem)

        val factory = ProgressiveMediaSource.Factory(DefaultDataSource.Factory(appContext))
        val videoSource = factory.createMediaSource(
            mediaItem.buildUpon().setUri(Uri.parse(videoUrl)).build()
        )

        val audioUrl = VideoStreamUri.audioUrl(uri)
        if (audioUrl.isNullOrBlank()) return videoSource

        val audioSource = factory.createMediaSource(
            mediaItem.buildUpon().setUri(Uri.parse(audioUrl)).build()
        )
        return MergingMediaSource(videoSource, audioSource)
    }
}
