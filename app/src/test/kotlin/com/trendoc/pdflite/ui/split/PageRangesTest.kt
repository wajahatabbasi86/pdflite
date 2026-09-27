package com.trendoc.pdflite.ui.split

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageRangesTest {

    @Test fun `mixed pages and ranges become one group per part`() {
        assertEquals(
            listOf(listOf(0, 1, 2), listOf(6), listOf(8, 9, 10, 11)),
            PageRanges.toPageGroups("1-3, 7, 9-12")
        )
    }

    @Test fun `whitespace and empty parts are tolerated`() {
        assertNull(PageRanges.validate(" 1 - 2 ,, 4 ,", 5))
        assertEquals(listOf(listOf(0, 1), listOf(3)), PageRanges.toPageGroups(" 1 - 2 ,, 4 ,"))
    }

    @Test fun `overlapping ranges are allowed and kept as separate files`() {
        assertNull(PageRanges.validate("1-3, 2-4", 5))
        assertEquals(listOf(listOf(0, 1, 2), listOf(1, 2, 3)), PageRanges.toPageGroups("1-3, 2-4"))
    }

    @Test fun `a single-page range is valid`() {
        assertNull(PageRanges.validate("3-3", 5))
        assertEquals(listOf(listOf(2)), PageRanges.toPageGroups("3-3"))
    }

    @Test fun `blank input is not an error`() {
        assertNull(PageRanges.validate("", 5))
        assertNull(PageRanges.validate("   ", 5))
    }

    @Test fun `only separators asks for a page`() {
        assertEquals("Enter at least one page or range.", PageRanges.validate(", ,", 5))
    }

    @Test fun `pages outside the document are rejected`() {
        assertEquals("Page 6 is out of range (1-5).", PageRanges.validate("6", 5))
        assertEquals("Page 0 is out of range (1-5).", PageRanges.validate("0", 5))
        assertEquals("\"4-6\" is out of range (1-5).", PageRanges.validate("4-6", 5))
    }

    @Test fun `a reversed range is rejected`() {
        assertEquals("\"5-2\" is out of range (1-5).", PageRanges.validate("5-2", 5))
    }

    @Test fun `malformed input names the offending part`() {
        assertEquals("\"abc\" isn't a valid page number.", PageRanges.validate("1, abc", 5))
        assertEquals("\"1-x\" isn't a valid range.", PageRanges.validate("1-x", 5))
        assertEquals("\"1-2-3\" isn't a valid range.", PageRanges.validate("1-2-3", 5))
        assertTrue(PageRanges.validate("-3", 5)!!.contains("-3"))
    }
}
