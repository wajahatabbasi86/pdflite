package com.trendoc.pdflite.ui.home

import androidx.compose.ui.res.pluralStringResource
import androidx.annotation.StringRes
import com.trendoc.pdflite.R
import androidx.compose.ui.res.stringResource
import com.trendoc.pdflite.di.appContainer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CloudOff
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
import com.trendoc.pdflite.ui.common.GradientButton
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.ui.graphics.vector.ImageVector
import com.trendoc.pdflite.ui.common.ToolAvatar
import com.trendoc.pdflite.ui.settings.AppearanceViewModel

/** One entry in the Home screen's tool list (§2 / design system "Home layout" section).
 * [linkText] is the short colored CTA line at the bottom of a Document Utilities card
 * (e.g. "Organize", "Select Pages"), matching the design reference's card footer. */
private data class ToolCard(
    @StringRes val label: Int,
    @StringRes val description: Int,
    @StringRes val badge: Int,
    @StringRes val linkText: Int,
    val route: String,
    val tint: Color,
    val glyph: ImageVector
)

/**
 * Home screen per docs/REQUIREMENTS.md §2. Tool arrangement is a user preference
 * ([HomeLayout], see AppearanceScreen) rather than one fixed layout:
 * - [HomeLayout.BENTO] (default): View PDF gets the one hero card (the app's core
 *   feature); every other tool is a uniform white "Document Utilities" card in a 2-column
 *   grid below it — icon square + badge + title + description + a colored link line,
 *   rather than each tool having its own tinted card background.
 * - [HomeLayout.LIST]: flat rows with a tinted icon avatar, a short badge, and a chevron —
 *   the flat/utilitarian alternative from the design system's Home layout options.
 * - [HomeLayout.FEATURED]: each tool keeps its own full-width tinted card (its own [ToolCard.tint]
 *   as the card background, not a uniform white surface) — the "bold, colorful" alternative.
 * - [HomeLayout.CAROUSEL]: the hero card, then the rest of the tools in a horizontally
 *   swipeable row instead of a fixed grid — for a one-handed, thumb-swipe browsing feel.
 *
 * Every tool card below routes to its real feature screen (see TrenDocNavHost) — Merge,
 * Split, Compress, Image<->PDF, PDF->Image, View PDF, and Fill Forms are all built.
 */
// View PDF is the core feature (read/zoom/pan/swipe/presentation) — it gets the hero card.
private val bigTool = ToolCard(
    R.string.tool_view_pdf_label, R.string.tool_view_pdf_desc, R.string.tool_view_pdf_badge, R.string.tool_view_pdf_link,
    "view_pdf", Color(0xFF3B7FB5), Icons.Filled.Visibility
)
private val documentTools = listOf(
    ToolCard(
    R.string.tool_merge_label, R.string.tool_merge_desc, R.string.tool_merge_badge, R.string.tool_merge_link,
    "merge", Color(0xFFB7091B), Icons.Filled.SwapVert),
    ToolCard(
    R.string.tool_split_label, R.string.tool_split_desc, R.string.tool_split_badge, R.string.tool_split_link,
    "split", Color(0xFF4C5FD5), Icons.AutoMirrored.Filled.CallSplit),
    ToolCard(
    R.string.tool_compress_label, R.string.tool_compress_desc, R.string.tool_compress_badge, R.string.tool_compress_link,
    "compress", Color(0xFF2F8F82), Icons.Filled.Compress),
    ToolCard(
    R.string.tool_image_to_pdf_label, R.string.tool_image_to_pdf_desc, R.string.tool_image_to_pdf_badge, R.string.tool_image_to_pdf_link,
    "image_to_pdf", Color(0xFFC98A2E), Icons.Filled.PermMedia),
    ToolCard(
    R.string.tool_pdf_to_image_label, R.string.tool_pdf_to_image_desc, R.string.tool_pdf_to_image_badge, R.string.tool_pdf_to_image_link,
    "pdf_to_image", Color(0xFF7A4B8A), Icons.Filled.Image),
    // Structured AcroForm fields only (§10) — not freeform text editing anywhere on the page.
    ToolCard(
    R.string.tool_fill_forms_label, R.string.tool_fill_forms_desc, R.string.tool_fill_forms_badge, R.string.tool_fill_forms_link,
    "fill_forms", Color(0xFF4A8B5C), Icons.Filled.EditNote),
    // Works where Fill Forms cannot: a scanned claim or application form carries no AcroForm
    // fields at all, so there is nothing to fill — this types straight onto the page instead.
    ToolCard(
    R.string.tool_add_text_label, R.string.tool_add_text_desc, R.string.tool_add_text_badge, R.string.tool_add_text_link,
    "add_text", Color(0xFFB5643B), Icons.Filled.TextFields),
)
private val allTools = listOf(bigTool) + documentTools

