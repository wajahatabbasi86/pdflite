package com.trendoc.pdflite.ui.split

import android.content.Context
import androidx.annotation.StringRes
import com.trendoc.pdflite.R

/** Why a ranges input was rejected: a string resource and its format arguments. */
data class RangeProblem(@StringRes val message: Int, val args: List<Any> = emptyList()) {
    fun resolve(context: Context): String = context.getString(message, *args.toTypedArray())
}

/**
 * Split's "ranges" input — e.g. `1-3, 5, 7-9`, one output file per comma-separated part.
 * Pure so it can be unit tested without a ViewModel or a PDF.
 */
object PageRanges {

    /** The problem with [input] against a document of [pageCount] pages, or null when it is
     * valid (a blank input is "nothing entered yet", not an error). */
    fun validate(input: String, pageCount: Int): RangeProblem? {
        if (input.isBlank()) return null
        val parts = parts(input)
        if (parts.isEmpty()) return RangeProblem(R.string.range_empty)

        for (part in parts) {
            val range = part.split("-").map { it.trim() }
            when (range.size) {
                1 -> {
                    val page = range[0].toIntOrNull()
                        ?: return RangeProblem(R.string.range_bad_page, listOf(part))
                    if (page < 1 || page > pageCount) {
                        return RangeProblem(R.string.range_page_out_of_range, listOf(page, pageCount))
                    }
                }
                2 -> {
                    val start = range[0].toIntOrNull()
                    val end = range[1].toIntOrNull()
                    if (start == null || end == null) return RangeProblem(R.string.range_bad_range, listOf(part))
                    if (start < 1 || end > pageCount || start > end) {
                        return RangeProblem(R.string.range_range_out_of_range, listOf(part, pageCount))
                    }
                }
                else -> return RangeProblem(R.string.range_bad_range, listOf(part))
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
