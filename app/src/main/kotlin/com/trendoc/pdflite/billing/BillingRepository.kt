package com.trendoc.pdflite.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The "Remove Ads" product — must be created in Play Console's Monetization > Products >
 * In-app products with this exact ID before purchases can succeed on a real listing (see
 * docs/REQUIREMENTS.md §7). Consumable rather than a one-time forever unlock: each purchase
 * grants [PAID_REMOVAL_DURATION_MILLIS] of ad-free time and can be bought again once that
 * window runs out, the same shape as the rewarded-video path in [RewardedAdRepository]. */
const val REMOVE_ADS_PRODUCT_ID = "remove_ads"

/** UI-facing state for the Remove Ads screen. [priceText] is only populated once
 * [BillingClient.ProductDetails] has actually been fetched from Play — never hardcoded. */
data class BillingUiState(
    val isConnecting: Boolean = true,
    val priceText: String? = null,
    val isPurchasing: Boolean = false,
    val justGranted: Boolean = false,
    val billingUnavailable: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Wraps the Play Billing Library for the single "Remove Ads" consumable product. Owned by
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

    /** Opens the connection, then loads the product's live price. No purchase-history query on
     * start — a consumable has nothing standing to restore once its window has been consumed,
     * unlike the old non-consumable model. */
    fun start() {
        _uiState.update { it.copy(isConnecting = true, billingUnavailable = false, errorMessage = null) }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    loadProductDetails()
                } else {
                    _uiState.update { it.copy(isConnecting = false, billingUnavailable = true) }
                }
            }

            override fun onBillingServiceDisconnected() {
                // The library retries reconnection internally on the next call; surfacing this
                // here would just flash an error during a transient drop, so only a
                // user-triggered buy() against a disconnected client reports an error.
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

        // Play Billing 8 changed this callback's second argument from a plain
        // List<ProductDetails> to a QueryProductDetailsResult, which also carries the
        // products Play couldn't fetch. Only the fetched list matters here — an unfetched
        // "remove_ads" falls through to the same billingUnavailable state as any other
        // failure to load a price.
        billingClient.queryProductDetailsAsync(params) { result, queryResult ->
            val details = queryResult.productDetailsList.firstOrNull()
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

    fun launchPurchaseFlow(activity: Activity) {
        val details = productDetails
        if (details == null) {
            _uiState.update { it.copy(billingUnavailable = true) }
            return
        }
        _uiState.update { it.copy(isPurchasing = true, errorMessage = null, justGranted = false) }
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
        if (!purchase.products.contains(REMOVE_ADS_PRODUCT_ID)) return

        scope.launch { entitlementRepository.grantAdFreeFor(PAID_REMOVAL_DURATION_MILLIS) }
        _uiState.update { it.copy(isPurchasing = false, justGranted = true, errorMessage = null) }

        // Consumable, not acknowledge-only: consuming immediately is what lets the same
        // product be bought again once this window runs out.
        val consumeParams = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.consumeAsync(consumeParams) { _, _ -> /* entitlement already granted above
            regardless of the consume call's own result */ }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun close() {
        billingClient.endConnection()
    }
}

/** How long a single paid "Remove Ads" purchase removes the banner for. */
const val PAID_REMOVAL_DURATION_MILLIS = 24L * 60 * 60 * 1000 // 1 day