/** "2h 14m" / "38m" for the top bar's "Ad-free (...)" label. */
@Composable
private fun formatRemaining(millis: Long): String {
    val totalMinutes = millis / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) stringResource(R.string.duration_hours_minutes, hours, minutes)
    else stringResource(R.string.duration_minutes, minutes)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onToolSelected: (String) -> Unit,
    onOpenBilling: () -> Unit,
    onOpenRecents: () -> Unit,
    onOpenFile: (android.net.Uri) -> Unit
) {
    val appearanceViewModel: AppearanceViewModel = viewModel()
    val prefs by appearanceViewModel.preferences.collectAsState()
    val darkGround = prefs.background == BackgroundStyle.CRYSTAL_INK

    val context = LocalContext.current
    val entitlementRepository = context.appContainer.entitlements
    val remainingMillis by entitlementRepository.remainingMillis.collectAsState(initial = 0L)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            stringResource(R.string.home_byline),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    // "Remove Ads" stays a persistent, non-modal top-bar action. Always tappable,
                    // even during an active ad-free window, so the user can check the remaining
                    // time or extend it. The banner itself is global now (see TrenDocNavHost),
                    // not owned by this screen. Appearance is reached via the bottom nav's
                    // "Settings" tab now, not a separate top-bar icon.
                    TextButton(onClick = onOpenBilling) {
                        Text(if (remainingMillis > 0) stringResource(R.string.home_ad_free_remaining, formatRemaining(remainingMillis)) else stringResource(R.string.common_remove_ads))
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
                HomeLayout.BENTO -> HomeBento(onToolSelected, darkGround, onOpenRecents, onOpenFile)
                HomeLayout.LIST -> HomeList(onToolSelected)
                HomeLayout.FEATURED -> HomeFeatured(onToolSelected, onOpenRecents, onOpenFile)
                HomeLayout.CAROUSEL -> HomeCarousel(onToolSelected, darkGround, onOpenRecents, onOpenFile)
            }
        }
    }
}

