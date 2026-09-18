package com.trendoc.pdflite.ui.common

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** The same "approved" green used across the app for success states (compression results,
 * this screen's check mark) — kept separate from the user's accent color, which drives
 * primary actions, not confirmations. */
private val ApprovedGreen = Color(0xFF2F8F62)

/**
 * Shared "success" screen per docs/REQUIREMENTS.md §1.2: output filename, Open, Share,
 * and Done (return to Home) actions. Every tool (Merge, Split, Compress, Convert) reuses
 * this instead of five near-identical result screens.
 *
 * @param resultUri content Uri of the produced file (as returned by ACTION_CREATE_DOCUMENT).
 * @param mimeType MIME type used for the Open/Share intents (e.g. "application/pdf").
 * @param subtitle optional extra line under the filename (e.g. compression before/after size).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    fileName: String,
    resultUri: Uri,
    mimeType: String = "application/pdf",
    subtitle: String? = null,
    onDone: () -> Unit
) {
    val context = LocalContext.current

    Scaffold(
        topBar = { TopAppBar(title = { Text("Done") }) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(ApprovedGreen.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Done,
                    contentDescription = null,
                    tint = ApprovedGreen,
                    modifier = Modifier.size(36.dp)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(fileName, style = MaterialTheme.typography.titleMedium)
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            GradientButton(
                text = "Open",
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(resultUri, mimeType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, null))
                }
            )

            OutlinedButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = mimeType
                        putExtra(Intent.EXTRA_STREAM, resultUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share"))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Share")
            }

            TextButton(onClick = onDone) {
                Text("Done", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
