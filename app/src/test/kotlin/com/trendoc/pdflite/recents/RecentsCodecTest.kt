package com.trendoc.pdflite.recents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentsCodecTest {

    private fun entry(n: Int, source: String = "View PDF") = StoredRecent(
        uri = "content://docs/document/$n",
        displayName = "file $n.pdf",
        sizeBytes = 1000L * n,
        pageCount = n,
        timestampMillis = 1_700_000_000_000L + n,
        sourceLabel = source
    )

    @Test fun `encode then decode round-trips every field`() {
        val entries = listOf(entry(1), entry(2, "Compress"), entry(3, ""))
        assertEquals(entries, RecentsCodec.decode(RecentsCodec.encode(entries)))
    }

    @Test fun `names with quotes and unicode survive`() {
        val tricky = entry(1).copy(displayName = "Invoice \"final\" — é 日本.pdf")
        assertEquals(listOf(tricky), RecentsCodec.decode(RecentsCodec.encode(listOf(tricky))))
    }

    @Test fun `missing, blank or corrupt data is an empty list`() {
        assertTrue(RecentsCodec.decode(null).isEmpty())
        assertTrue(RecentsCodec.decode("").isEmpty())
        assertTrue(RecentsCodec.decode("not json").isEmpty())
        assertTrue(RecentsCodec.decode("[{\"uri\":\"x\"}]").isEmpty()) // required fields missing
    }

    @Test fun `entries written before sourceLabel existed decode with an empty label`() {
        val legacy = """[{"uri":"content://a","name":"a.pdf","size":1,"pages":1,"ts":5}]"""
        assertEquals("", RecentsCodec.decode(legacy).single().sourceLabel)
    }

    @Test fun `a new entry goes first`() {
        val result = RecentsCodec.upsert(listOf(entry(1), entry(2)), entry(3))
        assertEquals(listOf(3, 1, 2), result.map { it.pageCount })
    }

    @Test fun `re-touching an entry moves it to the top without duplicating it`() {
        val retouched = entry(2).copy(timestampMillis = 9, sourceLabel = "Split")
        val result = RecentsCodec.upsert(listOf(entry(1), entry(2), entry(3)), retouched)
        assertEquals(listOf(2, 1, 3), result.map { it.pageCount })
        assertEquals("Split", result.first().sourceLabel)
        assertEquals(1, result.count { it.uri == retouched.uri })
    }

    @Test fun `the list is capped, dropping the oldest`() {
        val full = (1..RecentsCodec.MAX_ENTRIES).map { entry(it) }
        val result = RecentsCodec.upsert(full, entry(99))
        assertEquals(RecentsCodec.MAX_ENTRIES, result.size)
        assertEquals(99, result.first().pageCount)
        assertEquals(RecentsCodec.MAX_ENTRIES - 1, result.last().pageCount)
    }
}