@Composable
private fun HomeBento(
    onToolSelected: (String) -> Unit,
    darkGround: Boolean,
    onOpenRecents: () -> Unit,
    onOpenFile: (android.net.Uri) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OfflineBadgeStrip(darkGround)
        HeroToolCard(
            tool = bigTool,
            onClick = { onToolSelected(bigTool.route) },
            onCrystal = darkGround
        )
        SectionHeader(stringResource(R.string.home_document_utilities), darkGround)
        documentTools.chunked(2).forEach { rowTools ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                rowTools.forEach { tool ->
                    DocumentUtilityCard(
                        tool = tool,
                        onClick = { onToolSelected(tool.route) },
                        onCrystal = darkGround,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowTools.size == 1) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
        RecentsPreviewStrip(darkGround = darkGround, onOpenRecents = onOpenRecents, onOpenFile = onOpenFile)
    }
}

/** "On-Device Recents" preview — the 3 most recent files this app has opened or produced,
 * with a "View All" link to the Recents tab. Reuses [com.trendoc.pdflite.recents.RecentsRepository]
 * directly (no separate ViewModel) since Home only needs a read-only, capped-length peek. */
@Composable
private fun RecentsPreviewStrip(
    darkGround: Boolean,
    onOpenRecents: () -> Unit,
    onOpenFile: (android.net.Uri) -> Unit
) {
    val context = LocalContext.current
    val recentsRepository = context.appContainer.recents
    val recents by recentsRepository.recents.collectAsState(initial = emptyList())
    if (recents.isEmpty()) return

    val titleColor = if (darkGround) Color(0xFFF4F2EC) else MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary

    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.home_on_device_recents), style = MaterialTheme.typography.titleSmall, color = titleColor, modifier = Modifier.semantics { heading() })
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 7.dp, vertical = 1.dp)
                ) {
                    Text("${recents.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    // 48dp tall target; the text alone made it 45dp.
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClick = onOpenRecents)
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.home_view_all), style = MaterialTheme.typography.labelMedium, color = accent)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
            }
        }
        Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            recents.take(3).forEach { entry ->
                Card(
                    onClick = { onOpenFile(entry.uri) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = if (darkGround) Color(0xFF23262E) else MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, Color(0x14191C1E))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        com.trendoc.pdflite.ui.common.FileIconAvatar()
                        Column(modifier = Modifier.weight(1f)) {
                            Text(entry.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            val size = if (entry.sizeBytes >= 0) formatBytes(entry.sizeBytes) else null
                            val pages = if (entry.pageCount > 0) {
                                pluralStringResource(R.plurals.page_count, entry.pageCount, entry.pageCount)
                            } else null
                            val local = stringResource(R.string.home_recent_local)
                            Text(
                                listOfNotNull(size, pages, local).joinToString(" • "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Localised "1.2 MB" / "340 kB" — the platform formatter picks units and decimal marks. */
@Composable
private fun formatBytes(bytes: Long): String =
    android.text.format.Formatter.formatShortFileSize(LocalContext.current, bytes)

/** The single hero card — View PDF, the app's core feature. White/surface card with an
 * icon-square + badge header, title, description, and a full-width primary button,
 * matching the design reference's hero-card treatment. */
@Composable
private fun HeroToolCard(tool: ToolCard, onClick: () -> Unit, onCrystal: Boolean) {
    val contentColor = if (onCrystal) Color(0xFFF4F2EC) else MaterialTheme.colorScheme.onSurface
    val descColor = if (onCrystal) Color(0xFFD7D3C8) else MaterialTheme.colorScheme.onSurfaceVariant
    val cardColor = if (onCrystal) Color(0xFF23262E) else MaterialTheme.colorScheme.surface
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = BorderStroke(1.dp, Color(0x14191C1E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ToolAvatar(icon = tool.glyph, tint = tool.tint, size = 40.dp, shape = RoundedCornerShape(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(tool.label), style = MaterialTheme.typography.titleMedium, color = contentColor)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(tool.tint.copy(alpha = 0.16f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(stringResource(tool.badge), style = MaterialTheme.typography.labelSmall, color = tool.tint)
                        }
                    }
                    Text(stringResource(tool.description), style = MaterialTheme.typography.bodySmall, color = descColor)
                }
            }
            GradientButton(
                text = stringResource(tool.linkText),
                onClick = onClick,
                modifier = Modifier.padding(top = 14.dp)
            )
        }
    }
}

/** "100% Offline & Private" pill plus the three-point trust strip under the app name,
 * matching the design reference's header treatment. Purely presentational — no new
 * capability, just surfacing what the app already does (no cloud upload, ever). */
@Composable
private fun OfflineBadgeStrip(darkGround: Boolean) {
    val contentColor = if (darkGround) Color(0xFFD7D3C8) else MaterialTheme.colorScheme.onSurfaceVariant
    val badges = listOf(
        Icons.Filled.CloudOff to stringResource(R.string.home_badge_no_cloud_upload),
        Icons.Filled.Bookmark to stringResource(R.string.home_badge_zero_watermark),
        Icons.Filled.Block to stringResource(R.string.home_badge_no_interstitial_ads)
    )
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        badges.forEach { (icon, label) ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(contentColor.copy(alpha = 0.10f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(12.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = contentColor, maxLines = 1)
            }
        }
    }
}

/** A small section label with a trailing "Local Execution" pill, echoing the header's
 * offline-first framing right above the tool grid it introduces. */
@Composable
private fun SectionHeader(title: String, darkGround: Boolean, modifier: Modifier = Modifier) {
    val titleColor = if (darkGround) Color(0xFFF4F2EC) else MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = titleColor, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.home_local_execution), style = MaterialTheme.typography.labelSmall, color = accent)
    }
}

@Composable
private fun HomeList(onToolSelected: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OfflineBadgeStrip(darkGround = false)
        SectionHeader(stringResource(R.string.home_document_utilities), darkGround = false)
        allTools.forEach { tool ->
            ListRow(tool = tool, onClick = { onToolSelected(tool.route) })
        }
    }
}

/** The "bold, colorful" layout — each tool keeps its own full-width card tinted in its
 * identity color (design system's per-tool tint), rather than Bento's uniform white
 * utility cards. View PDF still gets top billing as the hero. */
@Composable
private fun HomeFeatured(
    onToolSelected: (String) -> Unit,
    onOpenRecents: () -> Unit,
    onOpenFile: (android.net.Uri) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OfflineBadgeStrip(darkGround = false)
        HeroToolCard(tool = bigTool, onClick = { onToolSelected(bigTool.route) }, onCrystal = false)
        SectionHeader(stringResource(R.string.home_document_utilities), darkGround = false)
        documentTools.forEach { tool -> FeaturedToolCard(tool = tool, onClick = { onToolSelected(tool.route) }) }
        RecentsPreviewStrip(darkGround = false, onOpenRecents = onOpenRecents, onOpenFile = onOpenFile)
    }
}

