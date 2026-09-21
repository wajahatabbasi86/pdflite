package com.trendoc.pdflite.ui.donate

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trendoc.pdflite.billing.DonationRepository
import com.trendoc.pdflite.billing.DonationUiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Backs the "Support TrenDoc" donation screen. Owns the [DonationRepository]'s connection
 * for exactly this screen's lifetime — opened in [init], closed in [onCleared] — same
 * pattern as [com.trendoc.pdflite.ui.billing.BillingViewModel]. */
class DonationViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DonationRepository(application)

    val uiState: StateFlow<DonationUiState> = repository.uiState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DonationUiState()
    )

    init {
        repository.start()
    }

    fun donate(activity: Activity, productId: String) = repository.donate(activity, productId)
    fun clearError() = repository.clearError()

    override fun onCleared() {
        super.onCleared()
        repository.close()
    }
}
