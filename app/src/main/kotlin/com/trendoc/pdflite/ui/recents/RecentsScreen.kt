package com.trendoc.pdflite.ui.recents

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalContext
import com.trendoc.pdflite.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trendoc.pdflite.recents.RecentEntry
import com.trendoc.pdflite.ui.common.FileIconAvatar
import kotlin.math.roundToInt

/**
 * The "Recents" bottom-nav tab — every file this app itself has opened (View PDF) or
 * produced (Merge/Split/Image-to-PDF/Fill Forms saves), most-recent-first. Deliberately
 * not a device-wide storage scan (see [com.trendoc.pdflite.recents.RecentsRepository]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentsScreen(onOpenFile: (android.net.Uri) -> Unit, viewModel: RecentsViewModel = viewModel()) {
    val entries by viewModel.entries.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.common_recents)) },
                actions = {
                    if (entries.isNotEmpty()) {
                        TextButton(onClick = { viewModel.clearAll() }) {
                            Text(stringResource(R.string.common_clear_all), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        stringResource(R.string.recents_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(entries, key = { it.uri }) { entry ->
                    RecentRow(
                        entry = entry,
                        onClick = { onOpenFile(entry.uri) },
                        onRemove = { viewModel.remove(entry.uri) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentRow(entry: RecentEntry, onClick: () -> Unit, onRemove: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0x14191C1E))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FileIconAvatar()
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        listOfNotNull(
                            formatSize(entry.sizeBytes),
                            if (entry.pageCount > 0) pluralStringResource(R.plurals.page_count, entry.pageCount, entry.pageCount) else null,
                            relativeTime(entry.timestampMillis)
                        ).joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 7.dp, vertical = 1.dp)
                ) {
                    Text(
                        sourceLabelText(entry.sourceLabel),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.recents_remove_from_recents), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Localised "1.2 MB" — the platform formatter picks units and decimal marks. */
@Composable
private fun formatSize(bytes: Long): String =
    if (bytes < 0) "—" else android.text.format.Formatter.formatShortFileSize(LocalContext.current, bytes)

/** "5 minutes ago", "Yesterday"… — already translated by the platform for every locale. */
private fun relativeTime(timestampMillis: Long): String =
    android.text.format.DateUtils.getRelativeTimeSpanString(
        timestampMillis,
        System.currentTimeMillis(),
        android.text.format.DateUtils.MINUTE_IN_MILLIS
    ).toString()

/** The source tag is stored as a fixed English identifier ("Merge", "Split"…) — storing
 * translated text would freeze each entry in whatever language was active when it was saved.
 * Translate it here, at display time; anything unknown is shown as stored. */
@Composable
private fun sourceLabelText(stored: String): String = when (stored) {
    "View PDF" -> stringResource(R.string.recents_source_view_pdf)
    "Merge" -> stringResource(R.string.recents_source_merge)
    "Split" -> stringResource(R.string.recents_source_split)
    "Compress" -> stringResource(R.string.recents_source_compress)
    "Image to PDF" -> stringResource(R.string.recents_source_image_to_pdf)
    "Fill Forms" -> stringResource(R.string.recents_source_fill_forms)
    "Add Text" -> stringResource(R.string.recents_source_add_text)
    else -> stored
}
