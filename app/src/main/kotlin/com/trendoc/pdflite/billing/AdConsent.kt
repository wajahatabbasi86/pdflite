package com.trendoc.pdflite.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.trendoc.pdflite.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Google's EU user consent policy: before any ad is requested for a user in the EEA or UK, a
 * Google-certified CMP has to have gathered consent. This wraps the User Messaging Platform
 * (AdMob's own CMP) and is the only place the ads SDK is initialized.
 *
 * Order on every launch: refresh consent info → show the form if UMP says one is required →
 * only then flip [canRequestAds] and initialize [MobileAds]. Outside the EEA/UK UMP reports
 * consent as not required and [canRequestAds] becomes true straight away.
 *
 * The ads SDK is initialized lazily, from [ensureAdsInitialized], by whichever ad is shown
 * first — so while an ad-free period is active and no banner is composed, it never starts.
 */
object AdConsent {
    private const val TAG = "AdConsent"

    private val _canRequestAds = MutableStateFlow(false)
    /** True once consent allows requesting ads. Nothing may load an ad while this is false. */
    val canRequestAds: StateFlow<Boolean> = _canRequestAds.asStateFlow()

    private val _privacyOptionsRequired = MutableStateFlow(false)
    /** Whether settings must offer a "Privacy options" entry (UMP requires it for EEA/UK). */
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    private val gathering = AtomicBoolean(false)
    private val adsInitialized = AtomicBoolean(false)

    /** Call from the activity's onCreate on every launch; UMP caches answers, so a returning
     * user who already consented sees nothing. */
    fun gather(activity: Activity) {
        val info = UserMessagingPlatform.getConsentInformation(activity)
        // Consent from a previous session still counts while the refresh below is in flight.
        publish(info)
        if (!gathering.compareAndSet(false, true)) return

        val params = ConsentRequestParameters.Builder().apply {
            if (BuildConfig.DEBUG && BuildConfig.UMP_TEST_DEVICE_ID.isNotEmpty()) {
                setConsentDebugSettings(
                    ConsentDebugSettings.Builder(activity)
                        .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                        .addTestDeviceHashedId(BuildConfig.UMP_TEST_DEVICE_ID)
                        .build()
                )
            }
        }.build()

        info.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                    if (error != null) Log.w(TAG, "Consent form: ${error.errorCode} ${error.message}")
                    publish(info)
                    gathering.set(false)
                }
            },
            { error ->
                // Offline or misconfigured: fall back to whatever was stored last time. With no
                // stored consent canRequestAds() is false, so no ad is requested — the safe side.
                Log.w(TAG, "Consent info update: ${error.errorCode} ${error.message}")
                publish(info)
                gathering.set(false)
            }
        )
    }

    /** Re-opens the consent form so the user can change their choice (Settings → Privacy options). */
    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            if (error != null) Log.w(TAG, "Privacy options: ${error.errorCode} ${error.message}")
            publish(UserMessagingPlatform.getConsentInformation(activity))
        }
    }

    /** Starts the ads SDK once, and only after consent allows it. Returns whether ads may load. */
    fun ensureAdsInitialized(context: Context): Boolean {
        if (!_canRequestAds.value) return false
        if (adsInitialized.compareAndSet(false, true)) {
            MobileAds.initialize(context.applicationContext)
        }
        return true
    }

    private fun publish(info: ConsentInformation) {
        _canRequestAds.value = info.canRequestAds()
        _privacyOptionsRequired.value = info.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }
}
