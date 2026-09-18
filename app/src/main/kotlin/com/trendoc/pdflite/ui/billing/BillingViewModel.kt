package com.trendoc.pdflite.ui.billing

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trendoc.pdflite.billing.BillingRepository
import com.trendoc.pdflite.billing.BillingUiState
import com.trendoc.pdflite.billing.EntitlementRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Backs the "Remove Ads" screen (docs/REQUIREMENTS.md §7). Owns the [BillingRepository]'s
 * connection for exactly this screen's lifetime — opened in [init], closed in [onCleared]. */
class BillingViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BillingRepository(application, EntitlementRepository(application))

    val uiState: StateFlow<BillingUiState> = repository.uiState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BillingUiState()
    )

    init {
        repository.start()
    }

    fun buy(activity: Activity) = repository.launchPurchaseFlow(activity)
    fun restorePurchases() = repository.queryExistingPurchases(showLoadingOnEmpty = true)
    fun clearError() = repository.clearError()

    override fun onCleared() {
        super.onCleared()
        repository.close()
    }
}
