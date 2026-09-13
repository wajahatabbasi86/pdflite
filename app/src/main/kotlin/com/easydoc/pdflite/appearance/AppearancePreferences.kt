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
 * The three additional color roles a user can tune independently of [AccentColor] — a
 * scoped-down version of a full per-token theme editor (Background/Foreground/Primary/
 * Secondary/Muted/Accent/Card/Border/Input), covering just the roles that visibly change
 * a card's surface, its edge, and its secondary/disabled text.
 */
enum class CardTint(val label: String, val light: Color, val dark: Color) {
    PAPER("Paper", light = Color(0xFFFFFFFF), dark = Color(0xFF23262E)),
    IVORY("Ivory", light = Color(0xFFFBF7EE), dark = Color(0xFF2A2620)),
    SLATE("Slate", light = Color(0xFFF1F3F6), dark = Color(0xFF20242C));

    fun color(darkTheme: Boolean): Color = if (darkTheme) dark else light
}

enum class BorderTint(val label: String, val light: Color, val dark: Color) {
    SOFT("Soft", light = Color(0xFFE3DFD5), dark = Color(0xFF333947)),
    CRISP("Crisp", light = Color(0xFFC7C2B4), dark = Color(0xFF4A5262)),
    INK("Ink", light = Color(0xFF8A8F9B), dark = Color(0xFF6B7280));

    fun color(darkTheme: Boolean): Color = if (darkTheme) dark else light
}

enum class MutedTint(val label: String, val light: Color, val dark: Color) {
    WARM("Warm", light = Color(0xFF8A8478), dark = Color(0xFFB7BCC6)),
    COOL("Cool", light = Color(0xFF6B7280), dark = Color(0xFFA4A9B6)),
    GRAY("Gray", light = Color(0xFF75797F), dark = Color(0xFF9CA3AF));

    fun color(darkTheme: Boolean): Color = if (darkTheme) dark else light
}

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
    val background: BackgroundStyle = BackgroundStyle.PLAIN,
    val homeLayout: HomeLayout = HomeLayout.LIST,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val cardTint: CardTint = CardTint.PAPER,
    val borderTint: BorderTint = BorderTint.SOFT,
    val mutedTint: MutedTint = MutedTint.WARM
)
