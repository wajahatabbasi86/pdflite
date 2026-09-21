package com.trendoc.pdflite.ui.billing

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trendoc.pdflite.ui.common.GradientButton
import com.trendoc.pdflite.ui.donate.DonationSection

/**
 * The "Remove Ads" screen, reached from Home's top app bar. Two ways to clear the banner —
 * both time-limited rather than a forever unlock: watch a rewarded video for a couple of free
 * hours, or pay for a full day — plus the optional donation tiers underneath, which don't
 * touch ad state at all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillingScreen(onDone: () -> Unit, viewModel: BillingViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val remainingMillis by viewModel.remainingAdFreeMillis.collectAsState()
    val rewardedAdReady by viewModel.rewardedAdReady.collectAsState()
    val context = LocalContext.current

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
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (remainingMillis > 0) {
                ActiveWindowCard(remainingMillis)
            }

            Text(
                "One small banner, only ever on-screen — clear it for a while for free, or pay " +
                    "to clear it for longer. No subscription, and nothing else in the app changes.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            when {
                uiState.billingUnavailable -> BillingUnavailableCard()
                uiState.isConnecting -> LoadingIndicator()
                else -> {
                    OptionCard(
                        title = "Watch a video",
                        subtitle = "2 hours ad-free, free",
                        buttonText = if (rewardedAdReady) "Watch" else "Loading…",
                        enabled = rewardedAdReady,
                        onClick = { (context as? Activity)?.let(viewModel::watchRewardedAd) }
                    )

                    GradientButton(
                        text = if (uiState.isPurchasing) {
                            "Processing…"
                        } else {
                            "Buy — 1 day ad-free${uiState.priceText?.let { " ($it)" } ?: ""}"
                        },
                        onClick = { (context as? Activity)?.let(viewModel::buy) },
                        enabled = !uiState.isPurchasing && uiState.priceText != null
                    )

                    if (uiState.justGranted) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Text(
                                "Ads removed for the next day — thank you!",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
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

            HorizontalDivider()

            DonationSection()
        }
    }
}

@Composable
private fun ActiveWindowCard(remainingMillis: Long) {
    val totalMinutes = remainingMillis / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val text = when {
        hours > 0 -> "Ad-free for ${hours}h ${minutes}m"
        else -> "Ad-free for ${minutes}m"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Text(
            text,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun OptionCard(title: String, subtitle: String, buttonText: String, enabled: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text(buttonText)
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
                "Make sure you're signed in to the Play Store and connected to the internet, then come back to this screen. The ad banner stays visible until a purchase can complete.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
