package com.easydoc.pdflite.ui.picker

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.easydoc.pdflite.util.SafFileUtils

/**
 * Step 1 proof screen: pick a PDF via SAF, render its pages via PdfRenderer, show
 * thumbnails in a grid. This validates the plumbing that every later tool screen
 * (Merge, Split, Compress, Convert) will build on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfPickerScreen(
    viewModel: PdfPickerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val pickDocumentLauncher = rememberLauncherForActivityResult(
        contract = SafFileUtils.openSingleDocument
    ) { uri ->
        viewModel.onDocumentPicked(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("PDF Picker (Step 1 demo)") })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(onClick = { pickDocumentLauncher.launch(arrayOf("application/pdf")) }) {
                Text("Select PDF")
            }

            uiState.fileName?.let { name ->
                Text("$name — ${uiState.pageCount} page(s)")
            }

            uiState.errorMessage?.let { message ->
                Card(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(message)
                        Button(onClick = { viewModel.clearError() }) {
                            Text("Try Again")
                        }
                    }
                }
            }

            if (uiState.isLoading) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.thumbnails.isNotEmpty()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.thumbnails) { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .height(140.dp)
                        )
                    }
                }
            }
        }
    }
}
