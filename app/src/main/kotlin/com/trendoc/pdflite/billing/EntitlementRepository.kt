package com.trendoc.pdflite.billing

import android.content.Context
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest

private val Context.entitlementDataStore by preferencesDataStore(name = "entitlement_preferences")

/**
 * Persists the ad-free window locally via Jetpack DataStore, the same mechanism
 * [com.trendoc.pdflite.appearance.AppearanceRepository] uses for Appearance prefs. Ad removal
 * is time-limited rather than a permanent flag: watching a rewarded video or buying "Remove
 * Ads" both extend [adsRemovedUntilMillis] into the future rather than setting a one-time
 * forever switch. [BillingRepository] and [RewardedAdRepository] are the only writers; the
 * global ad banner (see [com.trendoc.pdflite.nav.TrenDocNavHost]) and Home's status text only
 * ever read [isAdFree].
 */
class EntitlementRepository(private val context: Context) {

    private val adsRemovedUntilKey = longPreferencesKey("ads_removed_until_millis")

    /** Epoch millis the current ad-free window runs until, or 0 if none is active. */
    val adsRemovedUntilMillis: Flow<Long> = context.entitlementDataStore.data.map { prefs ->
        prefs[adsRemovedUntilKey] ?: 0L
    }

    /**
     * True while now is before [adsRemovedUntilMillis], flipping to false on its own the
     * moment the window expires — without needing a new write, so the banner reappears
     * automatically rather than staying hidden until the next app launch.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val isAdFree: Flow<Boolean> = adsRemovedUntilMillis.transformLatest { until ->
        val remaining = until - System.currentTimeMillis()
        if (remaining > 0) {
            emit(true)
            delay(remaining)
            emit(false)
        } else {
            emit(false)
        }
    }

    /** Millis remaining in the current ad-free window, or 0 if none is active. Used for the
     * "Ad-free for Xh Ym" status text rather than a plain forever/not-forever flag. */
    val remainingMillis: Flow<Long> = adsRemovedUntilMillis.map { until ->
        (until - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    /** Extends the ad-free window by [durationMillis] from whichever is later, now or the
     * current expiry — so watching a second video while one is still active stacks on top of
     * it rather than overwriting it. */
    suspend fun grantAdFreeFor(durationMillis: Long) {
        context.entitlementDataStore.edit { prefs ->
            val current = prefs[adsRemovedUntilKey] ?: 0L
            val base = maxOf(current, System.currentTimeMillis())
            prefs[adsRemovedUntilKey] = base + durationMillis
        }
    }
}
