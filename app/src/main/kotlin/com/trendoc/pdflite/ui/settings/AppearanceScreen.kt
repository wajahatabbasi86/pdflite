package com.trendoc.pdflite.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trendoc.pdflite.appearance.AccentColor
import com.trendoc.pdflite.appearance.BackgroundStyle
import com.trendoc.pdflite.appearance.BorderTint
import com.trendoc.pdflite.appearance.CardTint
import com.trendoc.pdflite.appearance.CrystalSurface
import com.trendoc.pdflite.appearance.HomeLayout
import com.trendoc.pdflite.appearance.MutedTint
import com.trendoc.pdflite.appearance.ThemeMode

/**
 * Lets the user pick an accent color, three secondary color roles (Card/Border/Muted — a
 * scoped-down stand-in for a full per-token theme editor), a Home background style, a Home
 * layout, and light/dark/system. Every control writes straight to [AppearanceViewModel],
 * which persists via DataStore, so the rest of the app (starting with
 * [com.trendoc.pdflite.ui.home.HomeScreen]) reflects a change immediately.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(onBack: () -> Unit) {
    val viewModel: AppearanceViewModel = viewModel()
    val prefs by viewModel.preferences.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appearance") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        CrystalSurface(style = prefs.background, modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                SectionLabel("Accent color")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AccentColor.entries.forEach { accent ->
                        ColorDot(
                            color = accent.color(darkTheme = prefs.background == BackgroundStyle.CRYSTAL_INK),
                            selected = accent == prefs.accent,
                            onClick = { viewModel.setAccent(accent) }
                        )
                    }
                }

                SectionLabel("Card")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val darkTheme = prefs.background == BackgroundStyle.CRYSTAL_INK
                    CardTint.entries.forEach { tint ->
                        ColorDot(
                            color = tint.color(darkTheme),
                            selected = tint == prefs.cardTint,
                            onClick = { viewModel.setCardTint(tint) }
                        )
                    }
                }

                SectionLabel("Border")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val darkTheme = prefs.background == BackgroundStyle.CRYSTAL_INK
                    BorderTint.entries.forEach { tint ->
                        ColorDot(
                            color = tint.color(darkTheme),
                            selected = tint == prefs.borderTint,
                            onClick = { viewModel.setBorderTint(tint) }
                        )
                    }
                }

                SectionLabel("Muted")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val darkTheme = prefs.background == BackgroundStyle.CRYSTAL_INK
                    MutedTint.entries.forEach { tint ->
                        ColorDot(
                            color = tint.color(darkTheme),
                            selected = tint == prefs.mutedTint,
                            onClick = { viewModel.setMutedTint(tint) }
                        )
                    }
                }

                SectionLabel("Background")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BackgroundTile("Plain", BackgroundStyle.PLAIN, prefs.background, Modifier.weight(1f)) {
                        viewModel.setBackground(it)
                    }
                    BackgroundTile("Crystal · light", BackgroundStyle.CRYSTAL_LIGHT, prefs.background, Modifier.weight(1f)) {
                        viewModel.setBackground(it)
                    }
                    BackgroundTile("Crystal · ink", BackgroundStyle.CRYSTAL_INK, prefs.background, Modifier.weight(1f)) {
                        viewModel.setBackground(it)
                    }
                }

                SectionLabel("Home layout")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HomeLayout.entries.forEach { layout ->
                        LayoutTile(
                            layout = layout,
                            selected = layout == prefs.homeLayout,
                            // Bento and List are both built (see HomeScreen). Featured/
                            // Carousel are kept as real, selectable-later options rather
                            // than deleted, since they're already fully specified in the
                            // design system — just not implemented yet.
                            enabled = layout == HomeLayout.BENTO || layout == HomeLayout.LIST,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.setHomeLayout(layout) }
                        )
                    }
                }

                SectionLabel("Theme")
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = mode == prefs.theme,
                            onClick = { viewModel.setTheme(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = ThemeMode.entries.size)
                        ) {
                            Text(mode.name.lowercase().replaceFirstChar { it.uppercase() })
                        }
                    }
                }

                TextButton(onClick = { viewModel.resetToDefaults() }) {
                    Text("Reset to defaults")
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ColorDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color)
            .then(
                if (selected) {
                    Modifier.border(BorderStroke(2.dp, MaterialTheme.colorScheme.onBackground), CircleShape)
                } else Modifier
            )
            .clickable(onClick = onClick)
    )
}

@Composable
private fun BackgroundTile(
    label: String,
    style: BackgroundStyle,
    selected: BackgroundStyle,
    modifier: Modifier = Modifier,
    onClick: (BackgroundStyle) -> Unit
) {
    val isSelected = style == selected
    Card(
        modifier = modifier
            .aspectRatio(1f)
            .clickable { onClick(style) },
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(2.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        CrystalSurface(style = style, modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.35f))
                        .padding(vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun LayoutTile(
    layout: HomeLayout,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val alpha = if (enabled) 1f else 0.4f
    Card(
        modifier = modifier
            .aspectRatio(1f)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(2.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = layout.name.lowercase().replaceFirstChar { it.uppercase() } + if (!enabled) "\n(soon)" else "",
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
            )
        }
    }
}
