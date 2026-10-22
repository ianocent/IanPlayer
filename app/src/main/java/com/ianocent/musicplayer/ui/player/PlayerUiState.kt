package com.ianocent.musicplayer.ui.player

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.ianocent.musicplayer.data.LyricLine
import com.ianocent.musicplayer.data.LyricSource
import com.ianocent.musicplayer.data.Song
import com.ianocent.musicplayer.data.canvas.CanvasMode
import com.ianocent.musicplayer.data.canvas.CanvasVideoSource
import com.ianocent.musicplayer.data.getAdaptiveControlColor
import com.ianocent.musicplayer.viewmodel.MusicViewModel

data class PlayerPlaybackState(
    val song: Song?,
    val isPlaying: Boolean,
    val durationMs: Long,
    val isBuffering: Boolean,
    val isShuffleOn: Boolean,
    val repeatMode: Int,
    val queue: List<Song>,
)

enum class PlayerScreenSection {
    CONTROLS,
    UP_NEXT,
    LYRICS,
}

data class VisualSlice(
    val albumArt: Bitmap?,
    val ambientColor: Color,
    val paletteColors: List<Color>,
    val surfaceColor: Color,
    val canvasMode: CanvasMode?,
    val canvasVideoSource: String?,
    val canvasUsesVideo: Boolean,
    /** Live Spotify canvas URL for the current song (null = none/no canvas). */
    val remoteCanvasUrl: String?,
    val canvasFadeBeforeUpnext: Boolean,
)

data class LyricsSlice(
    val lyricSource: LyricSource?,
    val syncedLyric: List<LyricLine>?,
    val plainLyric: String?,
    val isLyricLoading: Boolean,
)

data class PreferenceSlice(
    val isDarkMode: Boolean,
    val reduceMotion: Boolean,
    val motionScale: Float,
    val isFavorite: Boolean,
)

data class NavigationSlice(
    val currentSection: PlayerScreenSection,
)

data class PlayerUiState(
    val playback: PlayerPlaybackState,
    val visuals: VisualSlice,
    val lyrics: LyricsSlice,
    val prefs: PreferenceSlice,
    val nav: NavigationSlice,
)

/**
 * Phase 2 state pipeline: single collect point for the whole player. Every
 * layout reads one PlayerUiState instead of poking the ViewModel flow-by-flow.
 * Canvas video path resolution happens here (once per song/mode) and is shared
 * with PlayerBackdrop through canvasUsesVideo + canvasVideoSource.
 */
@Composable
fun rememberPlayerUiState(
    viewModel: MusicViewModel,
    currentSection: PlayerScreenSection = PlayerScreenSection.CONTROLS,
): PlayerUiState {
    val song by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val durationMs by viewModel.duration.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val isShuffleOn by viewModel.isShuffleOn.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val queue by viewModel.queue.collectAsState()
    val albumArt by viewModel.albumArt.collectAsState()
    val ambientColor by viewModel.ambientColor.collectAsState()
    val paletteColors by viewModel.paletteColors.collectAsState()
    val canvasModeId by viewModel.canvasMode.collectAsState()
    val lyricSource by viewModel.lyricSource.collectAsState()
    val syncedLyric by viewModel.syncedLyric.collectAsState()
    val plainLyric by viewModel.plainLyric.collectAsState()
    val isLyricLoading by viewModel.isLyricLoading.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val reduceMotion by viewModel.reduceMotion.collectAsState()
    val motionScale by viewModel.motionScale.collectAsState()
    val canvasFadeBeforeUpnext by viewModel.canvasFadeBeforeUpnext.collectAsState()
    val spotifyCanvas by viewModel.spotifyCanvas.collectAsState()

    val canvasMode = remember(canvasModeId) { CanvasMode.from(canvasModeId) }
    val context = LocalContext.current
    val canvasVideoSource = remember(song?.id, canvasModeId) {
        if (canvasMode == CanvasMode.VIDEO) CanvasVideoSource.resolve(song, context) else null
    }
    val isFavorite = song?.let { favoriteIds.contains(it.id) } == true
    val surfaceColor = getAdaptiveControlColor(ambientColor, isDarkMode)

    return PlayerUiState(
        playback = PlayerPlaybackState(
            song = song,
            isPlaying = isPlaying,
            durationMs = durationMs,
            isBuffering = isBuffering,
            isShuffleOn = isShuffleOn,
            repeatMode = repeatMode,
            queue = queue,
        ),
        visuals = VisualSlice(
            albumArt = albumArt,
            ambientColor = ambientColor,
            paletteColors = paletteColors,
            surfaceColor = surfaceColor,
            canvasMode = canvasMode,
            canvasVideoSource = canvasVideoSource,
            canvasUsesVideo = canvasVideoSource != null,
            remoteCanvasUrl = spotifyCanvas?.takeIf { it.matched && !it.canvasUrl.isNullOrBlank() }?.canvasUrl,
            canvasFadeBeforeUpnext = canvasFadeBeforeUpnext,
        ),
        lyrics = LyricsSlice(
            lyricSource = lyricSource,
            syncedLyric = syncedLyric,
            plainLyric = plainLyric,
            isLyricLoading = isLyricLoading,
        ),
        prefs = PreferenceSlice(
            isDarkMode = isDarkMode,
            reduceMotion = reduceMotion,
            motionScale = motionScale,
            isFavorite = isFavorite,
        ),
        nav = NavigationSlice(
            currentSection = currentSection,
        ),
    )
}