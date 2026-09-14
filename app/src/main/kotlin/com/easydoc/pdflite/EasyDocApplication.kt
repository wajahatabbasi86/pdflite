package com.easydoc.pdflite

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

/**
 * PdfBox-Android requires [PDFBoxResourceLoader.init] to be called once before any
 * PDDocument/PDFMergerUtility usage (it loads AFM font metrics etc. from assets).
 * Every tool screen that touches PdfBox-Android (Merge, Split, Compress, Image<->PDF)
 * relies on this having already run — do it once here instead of per-screen.
 */
class EasyDocApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)

        // AdMob SDK init (§7). HomeScreen only composes AdBanner when the "Remove Ads"
        // purchase isn't active, but this one-time SDK init is cheap enough (and stateless
        // enough) to always run here rather than gating it behind an entitlement read at
        // startup — no ad request is made until a banner is actually shown.
        MobileAds.initialize(applicationContext)
    }
}
