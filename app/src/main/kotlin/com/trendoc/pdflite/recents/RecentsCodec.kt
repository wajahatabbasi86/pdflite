package com.trendoc.pdflite.recents

import org.json.JSONArray
import org.json.JSONObject

/**
 * [RecentEntry] as stored, with the Uri kept as its string. Keeping the codec free of
 * `android.net.Uri` lets it run in plain JVM unit tests.
 */
data class StoredRecent(
    val uri: String,
    val displayName: String,
    val sizeBytes: Long,
    val pageCount: Int,
    val timestampMillis: Long,
    val sourceLabel: String
)

/** The Recents list's storage format and its ordering rules. */
object RecentsCodec {

    const val MAX_ENTRIES = 20

    /** Unreadable or missing data is an empty list, never a crash — a corrupt preference
     * file must not stop the app opening. */
    fun decode(raw: String?): List<StoredRecent> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                StoredRecent(
                    uri = obj.getString("uri"),
                    displayName = obj.getString("name"),
                    sizeBytes = obj.getLong("size"),
                    pageCount = obj.getInt("pages"),
                    timestampMillis = obj.getLong("ts"),
                    sourceLabel = obj.optString("source", "")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun encode(entries: List<StoredRecent>): String {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("uri", entry.uri)
                    .put("name", entry.displayName)
                    .put("size", entry.sizeBytes)
                    .put("pages", entry.pageCount)
                    .put("ts", entry.timestampMillis)
                    .put("source", entry.sourceLabel)
            )
        }
        return array.toString()
    }

    /** Puts [entry] first, dropping any older entry for the same Uri (re-touching a file moves
     * it to the top rather than duplicating it), and keeps at most [MAX_ENTRIES]. */
    fun upsert(current: List<StoredRecent>, entry: StoredRecent): List<StoredRecent> =
        (listOf(entry) + current.filterNot { it.uri == entry.uri }).take(MAX_ENTRIES)
}
