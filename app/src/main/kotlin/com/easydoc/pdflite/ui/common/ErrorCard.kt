package com.easydoc.pdflite.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The plain-language error banner every tool screen shows below its content when
 * `uiState.errorMessage` is non-null (§1.4) — a message plus a "Try Again" action that clears
 * it. Was previously copy-pasted with tiny drifts across Merge/Split/Compress/Image<->PDF/View
 * PDF/the reference Picker screen (one of them even used `fillMaxSize` instead of
 * `fillMaxWidth`, stretching the card to the full screen height); centralizing it here keeps
 * every tool's error state looking and behaving identically.
 */
@Composable
fun ErrorCard(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(message)
            Button(onClick = onRetry) {
                Text("Try Again")
            }
        }
    }
}
