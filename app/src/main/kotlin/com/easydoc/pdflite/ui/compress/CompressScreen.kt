package com.easydoc.pdflite.ui.compress

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.easydoc.pdflite.ui.common.GradientButton
import com.easydoc.pdflite.ui.common.ResultScreen
import com.easydoc.pdflite.util.SafFileUtils
import kotlin.math.roundToInt

/** Compress PDF, per docs/REQUIREMENTS.md §5. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompressScreen(
    onDone: () -> Unit,
    viewModel: CompressViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val pickFileLauncher = rememberLauncherForActivityResult(
        contract = SafFileUtils.openSingleDocument
    ) { uri -> viewModel.onDocumentPicked(uri) }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = SafFileUtils.createDocument
    ) { destination -> viewModel.onSaveLocationChosen(destination) }

    LaunchedEffect(uiState.readyToSave) {
        if (uiState.readyToSave) {
            saveLauncher.launch(uiState.defaultSaveName)
        }
    }

    val savedUri = uiState.savedResultUri
    if (savedUri != null) {
        val original = uiState.originalSizeBytes
        val compressed = uiState.compressedSizeBytes
        val subtitle = if (original > 0 && compressed >= 0) {
            val pct = ((1.0 - compressed.toDouble() / original) * 100).roundToInt()
            "${formatSize(original)} → ${formatSize(compressed)} · ${if (pct > 0) "−$pct%" else "no size reduction"}"
        } else null
        ResultScreen(
            fileName = uiState.savedFileName ?: uiState.defaultSaveName,
            resultUri = savedUri,
            subtitle = subtitle,
            onDone = onDone
        )
        return
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Compress PDF") }) }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (uiState.fileName == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Button(onClick = { pickFileLauncher.launch(arrayOf("application/pdf")) }) {
                        Text("Select PDF")
                    }
                }
            } else {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(uiState.fileName ?: "", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (uiState.originalSizeBytes >= 0) formatSize(uiState.originalSizeBytes) else "—",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                uiState.errorMessage?.let { message ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(message)
                            Button(onClick = { viewModel.clearError() }) { Text("Try Again") }
                        }
                    }
                }

                if (uiState.isLoadingFile) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                }

                Text(
                    "COMPRESSION LEVEL",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    CompressionLevel.entries.forEachIndexed { index, level ->
                        SegmentedButton(
                            selected = level == uiState.level,
                            onClick = { viewModel.setLevel(level) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = CompressionLevel.entries.size)
                        ) { Text(level.label) }
                    }
                }

                Box(modifier = Modifier.weight(1f))

                if (uiState.isCompressing) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    GradientButton(
                        text = "Compress",
                        onClick = { viewModel.startCompress() },
                        enabled = uiState.canCompress
                    )
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes < 0) return "—"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 0.1) "%.1f MB".format(mb) else "${bytes / 1024} KB"
}
