package com.trendoc.pdflite.ui.billing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trendoc.pdflite.ui.common.GradientButton
import kotlinx.coroutines.delay

/**
 * The "Remove Ads" purchase screen, reached from Home's top app bar (§7). A simple, single
 * screen rather than a modal per the requirement's own wording ("Simple screen/dialog") — kept
 * as a screen so it fits the app's flat one-tap-there-one-tap-back navigation rule (§1.3)
 * instead of introducing a dialog pattern used nowhere else in the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillingScreen(onDone: () -> Unit, viewModel: BillingViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Purchases complete on their own (no explicit "close" tap in §7's flow: "remove banner
    // immediately, dismiss screen") — pop back a moment after success so the user briefly
    // sees the confirmation instead of the screen vanishing instantly underneath their tap.
    LaunchedEffect(uiState.isPurchased) {
        if (uiState.isPurchased) {
            delay(900)
            onDone()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Remove Ads") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when {
                uiState.isPurchased -> PurchasedState()
                uiState.billingUnavailable -> BillingUnavailableState()
                uiState.isConnecting -> LoadingState()
                else -> OfferState(
                    priceText = uiState.priceText,
                    isPurchasing = uiState.isPurchasing,
                    onBuy = { (context as? android.app.Activity)?.let(viewModel::buy) }
                )
            }

            uiState.errorMessage?.let { message ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        message,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            if (!uiState.isPurchased && !uiState.billingUnavailable) {
                TextButton(onClick = { viewModel.restorePurchases() }) {
                    Text("Restore Purchases")
                }
            }
        }
    }
}

@Composable
private fun OfferState(priceText: String?, isPurchasing: Boolean, onBuy: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Remove the banner ad", style = MaterialTheme.typography.titleMedium)
            Text(
                "One-time purchase, no subscription. Every tool keeps working exactly the same — this just removes the ad banner from Home.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (priceText != null) {
                Text(
                    priceText,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    GradientButton(
        text = if (isPurchasing) "Processing…" else "Buy",
        onClick = onBuy,
        enabled = !isPurchasing && priceText != null
    )
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun PurchasedState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(64.dp).background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                CircleShape
            ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        }
        Text("Ads removed — thank you!", style = MaterialTheme.typography.titleMedium)
    }
}


@Composable
private fun BillingUnavailableState() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Billing isn't available right now", style = MaterialTheme.typography.titleSmall)
            Text(
                "Make sure you're signed in to the Play Store and connected to the internet, then come back to this screen. The ad banner stays visible until the purchase can complete.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
