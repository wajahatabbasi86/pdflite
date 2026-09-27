package com.trendoc.pdflite.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/**
 * Hands the system camera app a writable [Uri] for a photo it captures, via [FileProvider] —
 * kept separate from [SafFileUtils] since this is the one deliberate exception to "every file
 * access goes through SAF" (§1.1): there's no SAF contract for "let another app write a new
 * photo," only for picking an existing file or choosing a save location. The Uri is scoped to
 * a single temp file in this app's own cache (see res/xml/file_paths.xml), not broader access,
 * and once the camera returns, that Uri flows straight into the same `content://` pipeline
 * every gallery-picked image already uses (see [com.trendoc.pdflite.ui.imagetopdf.ImageToPdfViewModel.onImagesPicked]) —
 * no separate code path downstream of this one call.
 */
object CameraCaptureUtils {

    private const val AUTHORITY = "com.trendoc.pdflite.fileprovider"
    private const val DIR = "camera_captures"
    private const val PREFIX = "capture_"

    /** Anything older than this is left over from a session that ended without cleaning up
     * (process killed, crash) and is swept on the next launch. */
    private const val STALE_AFTER_MILLIS = 24L * 60 * 60 * 1000

    fun newCaptureUri(context: Context): Uri {
        val file = File(captureDir(context).apply { mkdirs() }, "$PREFIX${UUID.randomUUID()}.jpg")
        return FileProvider.getUriForFile(context, AUTHORITY, file)
    }

    /**
     * Deletes the photo behind [uri] if — and only if — it is one of this app's own captures.
     * These are photos of the user's documents; they should live exactly as long as the
     * Image(s) → PDF session using them, not accumulate in cache indefinitely.
     * Gallery-picked Uris belong to other apps and are ignored.
     */
    fun deleteIfCapture(context: Context, uri: Uri) {
        captureFile(context, uri)?.delete()
    }

    /** Removes captures left behind by earlier sessions. Call once on app start. */
    fun sweepStale(context: Context, now: Long = System.currentTimeMillis()) {
        captureDir(context).listFiles()?.forEach { file ->
            if (now - file.lastModified() > STALE_AFTER_MILLIS) file.delete()
        }
    }

    private fun captureDir(context: Context) = File(context.cacheDir, DIR)

    /** Maps a FileProvider Uri back to its file, refusing anything that isn't a plain capture
     * file name in the capture directory — so a crafted Uri can't point the delete elsewhere. */
    private fun captureFile(context: Context, uri: Uri): File? {
        if (uri.scheme != "content" || uri.authority != AUTHORITY) return null
        val segments = uri.pathSegments
        if (segments.size != 2 || segments[0] != DIR) return null
        val name = segments[1]
        if (!name.startsWith(PREFIX) || name.contains('/') || name.contains("..")) return null
        return File(captureDir(context), name)
    }
}
