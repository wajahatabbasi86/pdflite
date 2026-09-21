package com.trendoc.pdflite.ui.donate

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trendoc.pdflite.billing.DonationTier

/**
 * "Support TrenDoc" — a purely optional donation section with three fixed Play Billing tiers
 * (Play doesn't support an arbitrary/open amount on a single product), embedded at the bottom
 * of the Remove Ads screen rather than as its own destination — goodwill sitting alongside the
 * other two ways of dealing with the banner, not a purchase that changes anything the app does.
 */
@Composable
fun DonationSection(viewModel: DonationViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Support TrenDoc", style = MaterialTheme.typography.titleMedium)
        Text(
            "Nothing here is paywalled. If the app's been useful, a one-time donation helps " +
                "keep it that way — entirely optional, and it doesn't unlock or change anything.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (uiState.thankYouVisible) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Text(
                    "Thank you!",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        when {
            uiState.billingUnavailable -> BillingUnavailableCard()
            uiState.isConnecting -> LoadingIndicator()
            else -> {
                uiState.tiers.forEach { tier ->
                    DonationTierCard(
                        tier = tier,
                        isPurchasing = uiState.purchasingProductId == tier.productId,
                        onDonate = {
                            (context as? Activity)?.let { activity -> viewModel.donate(activity, tier.productId) }
                        }
                    )
                }
            }
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
    }
}

@Composable
private fun DonationTierCard(tier: DonationTier, isPurchasing: Boolean, onDonate: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(tier.label, style = MaterialTheme.typography.titleMedium)
                tier.priceText?.let { price ->
                    Text(price, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Button(onClick = onDonate, enabled = !isPurchasing && tier.priceText != null) {
                Text(if (isPurchasing) "Processing…" else "Donate")
            }
        }
    }
}

@Composable
private fun LoadingIndicator() {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun BillingUnavailableCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Billing isn't available right now", style = MaterialTheme.typography.titleSmall)
            Text(
                "Make sure you're signed in to the Play Store and connected to the internet, then come back to this screen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
