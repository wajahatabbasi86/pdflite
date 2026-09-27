package com.trendoc.pdflite.util

import android.content.Context
import java.io.File

/**
 * The intermediate PDFs tools write to cache before the user picks where to save them
 * (`merge_<uuid>.pdf`, `split_…`, …). Each ViewModel deletes its own when the save completes or
 * the screen is left; this catches what a killed process never got to delete.
 */
object TempFiles {

    private val PREFIXES = listOf("compress_", "merge_", "split_", "images_", "filled_", "stamped_")
    private const val STALE_AFTER_MILLIS = 24L * 60 * 60 * 1000

    /** Call once on app start. Only this app's own temp outputs, only when stale. */
    fun sweepStale(context: Context, now: Long = System.currentTimeMillis()) {
        context.cacheDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".pdf") && PREFIXES.any(file.name::startsWith) &&
                now - file.lastModified() > STALE_AFTER_MILLIS
            ) {
                file.delete()
            }
        }
    }

    /** Deletes [file] if it exists; for a ViewModel dropping an unsaved output. */
    fun discard(file: File?) {
        file?.delete()
    }
}