/** A full-width card in the tool's own identity color — [ToolCard.tint] as the surface,
 * white text/icon on top, instead of a colored accent line on a white card. */
@Composable
private fun FeaturedToolCard(tool: ToolCard, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = tool.tint)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ToolAvatar(icon = tool.glyph, tint = Color.White.copy(alpha = 0.24f), size = 44.dp, shape = RoundedCornerShape(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(tool.label), style = MaterialTheme.typography.titleSmall, color = Color.White)
                Text(
                    stringResource(tool.description),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.White)
        }
    }
}

/** The hero card up top, then every other tool in a horizontally swipeable [LazyRow] instead
 * of a fixed grid — a one-handed, thumb-swipe way to browse tools. */
@Composable
private fun HomeCarousel(
    onToolSelected: (String) -> Unit,
    darkGround: Boolean,
    onOpenRecents: () -> Unit,
    onOpenFile: (android.net.Uri) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            OfflineBadgeStrip(darkGround)
            Box(modifier = Modifier.padding(top = 12.dp)) {
                HeroToolCard(tool = bigTool, onClick = { onToolSelected(bigTool.route) }, onCrystal = darkGround)
            }
        }
        SectionHeader(stringResource(R.string.home_document_utilities), darkGround, modifier = Modifier.padding(horizontal = 16.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)
        ) {
            items(documentTools) { tool ->
                DocumentUtilityCard(
                    tool = tool,
                    onClick = { onToolSelected(tool.route) },
                    onCrystal = darkGround,
                    modifier = Modifier.width(180.dp)
                )
            }
        }
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            RecentsPreviewStrip(darkGround = darkGround, onOpenRecents = onOpenRecents, onOpenFile = onOpenFile)
        }
    }
}

/** A uniform white "Document Utilities" card — small colored icon square, a badge chip,
 * title, description, and a colored link line at the bottom (e.g. "Organize"), matching
 * the design reference's card footer instead of a full tinted-card background. */
@Composable
private fun DocumentUtilityCard(
    tool: ToolCard,
    onClick: () -> Unit,
    onCrystal: Boolean,
    modifier: Modifier = Modifier
) {
    val contentColor = if (onCrystal) Color(0xFFF4F2EC) else MaterialTheme.colorScheme.onSurface
    val descColor = if (onCrystal) Color(0xFFD7D3C8) else MaterialTheme.colorScheme.onSurfaceVariant
    val cardColor = if (onCrystal) Color(0xFF23262E) else MaterialTheme.colorScheme.surface
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = BorderStroke(1.dp, Color(0x14191C1E))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                ToolAvatar(
                    icon = tool.glyph,
                    tint = tool.tint,
                    size = 32.dp,
                    shape = RoundedCornerShape(10.dp)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        stringResource(tool.badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = descColor,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                stringResource(tool.label),
                style = MaterialTheme.typography.titleSmall,
                color = contentColor,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                stringResource(tool.description),
                style = MaterialTheme.typography.bodySmall,
                color = descColor,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(stringResource(tool.linkText), style = MaterialTheme.typography.labelMedium, color = tool.tint)
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = tool.tint,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * The flat/utilitarian List layout — a tinted circular icon avatar, title + a small colored
 * badge chip (e.g. "Multi-file", "Size preview"), a description line, and a trailing chevron,
 * on a plain, elevated white card. Each tool keeps its own identity tint for both its avatar
 * and its badge (not the single accent color) — Merge reads red, Split reads indigo, and so
 * on, the same variety as Bento's tiles.
 */
@Composable
private fun ListRow(tool: ToolCard, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ToolAvatar(icon = tool.glyph, tint = tool.tint, size = 44.dp, shape = RoundedCornerShape(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(tool.label),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(tool.tint.copy(alpha = 0.14f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(stringResource(tool.badge), style = MaterialTheme.typography.labelSmall, color = tool.tint)
                    }
                }
                Text(
                    stringResource(tool.description),
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
