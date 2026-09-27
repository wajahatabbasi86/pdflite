package com.trendoc.pdflite.ui.recents

import com.trendoc.pdflite.di.appContainer
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

class RecentsViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: RecentsRepository = application.appContainer.recents
) : AndroidViewModel(application) {

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
