package com.trendoc.pdflite.recents

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
            val entry = StoredRecent(
                uri.toString(), displayName, sizeBytes, pageCount, System.currentTimeMillis(), sourceLabel
            )
            prefs[key] = RecentsCodec.encode(RecentsCodec.upsert(RecentsCodec.decode(prefs[key]), entry))
        }
    }

    suspend fun remove(uri: Uri) {
        context.recentsDataStore.edit { prefs ->
            val current = RecentsCodec.decode(prefs[key]).filterNot { it.uri == uri.toString() }
            prefs[key] = RecentsCodec.encode(current)
        }
    }

    suspend fun clear() {
        context.recentsDataStore.edit { prefs -> prefs[key] = "[]" }
    }

    private fun parse(raw: String?): List<RecentEntry> =
        RecentsCodec.decode(raw).map {
            RecentEntry(Uri.parse(it.uri), it.displayName, it.sizeBytes, it.pageCount, it.timestampMillis, it.sourceLabel)
        }
}
