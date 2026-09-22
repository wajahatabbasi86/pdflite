package com.trendoc.pdflite.onboarding

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.onboardingDataStore by preferencesDataStore(name = "onboarding_preferences")

/** Tracks whether the first-launch walkthrough has been completed, so it only ever
 * shows once per install (see [com.trendoc.pdflite.nav.TrenDocNavHost]'s start destination). */
class OnboardingRepository(private val context: Context) {

    private object Keys {
        val COMPLETED = booleanPreferencesKey("completed")
    }

    val hasCompletedOnboarding: Flow<Boolean> = context.onboardingDataStore.data.map { prefs ->
        prefs[Keys.COMPLETED] ?: false
    }

    suspend fun setCompleted() {
        context.onboardingDataStore.edit { it[Keys.COMPLETED] = true }
    }
}
