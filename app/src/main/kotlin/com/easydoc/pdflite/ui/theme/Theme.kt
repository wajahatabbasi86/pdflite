package com.easydoc.pdflite.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import android.content.Context
import androidx.compose.ui.graphics.Color
import com.easydoc.pdflite.appearance.AccentColor
import com.easydoc.pdflite.appearance.BorderTint
import com.easydoc.pdflite.appearance.CardTint
import com.easydoc.pdflite.appearance.MutedTint
import com.easydoc.pdflite.appearance.ThemeMode

/**
 * Material 3 theme wrapper. Using M3's default color schemes (with dynamic color
 * on Android 12+) gives correct dark-mode behavior with no extra work — satisfies
 * docs/REQUIREMENTS.md §8 ("dark mode ... should not be actively broken").
 *
 * Every parameter here comes from the user's Appearance preferences (see
 * `appearance.AppearancePreferences`) rather than being fixed at compile time: whichever
 * screen calls this collects the stored preferences as a [Flow], so a change re-renders
 * the whole app on the next recomposition, no restart needed. [accent]/[cardTint]/
 * [borderTint]/[mutedTint] are a scoped-down stand-in for a full per-token theme editor —
 * just the roles that visibly change a card's surface, its edge, and its secondary text —
 * rather than every M3 color role independently.
 */
@Composable
fun EasyDocTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accent: AccentColor = AccentColor.STAMP_RED,
    cardTint: CardTint = CardTint.PAPER,
    borderTint: BorderTint = BorderTint.SOFT,
    mutedTint: MutedTint = MutedTint.WARM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val baseScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    // dynamicColor defaults to off: a user-picked accent (below) and a system-derived
    // wallpaper palette would otherwise fight each other for the same primary/secondary
    // roles. Layer the chosen accent/card/border/muted tints onto the base scheme —
    // everything else (error colors, etc.) stays M3-default.
    val accentColor = accent.color(darkTheme)
    val onAccent = if (accentColor.luminance() > 0.5f) Color(0xFF1E2430) else Color.White
    val cardColor = cardTint.color(darkTheme)
    val borderColor = borderTint.color(darkTheme)
    val mutedColor = mutedTint.color(darkTheme)
    val colorScheme = baseScheme.copy(
        primary = accentColor,
        onPrimary = onAccent,
        secondary = accentColor,
        onSecondary = onAccent,
        tertiary = accentColor,
        surface = cardColor,
        surfaceVariant = cardColor,
        outline = borderColor,
        outlineVariant = borderColor.copy(alpha = 0.6f),
        onSurfaceVariant = mutedColor
    )

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

private fun Color.luminance(): Float = (0.299f * red + 0.587f * green + 0.114f * blue)
