package com.trendoc.pdflite.ui.files

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Context.filesDataStore by preferencesDataStore(name = "files_browser_preferences")

data class FileEntry(
    val uri: Uri,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long
)

data class FilesUiState(
    val rootUri: Uri? = null,
    /** Stack of directory Uris from root to the currently viewed folder — the last entry
     * is what's shown; popping it navigates up one level. */
    val pathStack: List<Uri> = emptyList(),
    val entries: List<FileEntry> = emptyList(),
    val isLoading: Boolean = false
) {
    val canGoUp: Boolean get() = pathStack.isNotEmpty()
}

/**
 * A minimal SAF-backed folder browser — the "Files" bottom-nav tab. The user grants access
 * to one folder tree (e.g. Downloads) via the system picker; TrenDoc remembers it (a
 * persisted Uri permission) so they don't have to re-grant it every launch. This is
 * read-only browsing to find and open a PDF, not a general file manager (no rename/move/
 * delete) — opening a PDF here hands it to View PDF, same as any other entry point.
 */
class FilesViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(FilesUiState())
    val uiState: StateFlow<FilesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = loadSavedRoot()
            if (saved != null) {
                _uiState.update { it.copy(rootUri = saved) }
                loadEntries(saved)
            }
        }
    }

    private suspend fun loadSavedRoot(): Uri? {
        val context = getApplication<Application>()
        val prefs = context.filesDataStore.data.first()
        val raw = prefs[ROOT_KEY] ?: return null
        val uri = Uri.parse(raw)
        val stillGranted = context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
        return if (stillGranted) uri else null
    }

    fun onRootPicked(uri: Uri?) {
        if (uri == null) return
        val context = getApplication<Application>()
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        viewModelScope.launch {
            context.filesDataStore.edit { it[ROOT_KEY] = uri.toString() }
        }
        _uiState.value = FilesUiState(rootUri = uri)
        loadEntries(uri)
    }

    fun openFolder(uri: Uri) {
        _uiState.update { it.copy(pathStack = it.pathStack + uri) }
        loadEntries(uri)
    }

    fun goUp() {
        val state = _uiState.value
        if (!state.canGoUp) return
        val newStack = state.pathStack.dropLast(1)
        val target = newStack.lastOrNull() ?: state.rootUri ?: return
        _uiState.update { it.copy(pathStack = newStack) }
        loadEntries(target)
    }

    fun changeRoot() {
        _uiState.value = FilesUiState()
    }

    private fun loadEntries(dirUri: Uri) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val context = getApplication<Application>()
            val entries = withContext(Dispatchers.IO) {
                try {
                    val dir = DocumentFile.fromTreeUri(context, dirUri)
                    dir?.listFiles()
                        ?.filter { it.isDirectory || (it.name?.endsWith(".pdf", ignoreCase = true) == true) }
                        ?.map { FileEntry(it.uri, it.name ?: "unnamed", it.isDirectory, it.length()) }
                        ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                        ?: emptyList()
                } catch (e: SecurityException) {
                    // The granted tree's permission was revoked or never persisted (e.g. the
                    // OS reclaimed it after a factory reset or storage change) — an empty
                    // folder is the honest result, not a crash.
                    emptyList()
                }
            }
            _uiState.update { it.copy(entries = entries, isLoading = false) }
        }
    }

    companion object {
        private val ROOT_KEY = stringPreferencesKey("root_tree_uri")
    }
}
