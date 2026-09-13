package com.easydoc.pdflite.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.easydoc.pdflite.appearance.AccentColor
import com.easydoc.pdflite.appearance.AppearancePreferences
import com.easydoc.pdflite.appearance.AppearanceRepository
import com.easydoc.pdflite.appearance.BackgroundStyle
import com.easydoc.pdflite.appearance.BorderTint
import com.easydoc.pdflite.appearance.CardTint
import com.easydoc.pdflite.appearance.HomeLayout
import com.easydoc.pdflite.appearance.MutedTint
import com.easydoc.pdflite.appearance.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs the Appearance screen. Every setter writes straight through to [AppearanceRepository]
 * (DataStore) — there's no local "unsaved" state or a separate Save action, matching how
 * system Settings apps apply a change the moment it's picked.
 */
class AppearanceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppearanceRepository(application)

    val preferences: StateFlow<AppearancePreferences> = repository.preferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppearancePreferences()
    )

    fun setAccent(accent: AccentColor) = viewModelScope.launch { repository.setAccent(accent) }
    fun setBackground(background: BackgroundStyle) = viewModelScope.launch { repository.setBackground(background) }
    fun setHomeLayout(layout: HomeLayout) = viewModelScope.launch { repository.setHomeLayout(layout) }
    fun setTheme(theme: ThemeMode) = viewModelScope.launch { repository.setTheme(theme) }
    fun setCardTint(tint: CardTint) = viewModelScope.launch { repository.setCardTint(tint) }
    fun setBorderTint(tint: BorderTint) = viewModelScope.launch { repository.setBorderTint(tint) }
    fun setMutedTint(tint: MutedTint) = viewModelScope.launch { repository.setMutedTint(tint) }
    fun resetToDefaults() = viewModelScope.launch { repository.resetToDefaults() }
}
