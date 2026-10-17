package com.ianocent.musicplayer.ui.player

import androidx.compose.runtime.Composable
import com.ianocent.musicplayer.data.ElementRect
import com.ianocent.musicplayer.ui.NowPlayingScreen
import com.ianocent.musicplayer.viewmodel.MusicViewModel

/**
 * Entry point for the full-screen player. Renders the single base layout
 * (Ian Classic / NowPlayingScreen). Alternate player layouts were removed;
 * glass UI is applied uniformly across the base layout.
 */
@Composable
fun PlayerHost(
    viewModel: MusicViewModel,
    onBack: () -> Unit,
    initialAlbumArtRect: ElementRect? = null,
) {
    NowPlayingScreen(
        viewModel = viewModel,
        onBack = onBack,
        initialAlbumArtRect = initialAlbumArtRect,
    )
}
