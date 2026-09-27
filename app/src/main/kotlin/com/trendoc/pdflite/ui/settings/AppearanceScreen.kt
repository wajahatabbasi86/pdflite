package com.trendoc.pdflite.ui.settings

import androidx.annotation.StringRes
import com.trendoc.pdflite.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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
import com.trendoc.pdflite.billing.AdConsent
import com.trendoc.pdflite.util.startActivitySafely
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
                title = { Text(stringResource(R.string.appearance_appearance)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
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
                SectionLabel(stringResource(R.string.appearance_section_accent))
                Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                    AccentColor.entries.forEach { accent ->
                        ColorDot(
                            label = stringResource(R.string.appearance_accent_option, stringResource(accent.label)),
                            color = accent.color(darkTheme = prefs.background == BackgroundStyle.CRYSTAL_INK),
                            selected = accent == prefs.accent,
                            onClick = { viewModel.setAccent(accent) }
                        )
                    }
                }

                SectionLabel(stringResource(R.string.appearance_section_card))
                Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                    val darkTheme = prefs.background == BackgroundStyle.CRYSTAL_INK
                    CardTint.entries.forEach { tint ->
                        ColorDot(
                            label = stringResource(R.string.appearance_card_option, stringResource(tint.label)),
                            color = tint.color(darkTheme),
                            selected = tint == prefs.cardTint,
                            onClick = { viewModel.setCardTint(tint) }
                        )
                    }
                }

                SectionLabel(stringResource(R.string.appearance_section_border))
                Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                    val darkTheme = prefs.background == BackgroundStyle.CRYSTAL_INK
                    BorderTint.entries.forEach { tint ->
                        ColorDot(
                            label = stringResource(R.string.appearance_border_option, stringResource(tint.label)),
                            color = tint.color(darkTheme),
                            selected = tint == prefs.borderTint,
                            onClick = { viewModel.setBorderTint(tint) }
                        )
                    }
                }

                SectionLabel(stringResource(R.string.appearance_section_muted))
                Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                    val darkTheme = prefs.background == BackgroundStyle.CRYSTAL_INK
                    MutedTint.entries.forEach { tint ->
                        ColorDot(
                            label = stringResource(R.string.appearance_muted_option, stringResource(tint.label)),
                            color = tint.color(darkTheme),
                            selected = tint == prefs.mutedTint,
                            onClick = { viewModel.setMutedTint(tint) }
                        )
                    }
                }

                SectionLabel(stringResource(R.string.appearance_section_background))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BackgroundTile(stringResource(R.string.appearance_background_plain), BackgroundStyle.PLAIN, prefs.background, Modifier.weight(1f)) {
                        viewModel.setBackground(it)
                    }
                    BackgroundTile(stringResource(R.string.appearance_background_crystal_light), BackgroundStyle.CRYSTAL_LIGHT, prefs.background, Modifier.weight(1f)) {
                        viewModel.setBackground(it)
                    }
                    BackgroundTile(stringResource(R.string.appearance_background_crystal_ink), BackgroundStyle.CRYSTAL_INK, prefs.background, Modifier.weight(1f)) {
                        viewModel.setBackground(it)
                    }
                }

                SectionLabel(stringResource(R.string.appearance_section_home_layout))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HomeLayout.entries.forEach { layout ->
                        LayoutTile(
                            layout = layout,
                            selected = layout == prefs.homeLayout,
                            enabled = true,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.setHomeLayout(layout) }
                        )
                    }
                }

                SectionLabel(stringResource(R.string.appearance_section_theme))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = mode == prefs.theme,
                            onClick = { viewModel.setTheme(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = ThemeMode.entries.size)
                        ) {
                            Text(stringResource(mode.labelRes()))
                        }
                    }
                }

                TextButton(onClick = { viewModel.resetToDefaults() }) {
                    Text(stringResource(R.string.appearance_reset_to_defaults))
                }

                SectionLabel(stringResource(R.string.appearance_section_about))
                val context = androidx.compose.ui.platform.LocalContext.current
                PrivacyPolicyRow(
                    onClick = {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(PRIVACY_POLICY_URL))
                        context.startActivitySafely(
                            intent,
                            context.getString(R.string.appearance_no_browser)
                        )
                    }
                )
                // UMP requires a way to revisit the consent choice wherever it applies
                // (EEA/UK); elsewhere it reports not-required and the row stays hidden.
                val privacyOptionsRequired by AdConsent.privacyOptionsRequired.collectAsState()
                if (privacyOptionsRequired) {
                    PrivacyPolicyRow(
                        title = stringResource(R.string.appearance_privacy_options),
                        opensExternally = false,
                        onClick = { (context as? android.app.Activity)?.let(AdConsent::showPrivacyOptions) }
                    )
                }
            }
        }
    }
}

/** Set once the privacy policy page (store-listing/privacy-policy.html) is hosted —
 * see docs on Play Console's "Privacy policy" requirement. */
private const val PRIVACY_POLICY_URL = "https://trenbridgeit.com/trendoc/privacy-policy.html"

@Composable
private fun PrivacyPolicyRow(
    title: String = stringResource(R.string.appearance_privacy_policy),
    opensExternally: Boolean = true,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color(0x14191C1E))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (opensExternally) {
                Icon(
                    Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        // Lets TalkBack users jump section to section instead of swiping every swatch.
        modifier = Modifier.semantics { heading() }
    )
}

@Composable
private fun ColorDot(label: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    // A 48dp touch target around the 36dp swatch. selectable() with a radio role gives
    // TalkBack the name, the selected state and "one of a group" — the colour alone, shown
    // only by a border, told a screen-reader user nothing.
    Box(
        modifier = Modifier
            .size(48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
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
        )
    }
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
    val description = stringResource(R.string.appearance_background_option, label)
    Card(
        modifier = modifier
            .aspectRatio(1f)
            .selectable(selected = isSelected, role = Role.RadioButton) { onClick(style) }
            .semantics(mergeDescendants = true) { contentDescription = description },
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
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
    val layoutName = stringResource(layout.labelRes())
    val description = stringResource(R.string.appearance_layout_option, layoutName)
    Card(
        modifier = modifier
            .aspectRatio(1f)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = description
            },
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = layoutName + if (!enabled) "\n" + stringResource(R.string.appearance_layout_soon) else "",
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
            )
        }
    }
}

/** Display names for enum constants that used to be shown via `name.lowercase()`. */
@StringRes
private fun HomeLayout.labelRes(): Int = when (this) {
    HomeLayout.BENTO -> R.string.appearance_layout_bento
    HomeLayout.LIST -> R.string.appearance_layout_list
    HomeLayout.FEATURED -> R.string.appearance_layout_featured
    HomeLayout.CAROUSEL -> R.string.appearance_layout_carousel
}

@StringRes
private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.LIGHT -> R.string.appearance_theme_light
    ThemeMode.DARK -> R.string.appearance_theme_dark
    ThemeMode.SYSTEM -> R.string.appearance_theme_system
}
