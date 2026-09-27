package com.trendoc.pdflite.ui.split

/**
 * Split's "ranges" input — e.g. `1-3, 5, 7-9`, one output file per comma-separated part.
 * Pure so it can be unit tested without a ViewModel or a PDF.
 */
object PageRanges {

    /** A user-facing problem with [input] against a document of [pageCount] pages, or null
     * when it is valid (a blank input is "nothing entered yet", not an error). */
    fun validate(input: String, pageCount: Int): String? {
        if (input.isBlank()) return null
        val parts = parts(input)
        if (parts.isEmpty()) return "Enter at least one page or range."

        for (part in parts) {
            val range = part.split("-").map { it.trim() }
            when (range.size) {
                1 -> {
                    val page = range[0].toIntOrNull()
                        ?: return "\"$part\" isn't a valid page number."
                    if (page < 1 || page > pageCount) {
                        return "Page $page is out of range (1-$pageCount)."
                    }
                }
                2 -> {
                    val start = range[0].toIntOrNull()
                    val end = range[1].toIntOrNull()
                    if (start == null || end == null) return "\"$part\" isn't a valid range."
                    if (start < 1 || end > pageCount || start > end) {
                        return "\"$part\" is out of range (1-$pageCount)."
                    }
                }
                else -> return "\"$part\" isn't a valid range."
            }
        }
        return null
    }

    /** Zero-based page indices, one list per output file. Only call on input [validate] accepted. */
    fun toPageGroups(input: String): List<List<Int>> =
        parts(input).map { part ->
            val range = part.split("-").map { it.trim() }
            if (range.size == 1) {
                listOf(range[0].toInt() - 1)
            } else {
                (range[0].toInt() - 1..range[1].toInt() - 1).toList()
            }
        }

    private fun parts(input: String) = input.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}
