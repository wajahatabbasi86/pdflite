package com.trendoc.pdflite.nav

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * One-shot holders that pass the file currently open in View PDF into Split or Compress
 * when the user taps a quick-action bridge ("Extract Page" / "Compress") — same pattern
 * as [PendingPdfIntent], and for the same reason: passing a content Uri through Compose
 * Navigation's string-based args means URL-encoding it, while a one-shot holder consumed
 * exactly once by the destination screen avoids that entirely.
 */
object PendingSplitUri {
    val uri = MutableStateFlow<Uri?>(null)

    fun consume(): Uri? {
        val value = uri.value
        uri.value = null
        return value
    }
}

object PendingCompressUri {
    val uri = MutableStateFlow<Uri?>(null)

    fun consume(): Uri? {
        val value = uri.value
        uri.value = null
        return value
    }
}

object PendingFillFormsUri {
    val uri = MutableStateFlow<Uri?>(null)

    fun consume(): Uri? {
        val value = uri.value
        uri.value = null
        return value
    }
}
