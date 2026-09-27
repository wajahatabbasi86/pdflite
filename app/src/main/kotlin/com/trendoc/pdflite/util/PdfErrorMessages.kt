package com.trendoc.pdflite.util

import com.trendoc.pdflite.R
import androidx.annotation.StringRes
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException

/**
 * The one place every tool screen's ViewModel gets its plain-language error text from — as
 * string resource ids, resolved with `getString` by the caller so the text is translatable — per
 * docs/REQUIREMENTS.md §1.4 ("plain-language error, never a raw stack trace... a distinct
 * message when the cause is specifically a password-protected PDF"). Before this, each
 * ViewModel (Merge, Split, Compress, PDF->Image, the reference Picker) duplicated its own
 * near-identical copy of this wording, which had drifted slightly out of sync between them —
 * centralizing it here keeps future wording changes from having to be repeated five times.
 */
object PdfErrorMessages {

    @StringRes val PASSWORD_PROTECTED = R.string.error_password_protected
    @StringRes val CORRUPTED_OR_PASSWORD_PROTECTED = R.string.error_unreadable
    @StringRes val SAVE_FAILED_SINGLE = R.string.error_save_failed_single
    @StringRes val SAVE_FAILED_PLURAL = R.string.error_save_failed_plural
    /** A failure while *writing* the filled/edited document, as opposed to reading it.
     * Reusing [forOpenFailure] here told the user their file was "corrupted or
     * password-protected" when in fact it had opened fine and only the write had failed. */
    @StringRes val WRITE_FAILED = R.string.error_write_failed

    @StringRes val SAVE_FAILED_OUTPUT_FOLDER =
        R.string.error_save_failed_output_folder

    /**
     * The message for a failure to *open/parse* a source PDF. Both exception types below are
     * thrown specifically for password-protected documents, just by different libraries:
     * PdfBox-Android's own APIs (Merge, Split, Compress's document loads) throw
     * [InvalidPasswordException], while Android's built-in `PdfRenderer` (Split/Compress/PDF->
     * Image/View PDF's thumbnail and page rendering) throws a plain [SecurityException] for the
     * same case. Anything else reads as generically corrupted/unreadable, since from the user's
     * side there's no way to tell those apart.
     */
    @StringRes
    fun forOpenFailure(error: Throwable): Int = when (error) {
        is InvalidPasswordException, is SecurityException -> PASSWORD_PROTECTED
        else -> CORRUPTED_OR_PASSWORD_PROTECTED
    }
}
