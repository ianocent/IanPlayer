package com.ianocent.musicplayer.data.canvas.providers

import android.content.Context
import com.ianocent.musicplayer.data.Song
import com.ianocent.musicplayer.data.canvas.CanvasMode

/**
 * Phase 3/4 seam: a canvas provider owns the rendering strategy for a canvas
 * mode. GENERATIVE and KEN_BURNS are implemented by the UI layer
 * (PlayerCanvas) today; VIDEO uses the media surface path. Future phases
 * swap providers behind this contract without touching layouts.
 *
 * A provider may declare a [fallback] so the backdrop walks a real priority
 * chain instead of hardcoding "video missing → plain gradient". When the
 * primary provider cannot render the current song (e.g. no local video
 * found), the next provider in the chain is tried.
 */
interface CanvasProvider {
    val id: CanvasMode

    /** True when the provider renders via an external media surface. */
    val usesVideoSurface: Boolean
        get() = false

    /**
     * Next provider to try when this one cannot render the current song.
     * Null means this provider is a terminal fallback (always renders).
     */
    val fallback: CanvasProvider?
        get() = null

    /** Whether this provider can render [song] right now. */
    fun isAvailable(song: Song?, context: Context): Boolean = true

    companion object {
        fun forMode(mode: CanvasMode): CanvasProvider =
            CanvasProviderRegistry.descriptions.first { it.id == mode }

        /**
         * Walks the provider chain starting at [mode]'s provider and returns
         * the first reachable provider that can render [song]. Guaranteed to
         * return a non-null provider for a non-null [mode] because the chain
         * ends at an always-available terminal (GENERATIVE).
         */
        fun resolveFor(
            mode: CanvasMode?,
            song: Song?,
            context: Context
        ): CanvasProvider? {
            if (mode == null) return null
            var current: CanvasProvider? = forMode(mode)
            val seen = mutableSetOf<CanvasMode>()
            while (current != null && seen.add(current.id)) {
                if (current.isAvailable(song, context)) return current
                current = current.fallback
            }
            return null
        }
    }
}

object CanvasProviderRegistry {
    val descriptions: List<CanvasProvider> = listOf(
        GenerativeProvider,
        KenBurnsProvider,
        VideoProvider,
    )

    fun forMode(mode: CanvasMode): CanvasProvider =
        descriptions.first { it.id == mode }
}

/** Aurora generative overlay (UI draw layer in PlayerCanvas). Terminal fallback. */
object GenerativeProvider : CanvasProvider {
    override val id = CanvasMode.GENERATIVE
}

/** Ken Burns crop-window pan/zoom on the album art. */
object KenBurnsProvider : CanvasProvider {
    override val id = CanvasMode.KEN_BURNS
    override val fallback: CanvasProvider? = GenerativeProvider
}

/** Looping muted video behind the UI. Falls back to generative when no video. */
object VideoProvider : CanvasProvider {
    override val id = CanvasMode.VIDEO
    override val usesVideoSurface = true
    override val fallback: CanvasProvider? = GenerativeProvider

    override fun isAvailable(song: Song?, context: Context): Boolean =
        com.ianocent.musicplayer.data.canvas.CanvasVideoSource.resolve(song, context) != null
}