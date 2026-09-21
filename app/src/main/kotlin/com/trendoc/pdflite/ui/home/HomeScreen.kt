package com.trendoc.pdflite.ui.home

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trendoc.pdflite.appearance.BackgroundStyle
import com.trendoc.pdflite.appearance.CrystalSurface
import com.trendoc.pdflite.appearance.HomeLayout
import com.trendoc.pdflite.billing.EntitlementRepository
import com.trendoc.pdflite.ui.common.ToolAvatar
import com.trendoc.pdflite.ui.common.ToolGlyphType
import com.trendoc.pdflite.ui.settings.AppearanceViewModel

/** One entry in the Home screen's tool list (§2 / design system "Home layout" section). */
private data class ToolCard(
    val label: String,
    val description: String,
    val badge: String,
    val route: String,
    val tint: Color,
    val glyph: ToolGlyphType
)

/**
 * Home screen per docs/REQUIREMENTS.md §2. Tool arrangement is a user preference
 * ([HomeLayout], see AppearanceScreen) rather than one fixed layout:
 * - [HomeLayout.BENTO] (default): Merge full-width, Split/Compress medium, Image<->PDF
 *   small — tile size communicates priority.
 * - [HomeLayout.LIST]: flat rows with a tinted icon avatar, a short badge, and a chevron —
 *   the flat/utilitarian alternative from the design system's Home layout options.
 * Featured/Carousel beyond these two are offered in Appearance but not yet built here
 * (disabled there) — see AppearanceScreen.
 *
 * Every tool card below routes to its real feature screen (see TrenDocNavHost) — Merge,
 * Split, Compress, Image<->PDF, PDF->Image, View PDF, and Fill Forms are all built.
 */
private val bigTool = ToolCard(
    "Merge PDFs", "Combine multiple documents into one", "Multi-file", "merge", Color(0xFFC1442D), ToolGlyphType.MERGE
)
private val medTools = listOf(
    ToolCard("Split", "Extract specific pages or burst all", "Custom range", "split", Color(0xFF4C5FD5), ToolGlyphType.SPLIT),
    ToolCard("Compress", "Reduce file size without quality loss", "Up to −88%", "compress", Color(0xFF2F8F82), ToolGlyphType.COMPRESS),
)
private val smallTools = listOf(
    ToolCard("Image → PDF", "Convert photos & gallery scans", "Batch", "image_to_pdf", Color(0xFFC98A2E), ToolGlyphType.IMAGE_TO_PDF),
    ToolCard("PDF → Image", "Export pages as high-res PNG/JPG", "Export", "pdf_to_image", Color(0xFF7A4B8A), ToolGlyphType.PDF_TO_IMAGE),
    // Read-only, no SAF save dialog at the end — also the landing screen when TrenDoc is
    // opened via the system "Open with" chooser for a PDF (see PendingPdfIntent).
    ToolCard("View PDF", "Open and read any PDF, no editing", "Quick view", "view_pdf", Color(0xFF3B7FB5), ToolGlyphType.VIEW_PDF),
    // Structured AcroForm fields only (§10) — not freeform text editing anywhere on the page.
    ToolCard("Fill Forms", "Fill in existing PDF form fields", "No subscription", "fill_forms", Color(0xFF4A8B5C), ToolGlyphType.FILL_FORM),
)
private val allTools = listOf(bigTool) + medTools + smallTools

/** "2h 14m" / "38m" for the top bar's "Ad-free (...)" label. */
private fun formatRemaining(millis: Long): String {
    val totalMinutes = millis / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onToolSelected: (String) -> Unit, onOpenAppearance: () -> Unit, onOpenBilling: () -> Unit) {
    val appearanceViewModel: AppearanceViewModel = viewModel()
    val prefs by appearanceViewModel.preferences.collectAsState()
    val darkGround = prefs.background == BackgroundStyle.CRYSTAL_INK

    val context = LocalContext.current
    val entitlementRepository = remember { EntitlementRepository(context) }
    val remainingMillis by entitlementRepository.remainingMillis.collectAsState(initial = 0L)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TrenDoc") },
                actions = {
                    // "Remove Ads" stays a persistent, non-modal top-bar action — Appearance is
                    // a separate entry point, not a replacement for it. Always tappable, even
                    // during an active ad-free window, so the user can check the remaining time
                    // or extend it. The banner itself is global now (see TrenDocNavHost), not
                    // owned by this screen.
                    TextButton(onClick = onOpenBilling) {
                        Text(if (remainingMillis > 0) "Ad-free (${formatRemaining(remainingMillis)})" else "Remove Ads")
                    }
                    IconButton(onClick = onOpenAppearance) {
                        Icon(Icons.Filled.Settings, contentDescription = "Appearance")
                    }
                }
            )
        }
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
        OfflineBadgeStrip(darkGround)
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
        SectionHeader("Document Utilities", darkGround)
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

/** "100% Offline & Private" pill plus the three-point trust strip under the app name,
 * matching the design reference's header treatment. Purely presentational — no new
 * capability, just surfacing what the app already does (no cloud upload, ever). */
@Composable
private fun OfflineBadgeStrip(darkGround: Boolean) {
    val contentColor = if (darkGround) Color(0xFFD7D3C8) else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf("No Cloud Upload", "Zero Watermark", "No Interstitial Ads").forEach { label ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(contentColor.copy(alpha = 0.10f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = contentColor)
            }
        }
    }
}

/** A small section label with a trailing "Local Execution" pill, echoing the header's
 * offline-first framing right above the tool grid it introduces. */
@Composable
private fun SectionHeader(title: String, darkGround: Boolean) {
    val titleColor = if (darkGround) Color(0xFFF4F2EC) else MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = titleColor)
        Text("Local Execution", style = MaterialTheme.typography.labelSmall, color = accent)
    }
}

@Composable
private fun HomeList(onToolSelected: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OfflineBadgeStrip(darkGround = false)
        SectionHeader("Document Utilities", darkGround = false)
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
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                ToolAvatar(
                    type = tool.glyph,
                    tint = if (onCrystal) contentColor else tool.tint,
                    size = 28.dp,
                    shape = RoundedCornerShape(9.dp)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(tool.tint.copy(alpha = if (onCrystal) 0.35f else 0.16f))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        tool.badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (onCrystal) contentColor else tool.tint
                    )
                }
            }
            Text(
                tool.label,
                style = MaterialTheme.typography.titleSmall,
                color = contentColor,
                modifier = Modifier.padding(top = 6.dp)
            )
            if (!compact) {
                Text(
                    tool.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = descColor,
                    modifier = Modifier.padding(top = 2.dp)
                )
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
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ToolAvatar(type = tool.glyph, tint = tool.tint, size = 44.dp, shape = RoundedCornerShape(12.dp))
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
