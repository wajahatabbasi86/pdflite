package com.easydoc.pdflite

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.easydoc.pdflite.nav.EasyDocNavHost
import com.easydoc.pdflite.nav.PendingPdfIntent
import com.easydoc.pdflite.ui.settings.AppearanceViewModel
import com.easydoc.pdflite.ui.theme.EasyDocTheme

/**
 * Single activity hosting the entire Compose UI, per docs/TECH_STACK.md
 * (single-activity + Compose Navigation).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        capturePdfViewIntent(intent)

        setContent {
            // Read the user's Appearance preferences at the root, once, so the whole app
            // (not just the Home/Appearance screens) recomposes the moment accent or
            // theme mode changes — same AppearanceViewModel instance the Appearance
            // screen writes to, since both are scoped to this Activity by default.
            val appearanceViewModel: AppearanceViewModel = viewModel()
            val prefs by appearanceViewModel.preferences.collectAsState()

            EasyDocTheme(
                themeMode = prefs.theme,
                accent = prefs.accent,
                cardTint = prefs.cardTint,
                borderTint = prefs.borderTint,
                mutedTint = prefs.mutedTint
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    EasyDocNavHost()
                }
            }
        }
    }

    // android:launchMode="singleTask" (AndroidManifest.xml) means tapping a second PDF
    // while EasyDoc is already running redelivers here instead of creating a new instance.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        capturePdfViewIntent(intent)
    }

    /** Picks up a PDF Uri from the system "Open with EasyDoc" chooser (ACTION_VIEW,
     * application/pdf — see the manifest's second intent-filter) and stashes it in
     * [PendingPdfIntent] for the nav graph to consume once. */
    private fun capturePdfViewIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW && intent.type == "application/pdf") {
            intent.data?.let { PendingPdfIntent.uri.value = it }
        }
    }
}
