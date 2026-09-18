package com.trendoc.pdflite.util

import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException

/**
 * The one place every tool screen's ViewModel gets its plain-language error text from, per
 * docs/REQUIREMENTS.md §1.4 ("plain-language error, never a raw stack trace... a distinct
 * message when the cause is specifically a password-protected PDF"). Before this, each
 * ViewModel (Merge, Split, Compress, PDF->Image, the reference Picker) duplicated its own
 * near-identical copy of this wording, which had drifted slightly out of sync between them —
 * centralizing it here keeps future wording changes from having to be repeated five times.
 */
object PdfErrorMessages {

    const val PASSWORD_PROTECTED = "This PDF is password-protected. Remove the password and try again."
    const val CORRUPTED_OR_PASSWORD_PROTECTED = "This file couldn't be read. It may be corrupted or password-protected."
    const val SAVE_FAILED_SINGLE = "Not enough space to save this file. Free up space and try again."
    const val SAVE_FAILED_PLURAL = "Not enough space to save these files. Free up space and try again."
    const val SAVE_FAILED_OUTPUT_FOLDER =
        "Couldn't save the converted images. Make sure there's enough free space and the destination folder is still accessible, then try again."

    /**
     * The message for a failure to *open/parse* a source PDF. Both exception types below are
     * thrown specifically for password-protected documents, just by different libraries:
     * PdfBox-Android's own APIs (Merge, Split, Compress's document loads) throw
     * [InvalidPasswordException], while Android's built-in `PdfRenderer` (Split/Compress/PDF->
     * Image/View PDF's thumbnail and page rendering) throws a plain [SecurityException] for the
     * same case. Anything else reads as generically corrupted/unreadable, since from the user's
     * side there's no way to tell those apart.
     */
    fun forOpenFailure(error: Throwable): String = when (error) {
        is InvalidPasswordException, is SecurityException -> PASSWORD_PROTECTED
        else -> CORRUPTED_OR_PASSWORD_PROTECTED
    }
}
