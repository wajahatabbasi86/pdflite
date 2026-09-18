package com.trendoc.pdflite.util

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Thin wrapper around Storage Access Framework (SAF) operations.
 *
 * Per docs/REQUIREMENTS.md §1.1: all file selection uses SAF — no raw file path access,
 * no broad storage permission requests. Every tool screen (Merge, Split, Compress,
 * Image<->PDF) reuses these helpers instead of duplicating intent/contract code.
 */
object SafFileUtils {

    /** Contract for picking one file. Filter the launched intent's type via [ActivityResultContracts.OpenDocument]. */
    val openSingleDocument = ActivityResultContracts.OpenDocument()

    /** Contract for picking multiple files (used by Merge and Image(s)->PDF). */
    val openMultipleDocuments = ActivityResultContracts.OpenMultipleDocuments()

    /** Contract for choosing a save location for a single output file. */
    val createDocument = ActivityResultContracts.CreateDocument("application/pdf")

    /** Contract for choosing a destination directory (used by Split's split-into-ranges
     * mode, which produces multiple output files at once — §4.4). */
    val openDocumentTree = ActivityResultContracts.OpenDocumentTree()

    /**
     * Resolves a content [Uri]'s display name (e.g. "invoice.pdf"), falling back to the
     * last path segment if the resolver has no display name column.
     */
    fun displayName(context: Context, uri: Uri): String {
        val resolver: ContentResolver = context.contentResolver
        var name: String? = null
        var cursor: Cursor? = null
        try {
            cursor = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    name = cursor.getString(nameIndex)
                }
            }
        } finally {
            cursor?.close()
        }
        return name ?: uri.lastPathSegment ?: "unknown"
    }

    /**
     * Opens a [ParcelFileDescriptor] in read-only mode for the given [uri].
     * Caller is responsible for closing it.
     */
    fun openFileDescriptor(context: Context, uri: Uri): ParcelFileDescriptor? {
        return context.contentResolver.openFileDescriptor(uri, "r")
    }

    /**
     * Returns the file size in bytes as reported by the content resolver, or -1 if unknown.
     * Used for Compress's "show original size" requirement (§5) and large-file warnings.
     */
    fun fileSize(context: Context, uri: Uri): Long {
        val resolver = context.contentResolver
        var size = -1L
        var cursor: Cursor? = null
        try {
            cursor = resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        } finally {
            cursor?.close()
        }
        return size
    }
}
