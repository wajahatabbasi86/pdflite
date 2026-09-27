package com.trendoc.pdflite.ui.split

import com.trendoc.pdflite.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertEquals(RangeProblem(R.string.range_empty), PageRanges.validate(", ,", 5))
    }

    @Test fun `pages outside the document are rejected`() {
        assertEquals(RangeProblem(R.string.range_page_out_of_range, listOf(6, 5)), PageRanges.validate("6", 5))
        assertEquals(RangeProblem(R.string.range_page_out_of_range, listOf(0, 5)), PageRanges.validate("0", 5))
        assertEquals(RangeProblem(R.string.range_range_out_of_range, listOf("4-6", 5)), PageRanges.validate("4-6", 5))
    }

    @Test fun `a reversed range is rejected`() {
        assertEquals(RangeProblem(R.string.range_range_out_of_range, listOf("5-2", 5)), PageRanges.validate("5-2", 5))
    }

    @Test fun `malformed input names the offending part`() {
        assertEquals(RangeProblem(R.string.range_bad_page, listOf("abc")), PageRanges.validate("1, abc", 5))
        assertEquals(RangeProblem(R.string.range_bad_range, listOf("1-x")), PageRanges.validate("1-x", 5))
        assertEquals(RangeProblem(R.string.range_bad_range, listOf("1-2-3")), PageRanges.validate("1-2-3", 5))
        assertEquals(listOf<Any>("-3"), PageRanges.validate("-3", 5)!!.args)
    }
}
