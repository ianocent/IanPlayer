package com.ianocent.musicplayer.ui.glass

import androidx.annotation.DrawableRes
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp

/**
 * Cached lookup for the Noun Project icon set (res/drawable/noun_*).
 * Kept as one function so the whole app resolves the same icons the same way
 * (theme-agnostic black fill — Icon's tint does the actual coloring).
 */
@Composable
fun nounIcon(@DrawableRes id: Int): ImageVector = ImageVector.vectorResource(id)

/** Follows the app's own dark toggle (same inference as IanGlassSurface). */
@Composable
fun isAppInDark(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/**
 * Glassy icon backdrop for the circular icon buttons across the app.
 *
 * Works standalone (glass OFF) and layered over [IanGlassSurface] (glass ON):
 * white top sheen falling into a deep base, an accent wash driven by the
 * album-art palette, a hairline ring, and an active glow ring. Theme-aware —
 * dark mode gets a smoked slab, light mode an airy frosted one.
 *
 * [accent]/[active] wire the circular buttons to the animated ambient colors:
 * the wash + glow follow album art/video palette transitions automatically.
 */
fun Modifier.glassIconBackdrop(
    dark: Boolean,
    accent: Color = Color.Unspecified,
    active: Boolean = false,
    shape: Shape = CircleShape
): Modifier = this.drawBehind {
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path()
    path.addOutline(outline)

    // 1) Base slab: smooth translucent top gradient
    val base = Brush.verticalGradient(
        if (dark) {
            listOf(Color.White.copy(alpha = 0.22f), Color.Black.copy(alpha = 0.45f))
        } else {
            listOf(Color.White.copy(alpha = 0.88f), Color.White.copy(alpha = 0.45f))
        }
    )
    clipPath(path) { drawRect(brush = base) }

    // 2) Accent wash driven by album art / ambient theme
    if (accent != Color.Unspecified) {
        val wash = if (active) 0.36f else 0.14f
        clipPath(path) {
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(
                        accent.copy(alpha = wash),
                        accent.copy(alpha = wash * 0.25f)
                    )
                )
            )
        }
    }

    // 3) Smooth radial top sheen following rounded contour (no flat straight cutoffs)
    val topSheen = Brush.radialGradient(
        colors = listOf(
            Color.White.copy(alpha = if (dark) 0.35f else 0.55f),
            Color.Transparent
        ),
        center = Offset(size.width * 0.5f, size.height * 0.12f),
        radius = size.width * 0.45f
    )
    clipPath(path) { drawRect(brush = topSheen) }

    // 4) Glass hairline rim: top-weighted gradient stroke following exact curvature
    val rimBrush = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = if (dark) 0.70f else 0.88f),
            Color.White.copy(alpha = if (dark) 0.25f else 0.40f),
            Color.White.copy(alpha = if (dark) 0.10f else 0.20f)
        )
    )
    drawPath(
        path = path,
        brush = rimBrush,
        style = Stroke(width = 1.2.dp.toPx())
    )

    // 5) Active glow ring when toggled ON
    if (active && accent != Color.Unspecified) {
        drawPath(path, accent.copy(alpha = 0.30f), style = Stroke(width = 3.6.dp.toPx()))
        drawPath(path, accent.copy(alpha = 0.90f), style = Stroke(width = 1.8.dp.toPx()))
    }
}
