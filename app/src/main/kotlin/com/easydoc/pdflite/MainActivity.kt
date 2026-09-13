package com.easydoc.pdflite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.easydoc.pdflite.nav.EasyDocNavHost
import com.easydoc.pdflite.ui.theme.EasyDocTheme

/**
 * Single activity hosting the entire Compose UI, per docs/TECH_STACK.md
 * (single-activity + Compose Navigation).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EasyDocTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    EasyDocNavHost()
                }
            }
        }
    }
}
