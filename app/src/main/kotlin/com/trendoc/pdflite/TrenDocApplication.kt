package com.trendoc.pdflite

import com.trendoc.pdflite.util.TempFiles
import com.trendoc.pdflite.di.AppContainer
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
    /** Shared dependencies; see [AppContainer]. */
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        // Photos of documents from a session that never cleaned up (killed mid-flow).
        CameraCaptureUtils.sweepStale(applicationContext)
        // Unsaved tool outputs from sessions that were killed before cleaning up.
        TempFiles.sweepStale(applicationContext)

        // The ads SDK is deliberately *not* initialized here: EEA/UK consent has to be
        // gathered first, and that needs an Activity. See billing/AdConsent.kt.
    }
}
