package com.easydoc.pdflite.ui.pdftoimage

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.easydoc.pdflite.ui.common.FileIconAvatar
import com.easydoc.pdflite.ui.common.ResultScreen
import com.easydoc.pdflite.util.SafFileUtils

/** PDF -> Image(s), per docs/REQUIREMENTS.md §6.2: format + quality + optional page range,
 * rendered via PdfRenderer, saved as individual files to a chosen directory. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToImageScreen(
    onDone: () -> Unit,
    viewModel: PdfToImageViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val pickFileLauncher = rememberLauncherForActivityResult(
        contract = SafFileUtils.openSingleDocument
    ) { uri -> viewModel.onDocumentPicked(uri) }

    val pickDirLauncher = rememberLauncherForActivityResult(
        contract = SafFileUtils.openDocumentTree
    ) { uri -> viewModel.startConvert(uri) }

    if (uiState.savedResultUris.isNotEmpty()) {
        ResultScreen(
            fileName = "${uiState.savedResultUris.size} image${if (uiState.savedResultUris.size == 1) "" else "s"}",
            resultUri = uiState.savedResultUris.first(),
            mimeType = uiState.format.mimeType,
            subtitle = "${uiState.savedResultUris.size} files saved",
            onDone = onDone
        )
        return
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("PDF → Image(s)") }) }
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
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FileIconAvatar()
                        Column {
                            Text(uiState.fileName ?: "", style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            Text(
                                "${uiState.pageCount} page${if (uiState.pageCount == 1) "" else "s"} total",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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

                if (uiState.pageCount > 0) {
                    Text("FORMAT", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ImageFormat.entries.forEachIndexed { index, format ->
                            SegmentedButton(
                                selected = format == uiState.format,
                                onClick = { viewModel.setFormat(format) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = ImageFormat.entries.size)
                            ) { Text(format.label) }
                        }
                    }

                    Text("QUALITY", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ImageQuality.entries.forEachIndexed { index, quality ->
                            SegmentedButton(
                                selected = quality == uiState.quality,
                                onClick = { viewModel.setQuality(quality) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = ImageQuality.entries.size)
                            ) { Text(quality.label) }
                        }
                    }

                    OutlinedTextField(
                        value = uiState.rangeInput,
                        onValueChange = { viewModel.onRangeInputChanged(it) },
                        label = { Text("Pages (blank = all), e.g. 1-3, 5") },
                        isError = uiState.rangeError != null,
                        supportingText = { uiState.rangeError?.let { Text(it) } },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Box(modifier = Modifier.weight(1f))

                    if (uiState.isConverting) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            CircularProgressIndicator()
                            Text(
                                "Converting page ${uiState.convertedCount} of ${uiState.pageCount}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        com.easydoc.pdflite.ui.common.GradientButton(
                            text = "Convert",
                            onClick = { pickDirLauncher.launch(null) },
                            enabled = uiState.canConvert
                        )
                    }
                }
            }
        }
    }
}
