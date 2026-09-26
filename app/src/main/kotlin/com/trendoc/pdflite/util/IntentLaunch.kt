package com.trendoc.pdflite.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * Starts [intent], showing [failureMessage] instead of crashing when nothing on the device
 * can handle it.
 *
 * Every outbound intent in TrenDoc is optional to the task at hand — sharing a result,
 * opening a saved file in another app, viewing the privacy policy — so none of them is
 * worth taking the process down for. A bare `startActivity` throws
 * [ActivityNotFoundException] on a device with no browser, no PDF viewer, or (on API 30+,
 * where package visibility filters what an app can even see) an empty chooser. Centralized
 * here rather than repeated at each of the four call sites, in the same spirit as
 * [PdfErrorMessages].
 */
fun Context.startActivitySafely(intent: Intent, failureMessage: String) {
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, failureMessage, Toast.LENGTH_SHORT).show()
    }
}
