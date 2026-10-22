package com.ianocent.musicplayer.ui.glass

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ianocent.musicplayer.ui.perf.VisualPerformanceTier
import com.ianocent.musicplayer.ui.perf.VisualTier
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect

/** Builds the runtime glass config, auto-disabling it on low-end devices. */
@Composable
fun rememberGlassConfig(enabled: Boolean, blurRadius: Float, vibrancy: Float): IanGlassConfig {
    val context = LocalContext.current
    val runtimeAllowed = remember {
        VisualTier.detect(context) != VisualPerformanceTier.LOW
    }
    return IanGlassConfig(
        enabled = enabled && runtimeAllowed,
        blurRadius = blurRadius,
        vibrancy = vibrancy
    )
}

/**
 * Glass system (Haze-backed). On API 32+ with a [HazeState] source provided
 * this renders a REAL backdrop blur ("frosted glass") — the pixels of whatever
 * is painted behind the surface, blurred and tinted. On older platforms or
 * when no source is supplied it degrades to a translucent vibrancy gradient
 * (Haze auto-scrims on platforms without blur support).
 *
 * The caller must:
 *  - create a [HazeState] (e.g. [rememberHazeState]);
 *  - put `Modifier.hazeSource(state)` on the *background* layer that lives
 *    BEHIND the glass panels (as a sibling — not an ancestor wrapping them,
 *    otherwise Haze's ancestor-zIndex rule excludes it);
 *  - pass that same state to every [IanGlassSurface].
 *
 * IMPORTANT: the decorative layers below use [Modifier.matchParentSize],
 * NOT [Modifier.fillMaxSize]. `fillMaxSize()` asks for as much space as the
 * incoming constraints allow *before* the Box's own size is resolved, which
 * — when this surface is used as a small "pill"/card wrapped around a short
 * Row of text (its normal use case in NowPlayingScreen) sitting inside a
 * loosely-constrained Column — makes the whole surface balloon to fill
 * whatever vertical space its ancestors have available, instead of hugging
 * `content`'s actual size. `matchParentSize()` waits for the Box's size to
 * be settled by its other content first, then matches it — the standard
 * Compose pattern for background/decoration layers that must never
 * influence their own parent's size.
 */
@Composable
fun IanGlassSurface(
    config: IanGlassConfig,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(config.cornerRadius.dp),
    state: HazeState? = null,
    fallbackColor: Color? = null,
    // Re-sample the blur every frame ONLY when the pixels behind the glass are
    // genuinely moving each frame (video/gen canvas, live album-art motion). For
    // static backdrops (e.g. the home gradient) leaving this false lets Haze reuse
    // its cached blur — a handful of always-invalidating surfaces is what made the
    // home screen feel heavy and the tab-switch animation stutter.
    liveBackdrop: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    // Infer the active theme (follows the app's own dark toggle, not just the OS).
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Box(modifier.clip(shape)) {
        if (config.enabled) {
            if (state != null && canBlur && HazeDefaults.blurEnabled()) {
                Box(
                    Modifier
                        .matchParentSize()
                        .hazeEffect(state = state) {
                            blurRadius = config.blurRadius.dp
                            noiseFactor = HazeDefaults.noiseFactor
                            backgroundColor = Color.Unspecified
                            tints = listOf(HazeDefaults.tint(config.tint))
                            fallbackTint = HazeDefaults.tint(config.tint.copy(alpha = 0.3f))
                            // Only force a re-sample every frame when the pixels
                            // behind the glass are actually moving (playback running
                            // and motion not reduced). When paused/idle the backdrop
                            // is static, so leaving this false lets Haze reuse its
                            // cached blur instead of re-blurring 60×/s for nothing.
                            forceInvalidateOnPreDraw = liveBackdrop
                        }
                )
                // Dark mode: a faint smoked scrim deepens the frosted slab so it
                // reads as glass over an OLED-black backdrop instead of a grey
                // fog. Light mode keeps the airy, translucent look.
                if (dark) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.10f + config.vibrancy * 0.12f))
                    )
                }
            } else {
                Box(Modifier.matchParentSize().background(glassVibrancyBrush(config, dark)))
                Box(Modifier.matchParentSize().background(config.tint))
            }
            Box(Modifier.matchParentSize().drawBehind { glassDecoration(config, dark) })
        } else if (fallbackColor != null) {
            // Glass off — mimic the layout's pre-glass base container so the
            // "Liquid Glass OFF" default looks exactly like it did before the
            // feature existed (e.g. the solid home list container).
            Box(Modifier.matchParentSize().background(fallbackColor))
        }
        content(this)
    }
}

