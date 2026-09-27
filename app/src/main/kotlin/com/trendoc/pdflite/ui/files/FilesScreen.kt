package com.trendoc.pdflite.ui.files

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalContext
import com.trendoc.pdflite.R
import androidx.compose.ui.res.stringResource
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trendoc.pdflite.ui.common.FileIconAvatar
import com.trendoc.pdflite.util.SafFileUtils

/**
 * The "Files" bottom-nav tab — a minimal read-only browser over a folder tree the user
 * grants access to via SAF (no broad storage permission). Tapping a PDF opens it in View
 * PDF; tapping a folder descends into it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(onOpenFile: (Uri) -> Unit, viewModel: FilesViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    val pickTreeLauncher = rememberLauncherForActivityResult(
        contract = SafFileUtils.openDocumentTree
    ) { uri -> viewModel.onRootPicked(uri) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (uiState.canGoUp) {
                        IconButton(onClick = { viewModel.goUp() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.files_up_one_folder))
                        }
                    }
                },
                title = { Text(stringResource(R.string.common_files)) },
                actions = {
                    if (uiState.rootUri != null) {
                        TextButton(onClick = { viewModel.changeRoot() }) {
                            Text(stringResource(R.string.files_change_folder))
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                uiState.rootUri == null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Text(
                            stringResource(R.string.files_grant_access),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { pickTreeLauncher.launch(null) },
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            Text(stringResource(R.string.files_choose_folder))
                        }
                    }
                }
                uiState.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                uiState.entries.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.files_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.entries, key = { it.uri }) { entry ->
                        Card(
                            onClick = {
                                if (entry.isDirectory) viewModel.openFolder(entry.uri) else onOpenFile(entry.uri)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, Color(0x14191C1E))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (entry.isDirectory) {
                                    Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                } else {
                                    FileIconAvatar()
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(entry.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                    if (!entry.isDirectory) {
                                        Text(
                                            formatSize(entry.sizeBytes),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Localised "1.2 MB" — the platform formatter picks units and decimal marks. */
@Composable
private fun formatSize(bytes: Long): String =
    if (bytes < 0) "—" else android.text.format.Formatter.formatShortFileSize(LocalContext.current, bytes)
