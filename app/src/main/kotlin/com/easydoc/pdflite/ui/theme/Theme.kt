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
import com.easydoc.pdflite.appearance.ThemeMode

/**
 * Material 3 theme wrapper. Using M3's default color schemes (with dynamic color
 * on Android 12+) gives correct dark-mode behavior with no extra work — satisfies
 * docs/REQUIREMENTS.md §8 ("dark mode ... should not be actively broken").
 *
 * [themeMode] and [accent] come from the user's Appearance preferences (see
 * `appearance.AppearancePreferences`) rather than being fixed at compile time: whichever
 * screen calls this collects the stored preference as a [Flow], so a change re-renders
 * the whole app on the next recomposition, no restart needed.
 */
@Composable
fun EasyDocTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accent: AccentColor = AccentColor.STAMP_RED,
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
    // roles. Layer the chosen accent onto the base scheme's primary/secondary/tertiary —
    // everything else (surfaces, error colors, etc.) stays M3-default.
    val accentColor = accent.color(darkTheme)
    val onAccent = if (accentColor.luminance() > 0.5f) Color(0xFF1E2430) else Color.White
    val colorScheme = baseScheme.copy(
        primary = accentColor,
        onPrimary = onAccent,
        secondary = accentColor,
        onSecondary = onAccent,
        tertiary = accentColor
    )

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

private fun Color.luminance(): Float = (0.299f * red + 0.587f * green + 0.114f * blue)