@Composable
fun IanGlassButton(
    config: IanGlassConfig = IanGlassConfig(blurRadius = 14f, vibrancy = 0.25f, cornerRadius = 18f),
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Press feel: the slab settles (scale down) instead of the old flat alpha
    // dip — glass "pushes in" like a physical pane and springs back on lift.
    val pressProgress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "glassBtnPress"
    )
    val alpha = 1f - pressProgress * 0.12f
    val scale = 1f - pressProgress * 0.05f
    IanGlassSurface(
        config = config,
        modifier = modifier
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled
            ) { onClick() },
        shape = RoundedCornerShape(config.cornerRadius.dp)
    ) {
        content(this)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IanGlassSheet(
    config: IanGlassConfig,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        containerColor = Color.Transparent,
        dragHandle = null,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        IanGlassSurface(
            config = config.copy(cornerRadius = 28f),
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            content(this)
        }
    }
}

private fun glassVibrancyBrush(config: IanGlassConfig, dark: Boolean): Brush {
    val alpha = (0.10f + config.vibrancy * 0.25f).coerceIn(0f, 0.5f)
    return if (dark) {
        // Smoked glass: a soft top sheen over a deep base, so it reads as a dark
        // translucent slab on the OLED backdrop rather than a washed-out grey.
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = alpha * 0.42f),
                Color.Black.copy(alpha = 0.16f + config.vibrancy * 0.10f)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = alpha),
                Color.White.copy(alpha = alpha * 0.35f)
            )
        )
    }
}

private fun DrawScope.glassDecoration(config: IanGlassConfig, dark: Boolean) {
    val radius = config.cornerRadius.dp.toPx()

    // Small circular controls (buttons/pills) never get the top highlight bar —
    // a straight bright line across a circle reads as a defect, not an edge.
    // The mini player bar (single row of 44dp controls) is a medium-size case:
    // it gets the highlight, but inset horizontally past the rounded corners so
    // it doesn't smear over the left/right arcs.
    val isSmall = size.height <= 56.dp.toPx()
    if (!isSmall) {
        val height = 1.5.dp.toPx() * (0.6f + config.highlight)
        val insetX = (radius * 0.5f).coerceAtMost(size.width / 4f)
        // Dim the sheen a touch in dark mode so it stays a highlight, not a glare.
        val sheen = config.highlight.coerceIn(0f, 0.4f) * (if (dark) 0.85f else 1f)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = sheen),
                    Color.Transparent
                )
            ),
            topLeft = androidx.compose.ui.geometry.Offset(insetX, 0f),
            size = androidx.compose.ui.geometry.Size(size.width - insetX * 2f, height)
        )
    }

    // Border stroke always follows the shape the clip applies: pills get a
    // crisp inset ring, bigger panels a soft outer edge. The stroke is inset
    // by its own width so it never clips against the RoundedCornerShape — a
    // centered stroke on the exact edge bleeds half outside the clip and
    // renders as a ragged glow at the bottom corners of the mini bar.
    val strokeWidth = if (isSmall) 1.5.dp.toPx() else 1.dp.toPx()
    val inset = strokeWidth / 2f
    val drawRadius = (radius - inset).coerceAtLeast(0f)
    val borderAlpha = if (isSmall) {
        if (dark) 0.34f else 0.30f
    } else {
        (if (dark) 0.18f else 0.16f) + config.highlight * 0.4f
    }
    drawRoundRect(
        color = Color.White.copy(alpha = borderAlpha),
        topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
        size = androidx.compose.ui.geometry.Size(
            size.width - inset * 2f,
            size.height - inset * 2f
        ),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(drawRadius, drawRadius),
        style = Stroke(width = strokeWidth)
    )
}