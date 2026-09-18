package com.easydoc.pdflite.util

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
 * every gallery-picked image already uses (see [com.easydoc.pdflite.ui.imagetopdf.ImageToPdfViewModel.onImagesPicked]) —
 * no separate code path downstream of this one call.
 */
object CameraCaptureUtils {

    fun newCaptureUri(context: Context): Uri {
        val dir = File(context.cacheDir, "camera_captures").apply { mkdirs() }
        val file = File(dir, "capture_${UUID.randomUUID()}.jpg")
        return FileProvider.getUriForFile(context, "com.easydoc.pdflite.fileprovider", file)
    }
}
