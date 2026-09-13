package com.easydoc.pdflite.appearance

import androidx.compose.ui.graphics.Color

/**
 * The accent color a user can pick in Appearance settings. Every option carries a light-theme
 * and dark-theme value (mirroring the design system's Stamp Red → light/dark pair) so accent
 * choice and light/dark theme stay independent of each other.
 */
enum class AccentColor(val label: String, val light: Color, val dark: Color) {
    STAMP_RED("Stamp Red", light = Color(0xFFC1442D), dark = Color(0xFFE2593F)),
    INDIGO("Indigo", light = Color(0xFF4C5FD5), dark = Color(0xFF7C89E0)),
    TEAL("Teal", light = Color(0xFF2F8F82), dark = Color(0xFF5FB3A6)),
    AMBER("Amber", light = Color(0xFFC98A2E), dark = Color(0xFFE0A94F)),
    PLUM("Plum", light = Color(0xFF7A4B8A), dark = Color(0xFFA576B3)),
    FOREST("Forest", light = Color(0xFF3F7A50), dark = Color(0xFF6BA97C));

    fun color(darkTheme: Boolean): Color = if (darkTheme) dark else light
}

/** Home screen background, per the design system's "Background" section. */
enum class BackgroundStyle { PLAIN, CRYSTAL_LIGHT, CRYSTAL_INK }

/**
 * Home screen tool-card arrangement. Only [BENTO] is implemented today (it's the shipped
 * default); the others are kept here — rather than deleted — because they're already fully
 * specified in the design system and are the natural next slice of this feature, not a
 * hypothetical one.
 */
enum class HomeLayout { BENTO, LIST, FEATURED, CAROUSEL }

enum class ThemeMode { LIGHT, DARK, SYSTEM }

/** A user's full Appearance selection. Every field is independently changeable and, once
 * changed, re-renders the running app immediately — see [AppearanceRepository]. */
data class AppearancePreferences(
    val accent: AccentColor = AccentColor.STAMP_RED,
    val background: BackgroundStyle = BackgroundStyle.CRYSTAL_INK,
    val homeLayout: HomeLayout = HomeLayout.BENTO,
    val theme: ThemeMode = ThemeMode.SYSTEM
)
