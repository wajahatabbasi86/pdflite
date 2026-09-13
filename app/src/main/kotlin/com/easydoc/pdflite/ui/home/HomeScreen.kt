package com.easydoc.pdflite.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.easydoc.pdflite.appearance.BackgroundStyle
import com.easydoc.pdflite.appearance.CrystalSurface
import com.easydoc.pdflite.appearance.HomeLayout
import com.easydoc.pdflite.ui.settings.AppearanceViewModel

/** One entry in the Home screen's tool list (§2 / design system "Home layout" section). */
private data class ToolCard(
    val label: String,
    val description: String,
    val badge: String,
    val route: String,
    val tint: Color
)

/**
 * Home screen per docs/REQUIREMENTS.md §2. Tool arrangement is a user preference
 * ([HomeLayout], see AppearanceScreen) rather than one fixed layout:
 * - [HomeLayout.BENTO] (default): Merge full-width, Split/Compress medium, Image<->PDF
 *   small — tile size communicates priority.
 * - [HomeLayout.LIST]: flat rows with a tinted icon avatar, a short badge, and a chevron —
 *   the flat/utilitarian alternative from the design system's Home layout options.
 * List/Featured/Carousel beyond these two are offered in Appearance but not yet built here
 * (disabled there) — see AppearanceScreen.
 *
 * Compress/Convert routes are still placeholders until their respective build steps (5-7)
 * land; tapping one of those just navigates to the picker demo screen for now. Merge
 * (step 3) and Split (step 4) route to the real features.
 */
private val bigTool = ToolCard(
    "Merge PDFs", "Combine multiple documents into one", "Multi-file", "merge", Color(0xFFC1442D)
)
private val medTools = listOf(
    ToolCard("Split", "Extract specific pages or burst all", "Custom range", "split", Color(0xFF4C5FD5)),
    ToolCard("Compress", "Reduce file size without quality loss", "Up to −88%", "picker", Color(0xFF2F8F82)),
)
private val smallTools = listOf(
    ToolCard("Image → PDF", "Convert photos & gallery scans", "Batch", "picker", Color(0xFFC98A2E)),
    ToolCard("PDF → Image", "Export pages as high-res PNG/JPG", "Export", "picker", Color(0xFF7A4B8A)),
)
private val allTools = listOf(bigTool) + medTools + smallTools

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onToolSelected: (String) -> Unit, onOpenAppearance: () -> Unit) {
    val appearanceViewModel: AppearanceViewModel = viewModel()
    val prefs by appearanceViewModel.preferences.collectAsState()
    val darkGround = prefs.background == BackgroundStyle.CRYSTAL_INK

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("EasyDoc") },
                actions = {
                    // §7: "Remove Ads" stays a persistent, non-modal top-bar action —
                    // Appearance is a separate entry point, not a replacement for it.
                    TextButton(onClick = { /* Billing screen — build step 8 */ }) {
                        Text("Remove Ads")
                    }
                    IconButton(onClick = onOpenAppearance) {
                        Icon(Icons.Filled.Settings, contentDescription = "Appearance")
                    }
                }
            )
        }
        // AdMob banner (§1.5, §7) is added in build step 8 — Home screen only, not on
        // tool/processing screens.
    ) { innerPadding ->
        CrystalSurface(
            style = prefs.background,
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            when (prefs.homeLayout) {
                HomeLayout.BENTO -> HomeBento(onToolSelected, darkGround)
                // FEATURED and CAROUSEL aren't built yet (see AppearanceScreen — they're
                // disabled there); List is the fallback for both, same as the stored
                // default, so an unbuilt selection never renders a blank screen.
                else -> HomeList(onToolSelected)
            }
        }
    }
}

@Composable
private fun HomeBento(onToolSelected: (String) -> Unit, darkGround: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BentoTile(
            tool = bigTool,
            onClick = { onToolSelected(bigTool.route) },
            onCrystal = darkGround,
            modifier = Modifier.fillMaxWidth().height(96.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            medTools.forEach { tool ->
                BentoTile(
                    tool = tool,
                    onClick = { onToolSelected(tool.route) },
                    onCrystal = darkGround,
                    modifier = Modifier.weight(1f).height(84.dp)
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            smallTools.forEach { tool ->
                BentoTile(
                    tool = tool,
                    onClick = { onToolSelected(tool.route) },
                    onCrystal = darkGround,
                    modifier = Modifier.weight(1f).height(64.dp),
                    compact = true
                )
            }
        }
    }
}

@Composable
private fun HomeList(onToolSelected: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        allTools.forEach { tool ->
            ListRow(tool = tool, onClick = { onToolSelected(tool.route) })
        }
    }
}

@Composable
private fun BentoTile(
    tool: ToolCard,
    onClick: () -> Unit,
    onCrystal: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val contentColor = if (onCrystal) Color(0xFFF4F2EC) else MaterialTheme.colorScheme.onSurface
    val descColor = if (onCrystal) Color(0xFFD7D3C8) else MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = tool.tint.copy(alpha = if (onCrystal) 0.30f else 0.14f)),
        border = BorderStroke(1.dp, tool.tint.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(tool.label, style = MaterialTheme.typography.titleSmall, color = contentColor)
            if (!compact) {
                Text(tool.description, style = MaterialTheme.typography.bodySmall, color = descColor)
            }
        }
    }
}

/**
 * The flat/utilitarian List layout — a tinted circular icon avatar, title + a small colored
 * badge chip (e.g. "Multi-file", "Up to −88%"), a description line, and a trailing chevron,
 * on a plain, elevated white card. Each tool keeps its own identity tint for both its avatar
 * and its badge (not the single accent color) — Merge reads red, Split reads indigo, and so
 * on, the same variety as Bento's tiles.
 */
@Composable
private fun ListRow(tool: ToolCard, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(50))
                    .background(tool.tint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(4.dp)).background(tool.tint))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tool.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(tool.tint.copy(alpha = 0.14f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(tool.badge, style = MaterialTheme.typography.labelSmall, color = tool.tint)
                    }
                }
                Text(
                    tool.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
