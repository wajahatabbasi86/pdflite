package com.trendoc.pdflite.util

import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class PdfErrorMessagesTest {

    @Test fun `PdfBox's password exception reads as password protected`() {
        // PdfBox only constructs this itself (the constructor is package-private).
        val error = InvalidPasswordException::class.java
            .getDeclaredConstructor(String::class.java)
            .apply { isAccessible = true }
            .newInstance("locked")
        assertEquals(PdfErrorMessages.PASSWORD_PROTECTED, PdfErrorMessages.forOpenFailure(error))
    }

    @Test fun `PdfRenderer's SecurityException reads as password protected`() {
        assertEquals(PdfErrorMessages.PASSWORD_PROTECTED, PdfErrorMessages.forOpenFailure(SecurityException()))
    }

    @Test fun `anything else reads as unreadable`() {
        listOf(IOException("bad xref"), IllegalStateException(), RuntimeException()).forEach {
            assertEquals(PdfErrorMessages.CORRUPTED_OR_PASSWORD_PROTECTED, PdfErrorMessages.forOpenFailure(it))
        }
    }
}
