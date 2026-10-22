package com.ianocent.musicplayer.ui.canvas

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.view.TextureView
import android.view.ViewGroup.LayoutParams
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.maxDimension
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.ianocent.musicplayer.data.canvas.CanvasLog
import com.ianocent.musicplayer.data.canvas.CanvasMode
import com.ianocent.musicplayer.ui.motion.rememberPlaybackClock
import com.ianocent.musicplayer.data.canvas.providers.CanvasProviderRegistry
import com.ianocent.musicplayer.data.canvas.providers.GenerativeProvider
import com.ianocent.musicplayer.data.canvas.providers.KenBurnsProvider
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Builds a drawBehind modifier (chainable onto the NowPlaying content column)
 * that paints an ambient canvas over the gradient but under the UI.
 * Routes rendering through CanvasProviderRegistry: video providers fall back
 * to Modifier here; use PlayerCanvasVideoSurface for the media surface.
 *
 * A single [rememberPlaybackClock] drives the generative/Ken-Burns motion and is
 * read at DRAW time, so nothing in this subtree recomposes per frame and the
 * animation freezes (zero frame work) whenever playback is paused.
 */
@Composable
fun PlayerCanvasLayer(
    mode: CanvasMode,
    isPlaying: Boolean,
    ambientColor: Color,
    paletteColors: List<Color>,
    albumArt: Bitmap?,
    reduceMotion: Boolean = false,
    modifier: Modifier = Modifier
): Modifier {
    val provider = CanvasProviderRegistry.forMode(mode)
    val draw = if (provider.usesVideoSurface) Modifier else when (provider) {
        GenerativeProvider -> auroraCanvasModifier(
            isPlaying, ambientColor, paletteColors, reduceMotion
        )
        KenBurnsProvider -> kenBurnsCanvasModifier(isPlaying, albumArt, ambientColor, reduceMotion)
        else -> Modifier
    }
    return modifier.then(draw)
}

/**
 * Renders a looping, muted video behind the UI. Usable when video mode is on
 * and a video file exists for the song.
 *
 * Uses a TextureView inside AspectRatioFrameLayout(ZOOM) instead of PlayerView:
 * PlayerView defaults to a SurfaceView, which punches a separate window layer
 * that Compose modifiers (alpha / drawWithContent fades) cannot affect — that
 * was why the bottom-edge gradient never appeared. TextureView composites
 * normally into the Compose layer.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun PlayerCanvasVideoSurface(
    path: String,
    isPlaying: Boolean,
    tint: Color,
    darkScrimAlpha: Float = 0.35f,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ALL
            setHandleAudioBecomingNoisy(false)
        }
    }
    var videoAspect by remember { mutableStateOf(0f) }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                CanvasLog.d("VIDEO: state=$playbackState (2=buffered 3=ready 4=ended)")
            }

            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                CanvasLog.d("VIDEO: playing=$isPlayingNow")
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                videoAspect = if (videoSize.height > 0 && videoSize.width > 0) {
                    videoSize.width.toFloat() / videoSize.height.toFloat()
                } else 0f
                CanvasLog.d("VIDEO: size=${videoSize.width}x${videoSize.height} aspect=$videoAspect")
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                CanvasLog.e("VIDEO: playerError code=${error.errorCode} cause=${error.cause}", error)
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(path) {
        CanvasLog.d("VIDEO: setting media path=$path isPlaying=$isPlaying")
        player.stop()
        player.clearMediaItems()
        player.setMediaItem(MediaItem.fromUri(path))
        player.prepare()
        player.playWhenReady = isPlaying
    }

    LaunchedEffect(isPlaying) {
        player.playWhenReady = isPlaying
    }

    Box(modifier = modifier.background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val textureView = TextureView(ctx)
                androidx.media3.ui.AspectRatioFrameLayout(ctx).apply {
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    addView(textureView, LayoutParams(
                        LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT
                    ))
                    tag = textureView
                }
            },
            update = { container ->
                val tv = container.tag as TextureView
                player.setVideoTextureView(tv)
                (container as androidx.media3.ui.AspectRatioFrameLayout).setAspectRatio(
                    if (videoAspect > 0f) videoAspect else 0f
                )
            }
        )
        if (darkScrimAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = darkScrimAlpha))
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(tint.copy(alpha = 0.12f))
        )
    }
}

// ---------------------------------------------------------------------------
// GENERATIVE (aurora)
// ---------------------------------------------------------------------------

private data class Blob(
    val phase: Float,
    val speed: Float,
    val amp: Float,
    val size: Float,
    val baseAlpha: Float
)

private data class Dust(
    val seed: Float,
    val speed: Float,
    val radius: Float,
    val phase: Float
)

/** Triangle wave 0→1→0 over [2 * periodMs]. */
private fun triangle(ms: Long, periodMs: Long): Float {
    if (periodMs <= 0L) return 0f
    val p = (ms % (2L * periodMs)).toFloat() / periodMs.toFloat()
    return if (p <= 1f) p else 2f - p
}

