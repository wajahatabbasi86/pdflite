package com.trendoc.pdflite.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.trendoc.pdflite.BuildConfig
import com.trendoc.pdflite.billing.AdConsent

/**
 * The single AdMob banner, shown under every screen by TrenDocNavHost. The caller is
 * responsible for not composing this at all once the "Remove Ads" purchase is active — per
 * §7's own wording, the banner view must be "removed entirely (not just hidden)" and ad SDK
 * init skipped, which a `visible = false` flag on an otherwise-composed AdView wouldn't satisfy.
 */
@Composable
fun AdBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // No consent yet (or refused where it is required) means no ad request at all — not an
    // empty banner slot, so the layout does not reserve space for an ad that cannot load.
    val canRequestAds by AdConsent.canRequestAds.collectAsState()
    if (!canRequestAds || !AdConsent.ensureAdsInitialized(context)) return
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
