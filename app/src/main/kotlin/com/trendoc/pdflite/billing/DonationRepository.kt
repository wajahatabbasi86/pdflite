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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The three one-time donation products — must be created in Play Console's Monetization >
 * Products > In-app products with these exact IDs before purchases can succeed on a real
 * listing, same as [REMOVE_ADS_PRODUCT_ID]. */
val DONATION_PRODUCT_IDS = listOf("donate_small", "donate_medium", "donate_large")

private fun tierLabel(productId: String) = when (productId) {
    "donate_small" -> "Small"
    "donate_medium" -> "Medium"
    "donate_large" -> "Generous"
    else -> productId
}

data class DonationTier(val productId: String, val label: String, val priceText: String?)

data class DonationUiState(
    val isConnecting: Boolean = true,
    val tiers: List<DonationTier> = DONATION_PRODUCT_IDS.map { DonationTier(it, tierLabel(it), null) },
    val purchasingProductId: String? = null,
    val thankYouVisible: Boolean = false,
    val billingUnavailable: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Wraps Play Billing for the three donation tiers — kept separate from [BillingRepository]'s
 * single non-consumable "Remove Ads" product because a donation is consumable rather than an
 * entitlement: it's acknowledged by being consumed immediately after purchase, so the same
 * tier can be bought again later. Nothing in the app checks or is gated by whether a donation
 * was ever made — this exists purely as an optional way to support development, per the
 * app's own "nothing is paywalled" positioning.
 */
class DonationRepository(context: Context) {
    private val appContext = context.applicationContext

    private val _uiState = MutableStateFlow(DonationUiState())
    val uiState: StateFlow<DonationUiState> = _uiState.asStateFlow()

    private var productDetailsById: Map<String, ProductDetails> = emptyMap()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { result, purchases ->
        when {
            result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null ->
                purchases.forEach { handlePurchase(it) }
            result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED ->
                _uiState.update { it.copy(purchasingProductId = null) }
            else ->
                _uiState.update {
                    it.copy(purchasingProductId = null, errorMessage = "Purchase couldn't be completed. Please try again.")
                }
        }
    }

    private val billingClient = BillingClient.newBuilder(appContext)
        .setListener(purchasesUpdatedListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

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
                // Same as BillingRepository: the library retries internally, so only a
                // user-triggered donate() against a disconnected client reports an error.
            }
        })
    }

    private fun loadProductDetails() {
        val products = DONATION_PRODUCT_IDS.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder().setProductList(products).build()

        billingClient.queryProductDetailsAsync(params) { result, detailsList ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK && detailsList.isNotEmpty()) {
                productDetailsById = detailsList.associateBy { it.productId }
                _uiState.update { state ->
                    state.copy(
                        isConnecting = false,
                        tiers = DONATION_PRODUCT_IDS.map { id ->
                            DonationTier(
                                productId = id,
                                label = tierLabel(id),
                                priceText = productDetailsById[id]?.oneTimePurchaseOfferDetails?.formattedPrice
                            )
                        }
                    )
                }
            } else {
                _uiState.update { it.copy(isConnecting = false, billingUnavailable = true) }
            }
        }
    }

    fun donate(activity: Activity, productId: String) {
        val details = productDetailsById[productId]
        if (details == null) {
            _uiState.update { it.copy(billingUnavailable = true) }
            return
        }
        _uiState.update { it.copy(purchasingProductId = productId, errorMessage = null, thankYouVisible = false) }
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
        if (purchase.products.none { it in DONATION_PRODUCT_IDS }) return

        val consumeParams = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.consumeAsync(consumeParams) { _, _ ->
            _uiState.update { it.copy(purchasingProductId = null, thankYouVisible = true) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun close() {
        billingClient.endConnection()
    }
}
