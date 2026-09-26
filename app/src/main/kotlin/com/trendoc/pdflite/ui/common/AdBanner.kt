package com.trendoc.pdflite.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.trendoc.pdflite.BuildConfig

/**
 * The single AdMob banner, per docs/REQUIREMENTS.md §7 — "Loads on Home screen only; standard
 * AdMob banner unit, no interstitial or rewarded ad units in v1." The caller (HomeScreen) is
 * responsible for not composing this at all once the "Remove Ads" purchase is active — per
 * §7's own wording, the banner view must be "removed entirely (not just hidden)" and ad SDK
 * init skipped, which a `visible = false` flag on an otherwise-composed AdView wouldn't satisfy.
 */
@Composable
fun AdBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = {
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                // Per build type — Google's test unit in debug, the real one in release.
                // See app/build.gradle.kts and admob.properties.example.
                adUnitId = BuildConfig.BANNER_AD_UNIT_ID
                loadAd(AdRequest.Builder().build())
            }
        },
        update = { /* no dynamic props to sync — the ad unit and size are fixed for v1 */ },
        onRelease = { adView -> adView.destroy() }
    )
}
