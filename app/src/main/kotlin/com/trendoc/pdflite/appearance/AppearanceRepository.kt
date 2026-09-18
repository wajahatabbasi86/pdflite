package com.trendoc.pdflite.appearance

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appearanceDataStore by preferencesDataStore(name = "appearance_preferences")

/**
 * Persists the user's Appearance choices (accent color, background style, Home layout,
 * light/dark/system) via Jetpack DataStore and exposes them as a [Flow]. [TrenDocTheme] and
 * [HomeScreen] both collect this flow, so changing any value here re-renders the running app
 * immediately — the same mechanism Compose already uses for system dark-mode changes.
 */
class AppearanceRepository(private val context: Context) {

    private object Keys {
        val ACCENT = stringPreferencesKey("accent")
        val BACKGROUND = stringPreferencesKey("background")
        val HOME_LAYOUT = stringPreferencesKey("home_layout")
        val THEME = stringPreferencesKey("theme")
        val CARD_TINT = stringPreferencesKey("card_tint")
        val BORDER_TINT = stringPreferencesKey("border_tint")
        val MUTED_TINT = stringPreferencesKey("muted_tint")
    }

    val preferences: Flow<AppearancePreferences> = context.appearanceDataStore.data.map { prefs ->
        AppearancePreferences(
            accent = prefs.enumOrDefault(Keys.ACCENT, AccentColor.STAMP_RED),
            background = prefs.enumOrDefault(Keys.BACKGROUND, BackgroundStyle.PLAIN),
            homeLayout = prefs.enumOrDefault(Keys.HOME_LAYOUT, HomeLayout.LIST),
            theme = prefs.enumOrDefault(Keys.THEME, ThemeMode.SYSTEM),
            cardTint = prefs.enumOrDefault(Keys.CARD_TINT, CardTint.PAPER),
            borderTint = prefs.enumOrDefault(Keys.BORDER_TINT, BorderTint.SOFT),
            mutedTint = prefs.enumOrDefault(Keys.MUTED_TINT, MutedTint.WARM)
        )
    }

    suspend fun setAccent(accent: AccentColor) = write(Keys.ACCENT, accent.name)
    suspend fun setBackground(background: BackgroundStyle) = write(Keys.BACKGROUND, background.name)
    suspend fun setHomeLayout(layout: HomeLayout) = write(Keys.HOME_LAYOUT, layout.name)
    suspend fun setTheme(theme: ThemeMode) = write(Keys.THEME, theme.name)
    suspend fun setCardTint(tint: CardTint) = write(Keys.CARD_TINT, tint.name)
    suspend fun setBorderTint(tint: BorderTint) = write(Keys.BORDER_TINT, tint.name)
    suspend fun setMutedTint(tint: MutedTint) = write(Keys.MUTED_TINT, tint.name)

    suspend fun resetToDefaults() {
        context.appearanceDataStore.edit { it.clear() }
    }

    private suspend fun write(key: Preferences.Key<String>, value: String) {
        context.appearanceDataStore.edit { it[key] = value }
    }

    private inline fun <reified E : Enum<E>> Preferences.enumOrDefault(
        key: Preferences.Key<String>,
        default: E
    ): E = this[key]?.let { stored ->
        enumValues<E>().firstOrNull { it.name == stored }
    } ?: default
}
