package com.trendoc.pdflite.recents

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.recentsDataStore by preferencesDataStore(name = "recents_preferences")

/** One file this app has opened or produced. [sourceLabel] is which screen touched it
 * last (e.g. "View PDF", "Compress") — shown as a small tag in the Recents list. */
data class RecentEntry(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val pageCount: Int,
    val timestampMillis: Long,
    val sourceLabel: String
)

/**
 * Tracks files this app itself has opened (View PDF) or produced (Merge/Split/Compress/
 * Image<->PDF/Fill Forms save) — deliberately not a device-wide storage scan, which would
 * need broad storage permissions this app has no other reason to request. Shown in Home's
 * "On-Device Recents" strip and the Recents tab.
 *
 * Persisted as a small JSON array in DataStore rather than a database — a capped, ordered
 * list of ~20 entries doesn't need Room's query surface. Re-touching an already-listed uri
 * moves it to the top instead of duplicating it.
 */
class RecentsRepository(private val context: Context) {
    private val key = stringPreferencesKey("recents_json")

    val recents: Flow<List<RecentEntry>> = context.recentsDataStore.data.map { prefs ->
        parse(prefs[key])
    }

    suspend fun record(
        uri: Uri,
        displayName: String,
        sizeBytes: Long,
        pageCount: Int,
        sourceLabel: String
    ) {
        // Without this, the one-time SAF read grant a picker/save dialog hands back only
        // lasts for the app's current process — reopening this same entry from Recents
        // after the app has been killed and restarted throws SecurityException. Some
        // providers (e.g. a freshly-created SAF document) don't support a persistable
        // grant at all, which throws too — either way, this entry just won't survive a
        // process restart, which is no worse than not recording it.
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
        }
        context.recentsDataStore.edit { prefs ->
            val current = parse(prefs[key]).toMutableList()
            current.removeAll { it.uri == uri }
            current.add(
                0,
                RecentEntry(uri, displayName, sizeBytes, pageCount, System.currentTimeMillis(), sourceLabel)
            )
            prefs[key] = serialize(current.take(MAX_ENTRIES))
        }
    }

    suspend fun remove(uri: Uri) {
        context.recentsDataStore.edit { prefs ->
            val current = parse(prefs[key]).filterNot { it.uri == uri }
            prefs[key] = serialize(current)
        }
    }

    suspend fun clear() {
        context.recentsDataStore.edit { prefs -> prefs[key] = "[]" }
    }

    private fun parse(raw: String?): List<RecentEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                RecentEntry(
                    uri = Uri.parse(obj.getString("uri")),
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

    private fun serialize(entries: List<RecentEntry>): String {
        val array = JSONArray()
        entries.forEach { entry ->
            val obj = JSONObject()
            obj.put("uri", entry.uri.toString())
            obj.put("name", entry.displayName)
            obj.put("size", entry.sizeBytes)
            obj.put("pages", entry.pageCount)
            obj.put("ts", entry.timestampMillis)
            obj.put("source", entry.sourceLabel)
            array.put(obj)
        }
        return array.toString()
    }

    companion object {
        private const val MAX_ENTRIES = 20
    }
}
