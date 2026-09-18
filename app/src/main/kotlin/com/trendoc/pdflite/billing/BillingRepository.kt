package com.trendoc.pdflite.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The one-time, non-consumable "Remove Ads" product — must be created in Play Console's
 * Monetization > Products > In-app products with this exact ID before purchases can succeed
 * on a real listing (see docs/REQUIREMENTS.md §7). */
const val REMOVE_ADS_PRODUCT_ID = "remove_ads"

/** UI-facing state for the Billing screen. [priceText] is only populated once
 * [BillingClient.ProductDetails] has actually been fetched from Play, per §7 ("price...
 * fetched live from Play Billing") — never hardcoded. */
data class BillingUiState(
    val isConnecting: Boolean = true,
    val priceText: String? = null,
    val isPurchased: Boolean = false,
    val isPurchasing: Boolean = false,
    val billingUnavailable: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Wraps the Play Billing Library for the single "Remove Ads" non-consumable product. Owned by
 * [com.trendoc.pdflite.ui.billing.BillingViewModel], which forwards its own [viewModelScope]
 * lifetime via [start]/[close].
 *
 * Per docs/REQUIREMENTS.md §7's edge case ("Billing unavailable... show a plain message, don't
 * crash; ads remain shown until purchase can complete"), every entry point into the client
 * degrades to [BillingUiState.billingUnavailable] rather than throwing.
 */
class BillingRepository(
    context: Context,
    private val entitlementRepository: EntitlementRepository
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _uiState = MutableStateFlow(BillingUiState())
    val uiState: StateFlow<BillingUiState> = _uiState.asStateFlow()

    private var productDetails: ProductDetails? = null

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        when {
            result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null ->
                purchases.forEach { handlePurchase(it) }
            result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED ->
                _uiState.update { it.copy(isPurchasing = false) }
            else ->
                _uiState.update {
                    it.copy(isPurchasing = false, errorMessage = "Purchase couldn't be completed. Please try again.")
                }
        }
    }

    private val billingClient = BillingClient.newBuilder(appContext)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    /** Opens the connection, then loads the product's live price and any existing purchase. */
    fun start() {
        _uiState.update { it.copy(isConnecting = true, billingUnavailable = false, errorMessage = null) }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    loadProductDetails()
                    queryExistingPurchases(showLoadingOnEmpty = false)
                } else {
                    _uiState.update {
                        it.copy(isConnecting = false, billingUnavailable = true)
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                // The library retries reconnection internally on the next call; surfacing this
                // here would just flash an error during a transient drop, so only user-triggered
                // actions (buy/restore) that fail against a disconnected client report an error.
            }
        })
    }

    private fun loadProductDetails() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(REMOVE_ADS_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()

        billingClient.queryProductDetailsAsync(params) { result, detailsList ->
            val details = detailsList.firstOrNull()
            if (result.responseCode == BillingClient.BillingResponseCode.OK && details != null) {
                productDetails = details
                _uiState.update {
                    it.copy(
                        isConnecting = false,
                        priceText = details.oneTimePurchaseOfferDetails?.formattedPrice
                    )
                }
            } else {
                _uiState.update { it.copy(isConnecting = false, billingUnavailable = true) }
            }
        }
    }

    /** Re-queries Play's purchase history — the "Restore Purchases" action (§7 point 4), also
     * run once silently on [start] so a reinstall or new device picks up an existing purchase
     * without the user needing to tap anything. */
    fun queryExistingPurchases(showLoadingOnEmpty: Boolean = true) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                if (showLoadingOnEmpty) {
                    _uiState.update { it.copy(errorMessage = "Couldn't check past purchases. Please try again.") }
                }
                return@queryPurchasesAsync
            }
            val owned = purchases.any { purchase ->
                purchase.products.contains(REMOVE_ADS_PRODUCT_ID) &&
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED
            }
            if (owned) {
                purchases.forEach { handlePurchase(it) }
            } else if (showLoadingOnEmpty) {
                _uiState.update { it.copy(errorMessage = "No previous purchase found for this account.") }
            }
        }
    }

    fun launchPurchaseFlow(activity: Activity) {
        val details = productDetails
        if (details == null) {
            _uiState.update { it.copy(billingUnavailable = true) }
            return
        }
        _uiState.update { it.copy(isPurchasing = true, errorMessage = null) }
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
        billingClient.launchBillingFlow(activity, flowParams)
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        scope.launch { entitlementRepository.setAdsRemoved(true) }
        _uiState.update { it.copy(isPurchasing = false, isPurchased = true, errorMessage = null) }

        // Play requires every purchase to be acknowledged within 3 days or it's auto-refunded —
        // this is a non-consumable "own it forever" product, so acknowledge rather than consume.
        if (!purchase.isAcknowledged) {
            val ackParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(ackParams) { /* best-effort; entitlement is
                already persisted locally above regardless of ack result */ }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun close() {
        billingClient.endConnection()
    }
}
