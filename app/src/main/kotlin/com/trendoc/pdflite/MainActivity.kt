package com.trendoc.pdflite

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
import com.trendoc.pdflite.billing.AdConsent
import com.trendoc.pdflite.nav.TrenDocNavHost
import com.trendoc.pdflite.nav.PendingPdfIntent
import com.trendoc.pdflite.ui.settings.AppearanceViewModel
import com.trendoc.pdflite.ui.theme.TrenDocTheme

/**
 * Single activity hosting the entire Compose UI, per docs/TECH_STACK.md
 * (single-activity + Compose Navigation).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        capturePdfViewIntent(intent)
        // Before any ad can load: shows the EEA/UK consent form when UMP says one is needed.
        AdConsent.gather(this)

        setContent {
            // Read the user's Appearance preferences at the root, once, so the whole app
            // (not just the Home/Appearance screens) recomposes the moment accent or
            // theme mode changes — same AppearanceViewModel instance the Appearance
            // screen writes to, since both are scoped to this Activity by default.
            val appearanceViewModel: AppearanceViewModel = viewModel()
            val prefs by appearanceViewModel.preferences.collectAsState()

            TrenDocTheme(
                themeMode = prefs.theme,
                accent = prefs.accent,
                cardTint = prefs.cardTint,
                borderTint = prefs.borderTint,
                mutedTint = prefs.mutedTint
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TrenDocNavHost()
                }
            }
        }
    }

    // android:launchMode="singleTask" (AndroidManifest.xml) means tapping a second PDF
    // while TrenDoc is already running redelivers here instead of creating a new instance.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        capturePdfViewIntent(intent)
    }

    /** Picks up a PDF Uri from the system "Open with TrenDoc" chooser (ACTION_VIEW,
     * application/pdf — see the manifest's second intent-filter) and stashes it in
     * [PendingPdfIntent] for the nav graph to consume once. */
    private fun capturePdfViewIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW && intent.type == "application/pdf") {
            intent.data?.takeIf(::isAcceptableIncomingUri)?.let { PendingPdfIntent.uri.value = it }
        }
    }

    /**
     * Only `content://` from someone else's provider. `file://` can't be read without the
     * storage permission TrenDoc deliberately doesn't hold (and Android 7+ blocks senders from
     * passing it); our own FileProvider is refused so another app can't get TrenDoc to open
     * files from its private cache.
     */
    private fun isAcceptableIncomingUri(uri: android.net.Uri): Boolean =
        uri.scheme == android.content.ContentResolver.SCHEME_CONTENT &&
            uri.authority != null &&
            uri.authority != "$packageName.fileprovider"
}
