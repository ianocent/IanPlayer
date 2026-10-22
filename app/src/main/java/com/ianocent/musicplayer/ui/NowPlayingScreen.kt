package com.ianocent.musicplayer.ui
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.MusicNote
import com.ianocent.musicplayer.R
import com.ianocent.musicplayer.ui.glass.glassIconBackdrop
import com.ianocent.musicplayer.ui.glass.isAppInDark
import com.ianocent.musicplayer.ui.glass.nounIcon



import android.view.TextureView
import com.ianocent.musicplayer.ResponsiveSnapList
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ianocent.musicplayer.player.PlaybackService
import com.ianocent.musicplayer.viewmodel.MusicViewModel
import java.util.concurrent.TimeUnit
import androidx.compose.foundation.rememberScrollState
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.ianocent.musicplayer.data.AlbumArtLoader
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import com.ianocent.musicplayer.data.LyricSource
import com.ianocent.musicplayer.data.IanDeepLinks
import com.ianocent.musicplayer.data.Song
import com.ianocent.musicplayer.data.StreamSources
import com.ianocent.musicplayer.data.ElementRect
import com.ianocent.musicplayer.data.canvas.CanvasVideoSource
import com.ianocent.musicplayer.ui.canvas.PlayerCanvasVideoSurface
import com.ianocent.musicplayer.ui.glass.IanGlassSurface
import com.ianocent.musicplayer.ui.glass.IanGlassButton
import com.ianocent.musicplayer.ui.glass.rememberGlassConfig
import com.ianocent.musicplayer.ui.glass.IanGlassConfig
import com.ianocent.musicplayer.data.SettingsStore
import com.ianocent.musicplayer.ui.motion.MotionTokens
import com.ianocent.musicplayer.ui.player.rememberPlayerUiState
import com.ianocent.musicplayer.ui.player.components.LyricSectionHeader
import com.ianocent.musicplayer.ui.player.components.PlayerControlsBar
import com.ianocent.musicplayer.ui.player.components.PlayerSeekBar
import com.ianocent.musicplayer.ui.player.components.UpnextSectionHeader
import com.ianocent.musicplayer.ui.player.components.rememberPlayerBackdrop
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.viewinterop.AndroidView
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState


