package com.easydoc.pdflite.ui.common

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(imageVector = Icons.Filled.Done, contentDescription = null)
            Text(fileName)
            subtitle?.let { Text(it) }

            Button(onClick = {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(resultUri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, null))
            }) {
                Text("Open")
            }

            OutlinedButton(onClick = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, resultUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share"))
            }) {
                Text("Share")
            }

            OutlinedButton(onClick = onDone) {
                Text("Done")
            }
        }
    }
}
