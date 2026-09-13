package com.easydoc.pdflite

import android.app.Application
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
    }
}
