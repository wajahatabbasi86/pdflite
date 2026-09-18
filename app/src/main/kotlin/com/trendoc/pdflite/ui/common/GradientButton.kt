package com.trendoc.pdflite.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * The sticky bottom call-to-action every tool screen ends on (Merge, Split, and Compress
 * once it's built) — a full-width, two-tone gradient pill from the current accent color
 * into indigo, matching the reference the user picked over the flat single-color button.
 * Disabled state just drops to a flat, muted fill rather than a dimmed gradient, since a
 * gradient at low opacity tends to look broken rather than "disabled."
 */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val accent = MaterialTheme.colorScheme.primary
    val gradient = Brush.horizontalGradient(listOf(accent, Color(0xFF4C5FD5)))
    val disabledColor = MaterialTheme.colorScheme.surfaceVariant

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) gradient else Brush.horizontalGradient(listOf(disabledColor, disabledColor)))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val contentColor = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        icon?.let {
            Icon(it, contentDescription = null, tint = contentColor, modifier = Modifier.padding(end = 8.dp))
        }
        Text(text, color = contentColor, style = MaterialTheme.typography.titleSmall)
    }
}