@Composable
private fun auroraCanvasModifier(
    isPlaying: Boolean,
    ambientColor: Color,
    paletteColors: List<Color>,
    reduceMotion: Boolean,
): Modifier {
    val clock = rememberPlaybackClock(isPlaying, reduceMotion)

    val colors = remember(paletteColors, ambientColor) {
        listOf(
            paletteColors.getOrNull(0) ?: ambientColor,
            paletteColors.getOrNull(1) ?: ambientColor,
            paletteColors.getOrNull(2) ?: Color(0xFFE91E63),
            Color.White.copy(alpha = 0.9f)
        )
    }

    val blobs = remember {
        List(4) { i ->
            val r = Random(i + 100)
            Blob(
                phase = r.nextFloat() * (PI * 2).toFloat(),
                speed = 0.6f + r.nextFloat() * 0.9f,
                amp = 0.7f + r.nextFloat() * 0.5f,
                size = 0.6f + r.nextFloat() * 0.5f,
                baseAlpha = 0.16f + r.nextFloat() * 0.14f
            )
        }
    }
    val dust = remember {
        List(45) { i ->
            val r = Random(i + 400)
            Dust(
                seed = r.nextFloat() * (PI * 2).toFloat(),
                speed = 0.25f + r.nextFloat() * 0.75f,
                radius = 1f + r.nextFloat() * 2.2f,
                phase = r.nextFloat() * (PI * 2).toFloat()
            )
        }
    }

    // Read the clock inside drawBehind: the canvas redraws every frame while
    // playing but the composable itself never recomposes.
    return Modifier.drawBehind {
        val t = clock.value * 0.003333f
        val pulse = 0.5f + 0.5f * sin(t * 1.9635f) // ~1.6s period
        val sway = (0.35f + 0.5f * pulse).coerceIn(0f, 1f)

        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val dim = max(w, h)

        // soft central glow
        drawCircle(
            brush = Brush.radialGradient(
                listOf(colors[0].copy(alpha = 0.30f + 0.12f * sway), Color.Transparent),
                center = Offset(cx, cy),
                radius = dim * 0.5f
            ),
            radius = dim * 0.5f,
            center = Offset(cx, cy)
        )

        // drifting aurora blobs
        blobs.forEachIndexed { i, b ->
            val bx = cx + sin(t * b.speed + b.phase) * w * 0.42f * b.amp
            val by = cy + cos(t * b.speed * 0.7f + b.phase * 1.31f) * h * 0.34f * b.amp
            val br = dim * b.size * (0.55f + 0.10f * sway)
            val col = colors[(i + 1) % colors.size]
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(col.copy(alpha = b.baseAlpha * (0.5f + sway)), Color.Transparent),
                    center = Offset(bx, by),
                    radius = br
                ),
                radius = br,
                center = Offset(bx, by)
            )
        }

        // pulsing energy ring
        val ringR = dim * (0.22f + 0.06f * pulse)
        drawCircle(
            color = colors[1].copy(alpha = 0.10f + 0.10f * sway),
            radius = ringR,
            center = Offset(cx, cy),
            style = Stroke(width = 1.5f * density)
        )

        // slow rising dust (like light motes)
        dust.forEach { d ->
            val yf = ((t * d.speed * 0.12f + d.phase / (PI * 2).toFloat()) % 1f + 1f) % 1f
            val dx = cx + sin(t * 0.1f * d.seed + d.phase) * w * 0.36f
            val dy = h - yf * h * 1.05f
            val alpha = 0.05f + 0.09f * abs(sin(d.seed + t * 0.2f)) * (0.4f + sway)
            drawCircle(
                color = Color.White.copy(alpha = alpha.coerceIn(0.02f, 0.2f)),
                radius = d.radius * (1f + 0.3f * sway),
                center = Offset(dx, dy),
                alpha = alpha.coerceIn(0.02f, 0.2f)
            )
        }

        // vignette for readability
        drawRect(
            brush = Brush.radialGradient(
                listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.38f)),
                center = Offset(cx, cy),
                radius = dim * 0.75f
            )
        )
    }
}

