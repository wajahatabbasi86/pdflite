package com.trendoc.pdflite.ui.billing

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trendoc.pdflite.billing.BillingRepository
import com.trendoc.pdflite.billing.BillingUiState
import com.trendoc.pdflite.billing.EntitlementRepository
import com.trendoc.pdflite.billing.RewardedAdRepository
import com.trendoc.pdflite.billing.VIDEO_REWARD_DURATION_MILLIS
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Backs the "Remove Ads" screen. Owns both the [BillingRepository] (the paid path) and the
 * [RewardedAdRepository] (the free path) for exactly this screen's lifetime — opened in
 * [init], closed in [onCleared]. */
class BillingViewModel(application: Application) : AndroidViewModel(application) {

    private val entitlementRepository = EntitlementRepository(application)
    private val billingRepository = BillingRepository(application, entitlementRepository)
    private val rewardedAdRepository = RewardedAdRepository(application)

    val uiState: StateFlow<BillingUiState> = billingRepository.uiState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = BillingUiState()
    )

    val remainingAdFreeMillis: StateFlow<Long> = entitlementRepository.remainingMillis.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 0L
    )

    val rewardedAdReady: StateFlow<Boolean> = rewardedAdRepository.isReady.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false
    )

    init {
        billingRepository.start()
        rewardedAdRepository.load()
    }

    fun buy(activity: Activity) = billingRepository.launchPurchaseFlow(activity)
    fun clearError() = billingRepository.clearError()

    fun watchRewardedAd(activity: Activity) {
        rewardedAdRepository.show(activity) {
            viewModelScope.launch { entitlementRepository.grantAdFreeFor(VIDEO_REWARD_DURATION_MILLIS) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        billingRepository.close()
    }
}
