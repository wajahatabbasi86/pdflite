package com.easydoc.pdflite.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** One entry in the Home screen's tool grid (§2). */
data class ToolCard(
    val label: String,
    val description: String,
    val route: String
)

/**
 * Home screen per docs/REQUIREMENTS.md §2: a grid of 5 tool cards.
 * Compress/Convert routes are still placeholders until their respective
 * build steps (5-7) land; tapping one of those just navigates to the picker
 * demo screen for now. Merge (step 3) and Split (step 4) route to the real features.
 */
private val tools = listOf(
    ToolCard("Merge", "Combine multiple PDFs into one", "merge"),
    ToolCard("Split", "Extract or split pages", "split"),
    ToolCard("Compress", "Reduce file size", "picker"),
    ToolCard("Image → PDF", "Turn images into a PDF", "picker"),
    ToolCard("PDF → Image", "Export pages as images", "picker"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onToolSelected: (String) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("EasyDoc") })
        }
        // AdMob banner (§1.5, §7) is added in build step 8 — Home screen only, not on
        // tool/processing screens.
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(tools) { tool ->
                Card(
                    onClick = { onToolSelected(tool.route) },
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(tool.label, modifier = Modifier.padding(12.dp))
                    Text(tool.description, modifier = Modifier.padding(horizontal = 12.dp))
                }
            }
        }
    }
}