@Composable
fun NowPlayingScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit,
    initialAlbumArtRect: ElementRect? = null
) {
    val uiState = rememberPlayerUiState(viewModel)
    val song = uiState.playback.song
    val isPlaying = uiState.playback.isPlaying
    // Kept as a State (not a hoisted Long) so the 100ms position tick only
    // recomposes the leaves that actually show time/lyrics — not this screen.
    val positionState = viewModel.currentPosition.collectAsState()
    val duration = uiState.playback.durationMs
    val isBuffering = uiState.playback.isBuffering
    val isShuffleOn = uiState.playback.isShuffleOn
    val repeatMode = uiState.playback.repeatMode
    val albumArt = uiState.visuals.albumArt
    val ambientColor = uiState.visuals.ambientColor
    val isDarkMode = uiState.prefs.isDarkMode
    val syncedLyric = uiState.lyrics.syncedLyric
    val plainLyric = uiState.lyrics.plainLyric
    val isLyricLoading = uiState.lyrics.isLyricLoading
    val lyricSource = uiState.lyrics.lyricSource

    val offsetY = remember { Animatable(0f) }
    val dismissThreshold = 300f
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val screenHeight = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val screenWidth = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val dragProgress = (offsetY.value / screenHeight).coerceIn(0f, 1f)
    val heroAnimProgress = remember { Animatable(0f) }
    var targetAlbumArtRect by remember { mutableStateOf<ElementRect?>(null) }
    var heroInitScale by remember { mutableStateOf(1f) }
    var heroInitOffsetX by remember { mutableStateOf(0f) }
    var heroInitOffsetY by remember { mutableStateOf(0f) }
    var isExiting by remember { mutableStateOf(false) }

    // FIX (root cause #2, see 04-Deep-Fix-And-Optimization-Trace.md):
    // `targetAlbumArtRect` used to be captured on every `onGloballyPositioned`
    // call from the SAME node that also carries the morph `graphicsLayer`
    // (scaleX/Y + translationX/Y driven by heroAnimProgress). Because
    // `onGloballyPositioned` reports position *after* transforms are applied,
    // every animation frame moved the reported position, which changed
    // `targetAlbumArtRect`, which — since the old effect was keyed on
    // `targetAlbumArtRect` too — cancelled and restarted the animation from
    // snapTo(0f) on every single frame. Net effect: the hero morph visually
    // froze partway (small, offset artwork stuck near its mini-player start
    // position) instead of ever reaching progress = 1.
    //
    // Fix: the effect is now keyed ONLY on `initialAlbumArtRect` (the one
    // value that's genuinely stable for the lifetime of one player-open
    // gesture), and it waits for the first non-null `targetAlbumArtRect`
    // via snapshotFlow instead of restarting whenever that value changes.
    // `targetAlbumArtRect` itself is also only captured ONCE (see the
    // `onGloballyPositioned` callback below) — it's the artwork's resting
    // position in the fully-expanded layout, which never changes for a given
    // composition, so there's no need to keep re-measuring it every frame.
    LaunchedEffect(initialAlbumArtRect) {
        val from = initialAlbumArtRect ?: return@LaunchedEffect

        val to = snapshotFlow { targetAlbumArtRect }
            .filterNotNull()
            .first()

        heroInitScale = (from.size.width / to.size.width).coerceIn(0.15f, 1f)
        heroInitOffsetX = from.center.x - to.center.x
        heroInitOffsetY = from.center.y - to.center.y

        heroAnimProgress.snapTo(0f)
        heroAnimProgress.animateTo(
            1f,
            animationSpec = MotionTokens.scaledTween(MotionTokens.EXPRESSIVE, uiState.prefs.motionScale, uiState.prefs.reduceMotion)
        )
    }

    val heroCornerRadius = if (initialAlbumArtRect != null) {
        val p = heroAnimProgress.value
        (18f * (1f - p) + 12f * p).dp
    } else 12.dp

    val handleBack: () -> Unit = {
        if (initialAlbumArtRect != null && heroAnimProgress.value > 0.05f) {
            isExiting = true
            coroutineScope.launch {
                heroAnimProgress.animateTo(0f, animationSpec = MotionTokens.scaledTween(MotionTokens.STANDARD, uiState.prefs.motionScale, uiState.prefs.reduceMotion))
            }
        }
        onBack()
    }

    BackHandler(enabled = true) {
        handleBack()
    }

    // Video mode: only meaningful for streamed songs (a real video stream can
    // be resolved). When active the layout swaps the art/lyrics for a rounded
    // video surface with the title above and the seek bar below.
    val videoMode by viewModel.videoMode.collectAsState()
    val isStreamSong = song?.isStream == true &&
        song?.source == StreamSources.YOUTUBE &&
        !song?.remoteId.isNullOrBlank()
    val videoModeActive = videoMode && isStreamSong
    val showLyrics = !videoModeActive

    // While video is on-screen, drive the backdrop vibrance from colors sampled
    // off the video frames instead of the album art — feels dynamic.
    val videoAmbient by viewModel.videoAmbientColor.collectAsState()
    val effectiveAmbient = if (videoModeActive && videoAmbient != null) videoAmbient!! else ambientColor

    val backdrop = rememberPlayerBackdrop(
        song = song,
        canvasModeId = uiState.visuals.canvasMode?.persisted ?: SettingsStore.CANVAS_OFF,
        isPlaying = isPlaying,
        ambientColor = effectiveAmbient,
        paletteColors = uiState.visuals.paletteColors,
        albumArt = albumArt,
        isDarkMode = isDarkMode,
        reduceMotion = uiState.prefs.reduceMotion,
        remoteCanvasUrl = uiState.visuals.remoteCanvasUrl,
        motionScale = uiState.prefs.motionScale,
        screenWidth = screenWidth,
        screenHeight = screenHeight,
    )

    // Live canvas is a single ON/OFF: ON shows the best live backdrop for the
    // current song — the looping video when a local canvas file exists, else
    // the animated "look of the song" gradient (aurora tinted from its art).
    val canvasIsOn = uiState.visuals.canvasMode != null
    val canvasContext = LocalContext.current
    val preferredLiveMode = remember(song?.id) {
        if (CanvasVideoSource.resolve(song, canvasContext) != null) {
            SettingsStore.CANVAS_VIDEO
        } else {
            SettingsStore.CANVAS_GENERATIVE
        }
    }
    var selectedLyricLines by remember { mutableStateOf(setOf<Int>()) }
    var showLyricCardSheet by remember { mutableStateOf(false) }

    // Canvas banner boundary: video must end exactly above the lyric header
    // row. Root box + boundary positions are captured toward window coords so
    // the banner height fraction can be derived at composition.
    var canvasRootTopY by remember { mutableStateOf(0f) }
    var canvasRootHeight by remember { mutableStateOf(0f) }
    var canvasBoundaryY by remember { mutableStateOf(0f) }
    var showWaveRecordSheet by remember { mutableStateOf(false) }

    val glassEnabled by viewModel.glassEnabled.collectAsState()
    val glassBlur by viewModel.glassBlur.collectAsState()
    val glassVibrancy by viewModel.glassVibrancy.collectAsState()
    val glassConfig = rememberGlassConfig(glassEnabled, glassBlur, glassVibrancy)
    // Shared backdrop source for the player glass panels (UpNext, Controls, etc).
    val hazeState = rememberHazeState(blurEnabled = glassEnabled)

    // Shared animation specs for section expand/collapse (lyrics ↔ upnext ↔ filler)
    val sectionAnimSpec = MotionTokens.scaledTween<Float>(MotionTokens.SECTION_EXPAND, uiState.prefs.motionScale, uiState.prefs.reduceMotion)
    val sectionSpacerAnimSpec = MotionTokens.scaledTween<Dp>(MotionTokens.SECTION_EXPAND, uiState.prefs.motionScale, uiState.prefs.reduceMotion)

    var isLyricExpanded by remember { mutableStateOf(true) }
    var isUpnextExpanded by remember { mutableStateOf(true) }

    val lyricWeight by animateFloatAsState(
        targetValue = when {
            !showLyrics -> 0f
            isLyricExpanded && isUpnextExpanded -> 0.4f
            isLyricExpanded -> 1f
            else -> 0f
        },
        animationSpec = sectionAnimSpec,
        label = "lyricWeight"
    )
    val upnextWeight by animateFloatAsState(
        targetValue = when {
            isUpnextExpanded && isLyricExpanded && showLyrics -> 0.6f
            isUpnextExpanded -> 1f
            else -> 0f
        },
        animationSpec = sectionAnimSpec,
        label = "upnextWeight"
    )
    val fillerWeight by animateFloatAsState(
        targetValue = if (!isLyricExpanded && !isUpnextExpanded) 1f else 0f,
        animationSpec = sectionAnimSpec,
        label = "fillerWeight"
    )
    val lyricAlpha by animateFloatAsState(
        targetValue = if (isLyricExpanded) 1f else 0f,
        animationSpec = sectionAnimSpec,
        label = "lyricAlpha"
    )
    val upnextAlpha by animateFloatAsState(
        targetValue = if (isUpnextExpanded) 1f else 0f,
        animationSpec = sectionAnimSpec,
        label = "upnextAlpha"
    )
    val sectionSpacerHeight by animateDpAsState(
        targetValue = if (isLyricExpanded && isUpnextExpanded) 12.dp else 0.dp,
        animationSpec = sectionSpacerAnimSpec,
        label = "sectionSpacerHeight"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(0, offsetY.value.roundToInt()) }
            .graphicsLayer {
                scaleX = 1f - dragProgress * 0.05f
                scaleY = 1f - dragProgress * 0.05f
            }
    ) {
        // Haze backdrop source — a sibling of the content Column (not an ancestor
        // of the glass panels), so the frosted surfaces sample ONLY this layer.
        Box(
            Modifier
                .matchParentSize()
                .hazeSource(hazeState)
                .then(backdrop.contentModifier)
                .onGloballyPositioned { coords ->
                    canvasRootTopY = coords.positionInRoot().y
                    canvasRootHeight = coords.size.height.toFloat()
                }
        ) {
            if (backdrop.canvasUsesVideo) {
                // Canvas "banner" design: the video ends exactly at the lyric
                // header row (Collapse/Expand • Lyric Card • Switch Lyric) —
                // measured live — and fades out smoothly into the ambient
                // gradient so lyrics below stay readable. Never full-screen.
                val boundaryFraction =
                    if (canvasRootHeight > 0f && canvasBoundaryY > 0f) {
                        ((canvasBoundaryY - canvasRootTopY) / canvasRootHeight)
                            .coerceIn(0.35f, 0.75f)
                    } else 0.5f
                PlayerCanvasVideoSurface(
                    path = backdrop.canvasVideoPath!!,
                    isPlaying = isPlaying,
                    tint = backdrop.animatedAmbient,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(boundaryFraction)
                        .align(Alignment.TopStart)
                        .graphicsLayer {
                            compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                        }
                        .drawWithContent {
                            drawContent()
                            // Fade the video's bottom edge to transparent —
                            // reveals the ambient gradient behind it smoothly.
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0.0f to Color.Black,
                                    0.35f to Color.Black,
                                    1.0f to Color.Transparent
                                ),
                                blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
                            )
                        }
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(20.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (offsetY.value > dismissThreshold) {
                                coroutineScope.launch { offsetY.snapTo(0f) }
                                handleBack()
                            } else {
                                coroutineScope.launch {
                                    if (uiState.prefs.reduceMotion) offsetY.snapTo(0f) else offsetY.animateTo(0f, animationSpec = spring())
                                }
                            }
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            coroutineScope.launch {
                                offsetY.snapTo((offsetY.value + dragAmount).coerceAtLeast(0f))
                            }
                        }
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Album art + song info — swapped for the video stage in video mode.
            if (videoModeActive) {
                VideoStage(
                    song = song,
                    viewModel = viewModel,
                    positionState = positionState,
                    durationMs = duration,
                    accentColor = backdrop.adaptiveColor
                )
            } else {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .onGloballyPositioned { coords ->
                            // FIX: capture the resting rect ONCE. Re-measuring on every
                            // callback (which fires again as the morph's own
                            // graphicsLayer moves this node) is what caused the
                            // feedback loop described above. This node's resting
                            // position in the expanded layout doesn't change, so one
                            // capture is both correct and sufficient.
                            if (targetAlbumArtRect == null) {
                                val pos = coords.positionInWindow()
                                val sz = coords.size
                                targetAlbumArtRect = ElementRect(
                                    center = Offset(pos.x + sz.width / 2f, pos.y + sz.height / 2f),
                                    size = androidx.compose.ui.geometry.Size(sz.width.toFloat(), sz.height.toFloat())
                                )
                            }
                        }
                        .graphicsLayer {
                            rotationZ = 0f
                            val p = heroAnimProgress.value
                            if (initialAlbumArtRect != null) {
                                scaleX = heroInitScale + (1f - heroInitScale) * p
                                scaleY = heroInitScale + (1f - heroInitScale) * p
                                translationX = heroInitOffsetX * (1f - p)
                                translationY = heroInitOffsetY * (1f - p)
                            }
                            clip = true
                            shape = RoundedCornerShape(heroCornerRadius)
                        }
                        .clip(RoundedCornerShape(heroCornerRadius))
                        .background(
                            if (albumArt == null)
                                Brush.linearGradient(listOf(Color(0xFF8B1E1E), Color(0xFF2B0A0A)))
                            else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (albumArt != null) {
                        Image(
                            bitmap = albumArt.asImageBitmap(),
                            contentDescription = "Album Art",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (isBuffering) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp).padding(bottom = 8.dp),
                                    color = Color.White,
                                    strokeWidth = 3.dp
                                )
                            } else {
                                AlbumArtTimer(positionState)
                            }
                        }
                    } else {
                        if (isBuffering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = Color.White,
                                strokeWidth = 3.dp
                            )
                        } else {
                            AlbumArtTimer(positionState, fontSize = 22, singleLine = true)
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // Teks polos di atas backdrop — glass HANYA buat tombol bulat kecil
                    // (pola Convx §2), bukan bungkus baris teks jadi card.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Song by :", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        song?.let { s ->
                            val liked = uiState.prefs.isFavorite
                            val btnConfig = glassConfig.copy(
                                cornerRadius = 20f, blurRadius = 12f, vibrancy = 0.3f
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Canvas mode button hidden for now (no design direction yet).
                                /*
                                IanGlassButton(
                                    config = btnConfig,
                                    onClick = {
                                        viewModel.setCanvasMode(
                                            if (canvasIsOn) SettingsStore.CANVAS_OFF else preferredLiveMode
                                        )
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = "Canvas",
                                        tint = if (uiState.visuals.canvasMode != null) backdrop.adaptiveColor else Color.Gray,
                                        modifier = Modifier.size(22.dp).align(Alignment.Center)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                */
                                IanGlassButton(
                                    config = btnConfig,
                                    onClick = { viewModel.toggleFavorite(s.id) },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(
                                        Modifier
                                            .matchParentSize()
                                            .glassIconBackdrop(
                                                dark = isAppInDark(),
                                                accent = if (liked) Color(0xFFE91E63) else Color.Gray,
                                                active = liked
                                            )
                                    )
                                    Icon(
                                        imageVector = if (liked) nounIcon(R.drawable.noun_love) else nounIcon(R.drawable.noun_love_outline),
                                        contentDescription = if (liked) "Unlike" else "Like",
                                        tint = if (liked) Color(0xFFE91E63) else Color.Gray,
                                        modifier = Modifier.size(22.dp).align(Alignment.Center)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                IanGlassButton(
                                    config = btnConfig,
                                    onClick = { showWaveRecordSheet = true },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(
                                        Modifier
                                            .matchParentSize()
                                            .glassIconBackdrop(dark = isAppInDark(), accent = Color(0xFFE91E63))
                                    )
                                    Icon(
                                        imageVector = nounIcon(R.drawable.noun_record),
                                        contentDescription = "Record",
                                        tint = Color(0xFFE91E63),
                                        modifier = Modifier.size(22.dp).align(Alignment.Center)
                                    )
                                }
                                if (s.isStream && !s.remoteId.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    val shareContext = LocalContext.current
                                    IanGlassButton(
                                        config = btnConfig,
                                        onClick = { IanDeepLinks.shareSong(shareContext, s) },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(
                                            Modifier
                                                .matchParentSize()
                                                .glassIconBackdrop(dark = isAppInDark(), accent = backdrop.adaptiveColor)
                                        )
                                        Icon(
                                            imageVector = Icons.Rounded.Share,
                                            contentDescription = "Share",
                                            tint = backdrop.adaptiveColor,
                                            modifier = Modifier.size(20.dp).align(Alignment.Center)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    AnimatedContent(
                        targetState = song,
                        transitionSpec = {
                            (fadeIn(animationSpec = MotionTokens.smallUiChange) togetherWith
                                    fadeOut(animationSpec = MotionTokens.smallUiChange))
                                .using(SizeTransform(clip = false))
                        },
                        label = "trackInfo"
                    ) { currentSong ->
                        Column {
                            Text(
                                currentSong?.artist ?: "Unknown Artist",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                currentSong?.title ?: "No song playing",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    PlayerSeekBar(
                        positionState = positionState,
                        durationMs = duration,
                        accentColor = backdrop.adaptiveColor,
                        onSeekFraction = { fraction -> viewModel.seekTo((fraction * duration).toLong()) }
                    )
                }
            }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Lyric & Upnext
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (showLyrics) {
                // Lyric toggle
                Box(Modifier.onGloballyPositioned { coords ->
                    (coords.positionInRoot().y + coords.size.height).let { if (it > 0f) canvasBoundaryY = it }
                }) {
                LyricSectionHeader(
                    isExpanded = isLyricExpanded,
                    hasSelectedLines = selectedLyricLines.isNotEmpty(),
                    lyricSource = lyricSource,
                    isLyricLoading = isLyricLoading,
                    hasSong = song != null,
                    accentColor = backdrop.adaptiveColor,
                    onToggle = { isLyricExpanded = !isLyricExpanded },
                    onOpenLyricCard = { if (selectedLyricLines.isNotEmpty()) showLyricCardSheet = true },
                    onCycleSource = { song?.let { viewModel.cycleLyricSource(it) } }
                )
                }
                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .weight(lyricWeight.coerceAtLeast(0.001f))
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = lyricAlpha
                            clip = true
                        }
                ) {
                    if (lyricWeight > 0.02f) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            when {
                                isLyricLoading -> SkeletonLyricLoader(adaptiveColor = backdrop.adaptiveColor, reduceMotion = uiState.prefs.reduceMotion)
                                !syncedLyric.isNullOrEmpty() -> SyncedLyricView(
                                    lines = syncedLyric!!,
                                    positionState = positionState,
                                    highlightColor = backdrop.adaptiveColor,
                                    selectedIndices = selectedLyricLines,
                                    reduceMotion = uiState.prefs.reduceMotion,
                                    motionScale = uiState.prefs.motionScale,
                                    onLineClick = { index ->
                                        selectedLyricLines = if (selectedLyricLines.contains(index))
                                            selectedLyricLines - index else selectedLyricLines + index
                                    }
                                )
                                !plainLyric.isNullOrBlank() -> {
                                    val lyricScrollState = rememberScrollState()
                                    Text(plainLyric!!, textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth().verticalScroll(lyricScrollState)
                                            .padding(horizontal = 16.dp).padding(bottom = 24.dp))
                                }
                                else -> Column(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .padding(horizontal = 24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        "No Lyrics of This Shit Song",
                                        color = Color.Gray,
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "Keep ur mouth silent and enjoy the music :)",
                                        color = Color.Gray.copy(alpha = 0.7f),
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
                }

                Spacer(modifier = Modifier.height(sectionSpacerHeight))

                // Upnext toggle
                UpnextSectionHeader(
                    isExpanded = isUpnextExpanded,
                    onToggle = { isUpnextExpanded = !isUpnextExpanded }
                )
                Spacer(modifier = Modifier.height(6.dp))

                val songs = uiState.playback.queue
                val upNextSongs = remember(songs, song) {
                    val idx = songs.indexOfFirst { it.id == song?.id }
                    (if (idx == -1) songs else songs.drop(idx + 1)).toMutableStateList()
                }
                val upnextListState = rememberLazyListState()
                val upnextReorderableState = rememberReorderableLazyListState(
                    lazyListState = upnextListState,
                    onMove = { from, to ->
                        upNextSongs.add(to.index, upNextSongs.removeAt(from.index))
                        viewModel.reorderUpNext(from.index, to.index)
                    }
                )

                Box(
                    modifier = Modifier
                        .weight(upnextWeight.coerceAtLeast(0.001f))
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = upnextAlpha
                            clip = true
                        }
                ) {
                    if (upnextWeight > 0.02f) {
                        val upnextGlassConfig = glassConfig.copy(
                            tint = backdrop.adaptiveColor.copy(alpha = 0.10f),
                            cornerRadius = 24f
                        )
                        IanGlassSurface(
                            config = upnextGlassConfig,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.fillMaxSize(),
                            state = hazeState,
                            liveBackdrop = isPlaying && !uiState.prefs.reduceMotion
                        ) {
                            ResponsiveSnapList(
                                items = upNextSongs,
                                key = { it.id },
                                scrollbarColor = backdrop.adaptiveColor,
                                modifier = Modifier.padding(12.dp),
                                listState = upnextListState,
                                minItemHeight = 64.dp,
                                bottomPadding = 4.dp
                            ) { upSong, _ ->
                                ReorderableItem(upnextReorderableState, key = upSong.id) { isDragging ->
                                    val elevation by animateDpAsState(
                                        if (isDragging) 8.dp else 0.dp,
                                        label = "upnext_drag_elevation"
                                    )
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .zIndex(if (isDragging) 1f else 0f)
                                            .shadow(elevation, RoundedCornerShape(12.dp))
                                            .padding(vertical = 4.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                                                alpha = 0.3f
                                            )
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .padding(start = 8.dp)
                                                    .size(24.dp)
                                                    .clip(CircleShape)
                                                    .background(backdrop.adaptiveColor.copy(alpha = 0.2f))
                                                    .draggableHandle(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Rounded.DragHandle,
                                                    contentDescription = "Drag",
                                                    tint = backdrop.adaptiveColor,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            Spacer(Modifier.width(8.dp))
                                            UpnextSongRow(
                                                upSong = upSong,
                                                viewModel = viewModel,
                                                isDarkMode = isDarkMode,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                viewModel.playSong(upSong)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (fillerWeight > 0.01f) {
                    Spacer(modifier = Modifier.weight(fillerWeight))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Controls
            Text(
                text = "Controls :",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))

            PlayerControlsBar(
                isPlaying = isPlaying,
                isShuffleOn = isShuffleOn,
                repeatMode = repeatMode,
                isDarkMode = isDarkMode,
                accentColor = backdrop.adaptiveColor,
                onPlayPause = { viewModel.togglePlayPause() },
                onPrevious = { viewModel.playPrevious() },
                onNext = { viewModel.playNext() },
                onToggleShuffle = { viewModel.toggleShuffle() },
                onToggleRepeat = { viewModel.toggleRepeat() },
                glassConfig = glassConfig.copy(tint = backdrop.adaptiveColor.copy(alpha = 0.10f)),
                state = hazeState,
                liveBackdrop = isPlaying && !uiState.prefs.reduceMotion
            )

            val audioSessionId by viewModel.audioSessionId.collectAsState()

            if (showWaveRecordSheet) {
                WaveRecordSheet(
                    song = song,
                    syncedLyric = syncedLyric,
                    plainLyric = plainLyric,
                    currentPositionValue = positionState.value,
                    currentPosition = { viewModel.getLivePosition() },
                    albumArt = albumArt,
                    accentColor = backdrop.adaptiveColor,
                    isPlaying = isPlaying,
                    audioSessionId = audioSessionId,
                    onDismiss = { showWaveRecordSheet = false }
                )
            }

            if (showLyricCardSheet) {
                val selectedText = selectedLyricLines.sorted()
                    .mapNotNull { syncedLyric?.getOrNull(it)?.text }
                    .joinToString("\n")

                LyricCardSheet(
                    song = song,
                    lyricText = selectedText,
                    albumArt = albumArt,
                    accentColor = backdrop.adaptiveColor,
                    onDismiss = {
                        showLyricCardSheet = false
                        selectedLyricLines = emptySet()
                    }
                )
            }
        }
    }
}

/**
 * Video-mode now-playing stage: title + artist, the rounded video surface, the
 * seek bar, and elapsed/total time. Replaces the art row + lyrics while a
 * streamed song from the "Videos" section plays its video.
 */
@Composable
private fun VideoStage(
    song: Song?,
    viewModel: MusicViewModel,
    positionState: State<Long>,
    durationMs: Long,
    accentColor: Color
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = song?.title ?: "No song playing",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val artist = song?.artist
            if (!artist.isNullOrBlank() && artist != "Unknown Artist") {
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
        ) {
            StreamVideoSurface(viewModel = viewModel, modifier = Modifier.fillMaxSize())
        }

        Spacer(modifier = Modifier.height(12.dp))

        PlayerSeekBar(
            positionState = positionState,
            durationMs = durationMs,
            accentColor = accentColor,
            onSeekFraction = { fraction -> viewModel.seekTo((fraction * durationMs).toLong()) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Elapsed on the left, elapsed/total stacked minutes-over-seconds (same
        // vertical style as Up-next rows) below the seek bar. Minutes can exceed
        // 59 for hour-plus videos (90:20 = 1h30m20s). Elapsed reads the position
        // State in its own composable so only the ticks recompose, not the whole
        // stage.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                ElapsedTimeStack(positionState = positionState, color = accentColor)
            }

            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                VerticalTimeStack(ms = durationMs, color = accentColor)
            }
        }
    }
}

@Composable
private fun ElapsedTimeStack(positionState: State<Long>, color: Color) {
    VerticalTimeStack(ms = positionState.value, color = color)
}

@Composable
private fun VerticalTimeStack(ms: Long, color: Color) {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = minutes.toString().padStart(2, '0'),
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 12.sp
        )
        Text(
            text = seconds.toString().padStart(2, '0'),
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 12.sp
        )
    }
}

/**
 * Hosts a [TextureView] wired to the session player's video output. A
 * TextureView (not SurfaceView) lets the UI read frames back, so we sample one
 * roughly every second to drive the backdrop's vibrance from the video itself.
 * Attached to the [MusicViewModel] on mount and detached on dispose so the
 * service ExoPlayer can render here while audio keeps flowing.
 */
@Composable
private fun StreamVideoSurface(
    viewModel: MusicViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val textureView = remember { TextureView(context) }

    AndroidView(
        modifier = modifier,
        factory = { textureView },
        update = { viewModel.attachVideoTexture(textureView) }
    )

    // Live ambient sampling: grab a tiny frame, pull its vibrant color, push it
    // to the view model. The sample is eased toward with an exponential moving
    // average so the backdrop (and the buttons derived from it) drift smoothly
    // instead of snapping every time the video's dominant hue jumps.
    LaunchedEffect(textureView) {
        var blended: Color? = null
        while (isActive) {
            delay(850)
            if (!textureView.isAvailable) continue
            val bmp = try {
                textureView.getBitmap(48, 27)
            } catch (_: Exception) {
                null
            } ?: continue
            val sampled = try {
                AlbumArtLoader.extractPaletteColors(bmp).firstOrNull()
                    ?: AlbumArtLoader.extractDominantColor(bmp)
            } finally {
                bmp.recycle()
            }
            val next = blended?.let { lerp(it, sampled, 0.45f) } ?: sampled
            blended = next
            viewModel.setVideoAmbientColor(next)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.attachVideoTexture(null)
            viewModel.setVideoAmbientColor(null)
        }
    }
}

@Composable
fun UpnextSongRow(
    upSong: Song,
    viewModel: MusicViewModel,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var art by remember(upSong.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(upSong.id) {
        viewModel.getCachedArt(upSong) { bitmap -> art = bitmap?.asImageBitmap() }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isDarkMode) Color.DarkGray else Color.LightGray),
            contentAlignment = Alignment.Center
        ) {
            if (art != null) {
                Image(bitmap = art!!, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.Gray)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                upSong.title,
                color = if (isDarkMode) Color.White else Color.Black,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                upSong.artist,
                color = if (isDarkMode) Color.Gray else Color.Gray,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        val mins = remember(upSong.duration) { TimeUnit.MILLISECONDS.toMinutes(upSong.duration).toString().padStart(2, '0') }
        val secs = remember(upSong.duration) { (TimeUnit.MILLISECONDS.toSeconds(upSong.duration) % 60).toString().padStart(2, '0') }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(mins, color = if (isDarkMode) Color.White else Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold, lineHeight = 12.sp)
            Text(secs, color = if (isDarkMode) Color.White else Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold, lineHeight = 12.sp)
        }
    }
}

@Composable
fun SyncedLyricView(
    lines: List<com.ianocent.musicplayer.data.LyricLine>,
    positionState: State<Long>,
    highlightColor: Color,
    selectedIndices: Set<Int>,
    onLineClick: (Int) -> Unit,
    reduceMotion: Boolean = false,
    motionScale: Float = 1f
) {
    // Only this view recomposes on the 100ms position tick.
    val currentPosition = positionState.value
    val activeIndex = remember(currentPosition, lines) {
        lines.indexOfLast { it.timeMs <= currentPosition }.coerceAtLeast(0)
    }
    // O(1) index lookup in the item lambda (was lines.indexOf(line) → O(n²) total).
    val indexByTime = remember(lines) {
        lines.withIndex().associate { (index, line) -> line.timeMs to index }
    }
    val listState = rememberLazyListState()

    LaunchedEffect(activeIndex) {
        if (selectedIndices.isEmpty()) {
            listState.animateScrollToItem(activeIndex)
        }
    }

    ResponsiveSnapList(
        items = lines,
        key = { it.timeMs },
        scrollbarColor = highlightColor,
        listState = listState,
        minItemHeight = 52.dp,
        bottomPadding = 8.dp
    ) { line, itemHeight ->
        val index = indexByTime[line.timeMs] ?: 0
        val isSelected = selectedIndices.contains(index)
        val isActive = index == activeIndex

        val activeColor by animateColorAsState(
            targetValue = if (isActive) highlightColor else Color.Gray,
            animationSpec = MotionTokens.scaledTween(MotionTokens.LYRIC_HIGHLIGHT, motionScale, reduceMotion),
            label = "lyrColor"
        )
        val activeSize by animateFloatAsState(
            targetValue = if (isActive) 16f else 14f,
            animationSpec = MotionTokens.scaledTween(MotionTokens.LYRIC_HIGHLIGHT, motionScale, reduceMotion),
            label = "lyrSize"
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .padding(horizontal = 12.dp, vertical = 2.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isSelected) highlightColor.copy(alpha = 0.2f) else Color.Transparent)
                .clickable { onLineClick(index) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                line.text,
                textAlign = TextAlign.Center,
                fontSize = activeSize.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                color = activeColor,
                maxLines = 2,
                lineHeight = 18.sp
            )
        }
    }
}

fun formatTime(ms: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return String.format("%02d:%02d", minutes, seconds)
}

/**
 * Timer overlay drawn inside the album art. Reads the position [State] here so
 * only this tiny composable recomposes on the 100ms tick, not the whole player.
 */
@Composable
private fun AlbumArtTimer(
    positionState: State<Long>,
    fontSize: Int = 26,
    singleLine: Boolean = false
) {
    val position = positionState.value
    if (singleLine) {
        Text(
            text = formatTime(position),
            color = Color.White,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    } else {
        Text(
            text = TimeUnit.MILLISECONDS.toMinutes(position).toString().padStart(2, '0'),
            color = Color.White,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = (TimeUnit.MILLISECONDS.toSeconds(position) % 60).toString().padStart(2, '0'),
            color = Color.White,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SkeletonLyricLoader(adaptiveColor: Color, reduceMotion: Boolean = false) {
    if (reduceMotion) {
        Box(Modifier.fillMaxSize().background(adaptiveColor.copy(alpha = 0.2f)))
        return
    }

    val shimmerColors = listOf(
        adaptiveColor.copy(alpha = 0.1f),
        adaptiveColor.copy(alpha = 0.3f),
        adaptiveColor.copy(alpha = 0.1f)
    )

    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = MotionTokens.SHIMMER, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(x = translateAnim, y = translateAnim)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val widths = listOf(0.6f, 0.8f, 0.9f, 0.7f, 0.5f)
        widths.forEach { fraction ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(14.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(brush)
            )
        }
    }
}