package com.trendoc.pdflite.billing

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.trendoc.pdflite.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How long one watched rewarded video removes the banner for — shorter than
 * [PAID_REMOVAL_DURATION_MILLIS] since this path costs nothing. */
const val VIDEO_REWARD_DURATION_MILLIS = 2L * 60 * 60 * 1000 // 2 hours

/**
 * The free path to temporary ad removal: watch one rewarded video, get [REWARD_DURATION_MILLIS]
 * of ad-free time (see [EntitlementRepository.grantAdFreeFor]). Kept separate from
 * [BillingRepository] since this never touches Play Billing at all — it's an AdMob rewarded ad
 * unit, not a purchase.
 */
class RewardedAdRepository(context: Context) {
    private val appContext = context.applicationContext

    private var rewardedAd: RewardedAd? = null

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun load() {
        if (rewardedAd != null || _isLoading.value) return
        _isLoading.value = true
        RewardedAd.load(
            appContext,
            // Per build type — Google's test unit in debug, the real one in release.
            // See app/build.gradle.kts and admob.properties.example.
            BuildConfig.REWARDED_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    _isLoading.value = false
                    _isReady.value = true
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    _isLoading.value = false
                    _isReady.value = false
                }
            }
        )
    }

    /** Shows the loaded ad, calling [onEarned] only if the user actually watched it through to
     * the reward (Play/AdMob's own [RewardedAd.show] callback), then preloads the next one so
     * a second watch doesn't need to wait on a fresh load. */
    fun show(activity: Activity, onEarned: () -> Unit) {
        val ad = rewardedAd ?: return
        _isReady.value = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                load()
            }

            /** Without this the ad that failed to show stayed assigned to [rewardedAd]
             * while isReady was already false, so load() short-circuited on the non-null
             * field and the "Watch" button sat on "Loading…" forever. Clearing it lets the
             * next load actually run. */
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                rewardedAd = null
                load()
            }
        }
        ad.show(activity) { onEarned() }
    }
}
