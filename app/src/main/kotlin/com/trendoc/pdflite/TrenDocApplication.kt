package com.trendoc.pdflite

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.trendoc.pdflite.util.CameraCaptureUtils

/**
 * PdfBox-Android requires [PDFBoxResourceLoader.init] to be called once before any
 * PDDocument/PDFMergerUtility usage (it loads AFM font metrics etc. from assets).
 * Every tool screen that touches PdfBox-Android (Merge, Split, Compress, Image<->PDF)
 * relies on this having already run — do it once here instead of per-screen.
 */
class TrenDocApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        // Photos of documents from a session that never cleaned up (killed mid-flow).
        CameraCaptureUtils.sweepStale(applicationContext)

        // The ads SDK is deliberately *not* initialized here: EEA/UK consent has to be
        // gathered first, and that needs an Activity. See billing/AdConsent.kt.
    }
}
