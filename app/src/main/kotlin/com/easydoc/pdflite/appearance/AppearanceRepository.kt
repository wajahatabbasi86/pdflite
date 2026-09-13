package com.easydoc.pdflite.appearance

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
 * light/dark/system) via Jetpack DataStore and exposes them as a [Flow]. [EasyDocTheme] and
 * [HomeScreen] both collect this flow, so changing any value here re-renders the running app
 * immediately — the same mechanism Compose already uses for system dark-mode changes.
 */
class AppearanceRepository(private val context: Context) {

    private object Keys {
        val ACCENT = stringPreferencesKey("accent")
        val BACKGROUND = stringPreferencesKey("background")
        val HOME_LAYOUT = stringPreferencesKey("home_layout")
        val THEME = stringPreferencesKey("theme")
    }

    val preferences: Flow<AppearancePreferences> = context.appearanceDataStore.data.map { prefs ->
        AppearancePreferences(
            accent = prefs.enumOrDefault(Keys.ACCENT, AccentColor.STAMP_RED),
            background = prefs.enumOrDefault(Keys.BACKGROUND, BackgroundStyle.CRYSTAL_INK),
            homeLayout = prefs.enumOrDefault(Keys.HOME_LAYOUT, HomeLayout.BENTO),
            theme = prefs.enumOrDefault(Keys.THEME, ThemeMode.SYSTEM)
        )
    }

    suspend fun setAccent(accent: AccentColor) = write(Keys.ACCENT, accent.name)
    suspend fun setBackground(background: BackgroundStyle) = write(Keys.BACKGROUND, background.name)
    suspend fun setHomeLayout(layout: HomeLayout) = write(Keys.HOME_LAYOUT, layout.name)
    suspend fun setTheme(theme: ThemeMode) = write(Keys.THEME, theme.name)

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
