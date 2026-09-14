package com.easydoc.pdflite.nav

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Holds a PDF Uri the app was just asked to open from *outside* itself — the system
 * "Open with" chooser (a PDF tapped in Files, Downloads, an email attachment, etc.),
 * per docs/REQUIREMENTS.md's spirit of "no dead ends": tapping a PDF elsewhere on the
 * device should land straight on View PDF with that file loaded, not on Home.
 *
 * This is deliberately a simple in-memory holder rather than a nav-graph argument —
 * passing a content Uri through Compose Navigation's string-based args means URL-encoding
 * it and hoping every special character round-trips; a one-shot holder consumed exactly
 * once by [EasyDocNavHost] avoids that entirely. [MainActivity] writes to it (from
 * onCreate for a cold start, onNewIntent for singleTask re-delivery); the NavHost reads
 * and immediately clears it so back-navigation or a config change never re-triggers it.
 */
object PendingPdfIntent {
    val uri = MutableStateFlow<Uri?>(null)

    fun consume(): Uri? {
        val value = uri.value
        uri.value = null
        return value
    }
}