// ---------------------------------------------------------------------------
// KEN BURNS (crop window pan/zoom - no distortion, no upscale artifacts)
// ---------------------------------------------------------------------------

@Composable
private fun kenBurnsCanvasModifier(
    isPlaying: Boolean,
    albumArt: Bitmap?,
    ambientColor: Color,
    reduceMotion: Boolean,
): Modifier {
    val clock = rememberPlaybackClock(isPlaying, reduceMotion)
    val art = albumArt
    val artImage = albumArt?.asImageBitmap()

    return Modifier.drawBehind {
        if (artImage != null && art != null) {
            val ms = clock.value
            val kbZoom = 1.0f + 0.22f * triangle(ms, 15000L)
            val kbPanX = 0.45f + 0.13f * triangle(ms, 20000L)
            val kbPanY = 0.40f + 0.15f * triangle(ms, 17000L)
            val kbDim = 0.18f + 0.12f * triangle(ms, 9000L)

            val srcW = art.width.toFloat()
            val srcH = art.height.toFloat()
            val dstW = size.width
            val dstH = size.height
            val srcAspect = srcW / srcH
            val dstAspect = dstW / dstH

            // full-cover base crop (no stretching)
            val baseCropW: Float
            val baseCropH: Float
            if (srcAspect > dstAspect) {
                baseCropH = srcH
                baseCropW = srcH * dstAspect
            } else {
                baseCropW = srcW
                baseCropH = srcW / dstAspect
            }

            // zoom shrinks the crop window
            val cropW = baseCropW / kbZoom
            val cropH = baseCropH / kbZoom
            val maxX = srcW - cropW
            val maxY = srcH - cropH
            val cropX = (kbPanX * maxX).coerceIn(0f, maxX)
            val cropY = (kbPanY * maxY).coerceIn(0f, maxY)

            drawImage(
                image = artImage,
                srcOffset = IntOffset(cropX.roundToInt(), cropY.roundToInt()),
                srcSize = IntSize(cropW.roundToInt(), cropH.roundToInt()),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(dstW.toInt(), dstH.toInt()),
                filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium
            )

            // readable tint + vignette
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = kbDim * 1.3f),
                        Color.Black.copy(alpha = kbDim * 0.4f),
                        Color.Black.copy(alpha = kbDim * 1.1f)
                    )
                )
            )
            drawRect(
                brush = Brush.radialGradient(
                    listOf(
                        ambientColor.copy(alpha = 0.10f),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.30f)
                    ),
                    center = Offset(dstW / 2f, dstH / 2f),
                    radius = size.maxDimension * 0.75f
                )
            )
        } else {
            // no art: ambient tinted radial fallback
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(ambientColor.copy(alpha = 0.55f), Color.Transparent)
                ),
                radius = size.maxDimension
            )
        }
    }
}
