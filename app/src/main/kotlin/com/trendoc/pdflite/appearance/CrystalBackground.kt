package com.trendoc.pdflite.appearance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate

/**
 * The "crystal" background from the design system's Background section: a soft blurred
 * gradient (not hard-edged facets — those read as low-poly wallpaper and got busy behind
 * text) plus one diagonal sheen line standing in for light catching a glass edge.
 *
 * [BackgroundStyle.PLAIN] draws nothing — the screen's own flat background shows through.
 */
@Composable
fun CrystalSurface(
    style: BackgroundStyle,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier) {
        if (style != BackgroundStyle.PLAIN) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawCrystal(style, size.width, size.height)
            }
        }
        content()
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCrystal(
    style: BackgroundStyle,
    width: Float,
    height: Float
) {
    val ink = if (style == BackgroundStyle.CRYSTAL_INK) {
        listOf(Color(0xFF7B818F), Color(0xFF5C6270), Color(0xFF3D424B))
    } else {
        listOf(Color(0xFFF7F5EF), Color(0xFFEFECE4), Color(0xFFE6E2D6))
    }
    val paperHighlight = if (style == BackgroundStyle.CRYSTAL_INK) {
        Color(0xFFEFECE4).copy(alpha = 0.16f)
    } else {
        Color.White.copy(alpha = 0.55f)
    }
    val accentWash = Color(0xFFC1442D).copy(alpha = if (style == BackgroundStyle.CRYSTAL_INK) 0.12f else 0.08f)
    val baseWash = if (style == BackgroundStyle.CRYSTAL_INK) {
        Color(0xFF10151C).copy(alpha = 0.42f)
    } else {
        Color(0xFF666B78).copy(alpha = 0.14f)
    }
    val sheenAlpha = if (style == BackgroundStyle.CRYSTAL_INK) 0.14f else 0.42f
    // Highlight/accent radii are capped by the *shorter* side (typically width, on a phone):
    // a phone screen is much taller than it is wide, so a radius sized off width alone (as a
    // large multiple) still reads as "localized" horizontally but washes out everything near
    // the top third vertically — exactly where content (an app bar, the first row of tiles)
    // actually lives. Keeping these below ~0.7x the short side keeps them a corner highlight
    // rather than a wash.
    val shortSide = minOf(width, height)

    // Base diagonal gradient — the calm ground everything else sits on.
    drawRect(brush = Brush.linearGradient(colors = ink))

    // Soft highlight, top-left, where an app bar's title/actions typically sit.
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(paperHighlight, Color.Transparent),
            center = Offset(width * 0.08f, -height * 0.04f),
            radius = shortSide * 0.7f
        )
    )
    // A hint of the accent, top-right.
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(accentWash, Color.Transparent),
            center = Offset(width * 1.02f, -height * 0.02f),
            radius = shortSide * 0.55f
        )
    )
    // Deeper wash pooling toward the bottom.
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(baseWash, Color.Transparent),
            center = Offset(width * 0.5f, height * 1.05f),
            radius = shortSide * 0.9f
        )
    )

    // One diagonal sheen line, echoing light catching a cut edge.
    rotate(degrees = -24f, pivot = Offset(width * 0.5f, height * 0.5f)) {
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = sheenAlpha),
                    Color.Transparent
                ),
                start = Offset(width * 0.42f, 0f),
                end = Offset(width * 0.58f, 0f)
            ),
            topLeft = Offset(-width * 0.3f, -height * 0.3f),
            size = androidx.compose.ui.geometry.Size(width * 1.6f, height * 1.6f)
        )
    }
}
