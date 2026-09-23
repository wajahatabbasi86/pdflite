package com.trendoc.pdflite.ui.compress

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import com.trendoc.pdflite.ui.common.ErrorCard
import com.trendoc.pdflite.ui.common.GradientButton
import com.trendoc.pdflite.ui.common.ResultScreen
import com.trendoc.pdflite.util.SafFileUtils
import kotlin.math.roundToInt

/** Compress PDF, per docs/REQUIREMENTS.md §5. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompressScreen(
    onDone: () -> Unit,
    initialUri: android.net.Uri? = null,
    viewModel: CompressViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val pickFileLauncher = rememberLauncherForActivityResult(
        contract = SafFileUtils.openSingleDocument
    ) { uri -> viewModel.onDocumentPicked(uri) }

    // Pre-loaded when arriving from View PDF's "Compress" quick-action bridge.
    LaunchedEffect(initialUri) {
        if (initialUri != null) {
            viewModel.onDocumentPicked(initialUri)
        }
    }

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
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Compress PDF") }
            )
        },
        bottomBar = {
            if (uiState.fileName != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier.navigationBarsPadding().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (uiState.isCompressing) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                CircularProgressIndicator()
                            }
                        } else {
                            GradientButton(
                                text = "Compress (${uiState.level.label})",
                                onClick = { viewModel.startCompress() },
                                enabled = uiState.canCompress
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (uiState.fileName == null && uiState.isLoadingFile) {
                // Pre-loaded from View PDF's "Compress" bridge — analyzing the handed-off
                // file's images, not waiting on a pick. Showing "Select PDF" here
                // (fileName is still null until analysis finishes) would look like the
                // pre-load silently failed.
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.fileName == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Button(onClick = { pickFileLauncher.launch(arrayOf("application/pdf")) }) {
                        Text("Select PDF")
                    }
                }
            } else {
                CompressStatusStrip(sizeLabel = if (uiState.originalSizeBytes >= 0) formatSize(uiState.originalSizeBytes) else "—")

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0x14191C1E))
                ) {
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
                    ErrorCard(message = message, onRetry = { viewModel.clearError() })
                }

                if (uiState.isLoadingFile) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                }

                Text(
                    "Compression Preset",
                    style = MaterialTheme.typography.titleSmall
                )

                CompressionLevel.entries.forEach { level ->
                    CompressionPresetCard(
                        level = level,
                        selected = level == uiState.level,
                        estimatedBytes = uiState.estimatedSizes[level],
                        originalBytes = uiState.originalSizeBytes,
                        onClick = { viewModel.setLevel(level) }
                    )
                }
            }
        }
    }
}

/** One selectable preset row, replacing the earlier segmented-button row with the design
 * reference's vertical radio-card list. The description text is derived from the level's
 * real jpegQuality/downscale fields — never a fabricated size-reduction percentage, since
 * the actual reduction depends on the source file and is only known after compressing. */
@Composable
private fun CompressionPresetCard(
    level: CompressionLevel,
    selected: Boolean,
    estimatedBytes: Long?,
    originalBytes: Long,
    onClick: () -> Unit
) {
    val downscalePct = (level.downscale * 100).roundToInt()
    val qualityPct = (level.jpegQuality * 100).roundToInt()
    val description = if (downscalePct >= 100) {
        "$qualityPct% JPEG quality · original resolution"
    } else {
        "$qualityPct% JPEG quality · downscaled to $downscalePct%"
    }
    val estimateLabel = if (estimatedBytes != null && originalBytes > 0) {
        val pct = ((1.0 - estimatedBytes.toDouble() / originalBytes) * 100).roundToInt()
        if (pct > 0) "~${formatSize(estimatedBytes)} · −$pct%" else "~${formatSize(estimatedBytes)}"
    } else null
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else androidx.compose.ui.graphics.Color(0x14191C1E)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onClick, colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary))
            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                Text(level.label, style = MaterialTheme.typography.titleSmall)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (estimateLabel != null) {
                Text(
                    estimateLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** "100% On-Device" status pill plus the current file's size, echoing the same
 * offline-first framing used on Home/View PDF — purely presentational. */
@Composable
private fun CompressStatusStrip(sizeLabel: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            "100% On-Device",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
        Text(
            sizeLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes < 0) return "—"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 0.1) "%.1f MB".format(mb) else "${bytes / 1024} KB"
}
