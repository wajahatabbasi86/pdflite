package com.trendoc.pdflite.ui.recents

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trendoc.pdflite.recents.RecentEntry
import com.trendoc.pdflite.recents.RecentsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RecentsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = RecentsRepository(application)

    val entries: StateFlow<List<RecentEntry>> = repository.recents.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun remove(uri: Uri) {
        viewModelScope.launch { repository.remove(uri) }
    }

    fun clearAll() {
        viewModelScope.launch { repository.clear() }
    }
}
