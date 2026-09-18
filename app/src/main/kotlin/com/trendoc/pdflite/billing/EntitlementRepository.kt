package com.trendoc.pdflite.billing

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.entitlementDataStore by preferencesDataStore(name = "entitlement_preferences")

/**
 * Persists the "Remove Ads" one-time-purchase entitlement locally via Jetpack DataStore, the
 * same mechanism [com.trendoc.pdflite.appearance.AppearanceRepository] uses for Appearance
 * prefs. [BillingRepository] is the only writer — it sets this the moment a purchase is
 * acknowledged (or a prior purchase is found via "Restore Purchases") — while [HomeScreen] and
 * the ad-banner composable only ever read it, per docs/REQUIREMENTS.md §7 ("If the purchase is
 * active, banner view is removed entirely... and ad SDK init for banners is skipped").
 */
class EntitlementRepository(private val context: Context) {

    private val adsRemovedKey = booleanPreferencesKey("ads_removed")

    val adsRemoved: Flow<Boolean> = context.entitlementDataStore.data.map { prefs ->
        prefs[adsRemovedKey] ?: false
    }

    suspend fun setAdsRemoved(removed: Boolean) {
        context.entitlementDataStore.edit { it[adsRemovedKey] = removed }
    }
}
