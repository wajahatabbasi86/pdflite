package com.trendoc.pdflite.ui.pdftoimage

import androidx.compose.ui.res.pluralStringResource
import com.trendoc.pdflite.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trendoc.pdflite.ui.common.ErrorCard
import com.trendoc.pdflite.ui.common.FileIconAvatar
import com.trendoc.pdflite.ui.common.ResultScreen
import com.trendoc.pdflite.util.SafFileUtils

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

    val pickZipLauncher = rememberLauncherForActivityResult(
        contract = SafFileUtils.createZipDocument
    ) { uri -> viewModel.startConvertZip(uri) }

    if (uiState.savedResultUris.isNotEmpty()) {
        val isZip = uiState.exportAsZip
        ResultScreen(
            fileName = if (isZip) uiState.defaultZipName else pluralStringResource(R.plurals.image_count, uiState.savedResultUris.size, uiState.savedResultUris.size),
            resultUri = uiState.savedResultUris.first(),
            mimeType = if (isZip) "application/zip" else uiState.format.mimeType,
            subtitle = if (isZip) pluralStringResource(R.plurals.pdf_to_image_zipped, uiState.convertedCount, uiState.convertedCount) else pluralStringResource(R.plurals.split_files_saved, uiState.savedResultUris.size, uiState.savedResultUris.size),
            onDone = onDone
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                title = { Text(stringResource(R.string.pdf_to_image_pdf_image_s)) }
            )
        },
        bottomBar = {
            if (uiState.pageCount > 0) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier.navigationBarsPadding().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (uiState.isConverting) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                CircularProgressIndicator()
                                Text(
                                    stringResource(R.string.pdf_to_image_progress, uiState.convertedCount, uiState.pageCount),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            com.trendoc.pdflite.ui.common.GradientButton(
                                text = stringResource(R.string.pdf_to_image_action, pluralStringResource(R.plurals.pdf_to_image_page_count, uiState.pageCount, uiState.pageCount), uiState.format.label),
                                onClick = {
                                    if (uiState.exportAsZip) {
                                        pickZipLauncher.launch(uiState.defaultZipName)
                                    } else {
                                        pickDirLauncher.launch(null)
                                    }
                                },
                                enabled = uiState.canConvert
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
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.fileName == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Button(onClick = { pickFileLauncher.launch(arrayOf("application/pdf")) }) {
                        Text(stringResource(R.string.common_select_pdf))
                    }
                }
            } else {
                PdfToImageStatusStrip(pageCount = uiState.pageCount)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0x14191C1E))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FileIconAvatar()
                        Column {
                            Text(uiState.fileName ?: "", style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            Text(
                                pluralStringResource(R.plurals.page_count_total, uiState.pageCount, uiState.pageCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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

                if (uiState.pageCount > 0) {
                    Text(stringResource(R.string.pdf_to_image_format), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.semantics { heading() })
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ImageFormat.entries.forEachIndexed { index, format ->
                            SegmentedButton(
                                selected = format == uiState.format,
                                onClick = { viewModel.setFormat(format) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = ImageFormat.entries.size)
                            ) { Text(format.label) }
                        }
                    }

                    Text(stringResource(R.string.pdf_to_image_quality), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.semantics { heading() })
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        ImageQuality.entries.forEachIndexed { index, quality ->
                            SegmentedButton(
                                selected = quality == uiState.quality,
                                onClick = { viewModel.setQuality(quality) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = ImageQuality.entries.size)
                            ) { Text(stringResource(quality.label)) }
                        }
                    }

                    OutlinedTextField(
                        value = uiState.rangeInput,
                        onValueChange = { viewModel.onRangeInputChanged(it) },
                        label = { Text(stringResource(R.string.pdf_to_image_pages_blank_all_e_g)) },
                        isError = uiState.rangeError != null,
                        supportingText = { uiState.rangeError?.let { Text(it) } },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // The whole row toggles, so TalkBack reads "Export as ZIP, switch, off"
                    // as one control instead of an unnamed switch beside some text.
                    Row(
                        modifier = Modifier.fillMaxWidth().toggleable(
                            value = uiState.exportAsZip,
                            role = Role.Switch,
                            onValueChange = { viewModel.setExportAsZip(it) }
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.pdf_to_image_export_as_zip), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(R.string.pdf_to_image_zip_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = uiState.exportAsZip, onCheckedChange = null)
                    }
                }
            }
        }
    }
}

/** "100% Offline" status pill plus the loaded document's page count, echoing the same
 * offline-first framing used on Home/View PDF — purely presentational. */
@Composable
private fun PdfToImageStatusStrip(pageCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            stringResource(R.string.common_offline_badge),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
        Text(
            pluralStringResource(R.plurals.page_count, pageCount, pageCount),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}
