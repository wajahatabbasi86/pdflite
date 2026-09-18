package com.trendoc.pdflite.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The small colored-circle file avatar reused on every file row (Merge, Split) — a simple
 * hand-drawn page glyph (three lines on a folded-corner rect) rather than a Material icon,
 * since the app only depends on material-icons-core's small curated set, not -extended.
 */
@Composable
fun FileIconAvatar(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.size(width = 14.dp, height = 16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp)
        ) {
            Box(Modifier.fillMaxWidth().size(2.dp).clip(RoundedCornerShape(1.dp)).background(tint))
            Box(Modifier.fillMaxWidth().size(2.dp).clip(RoundedCornerShape(1.dp)).background(tint))
            Box(Modifier.size(width = 9.dp, height = 2.dp).clip(RoundedCornerShape(1.dp)).background(tint))
        }
    }
}

/** A soft, tinted instructional banner — e.g. "Drag or tap arrows to reorder before merging." */
@Composable
fun InfoBanner(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A dashed-border "add more" affordance — reads as additive/optional, distinct from the
 * solid primary action at the bottom of the screen. */
@Composable
fun DashedAddButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp).padding(end = 6.dp)
            )
            Text(text, color = accent, style = MaterialTheme.typography.labelLarge)
        }
    }
}
