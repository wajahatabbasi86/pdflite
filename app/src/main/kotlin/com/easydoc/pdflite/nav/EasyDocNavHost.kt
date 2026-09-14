package com.easydoc.pdflite.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.easydoc.pdflite.ui.compress.CompressScreen
import com.easydoc.pdflite.ui.home.HomeScreen
import com.easydoc.pdflite.ui.imagetopdf.ImageToPdfScreen
import com.easydoc.pdflite.ui.merge.MergeScreen
import com.easydoc.pdflite.ui.pdftoimage.PdfToImageScreen
import com.easydoc.pdflite.ui.picker.PdfPickerScreen
import com.easydoc.pdflite.ui.settings.AppearanceScreen
import com.easydoc.pdflite.ui.split.SplitScreen
import com.easydoc.pdflite.ui.view.ViewPdfScreen

/**
 * Single NavHost for the whole app. Per docs/REQUIREMENTS.md §1.3, navigation stays
 * flat: Home -> Tool Screen -> Result Screen, no deep nesting. Each build step (3-7)
 * adds its own tool route here rather than introducing nested graphs. Appearance is
 * reached from Home's top bar rather than a tool card, but follows the same flat rule —
 * one tap there, one tap back.
 */
object Routes {
    const val HOME = "home"
    const val PICKER_DEMO = "picker"
    const val MERGE = "merge"
    const val SPLIT = "split"
    const val COMPRESS = "compress"
    const val IMAGE_TO_PDF = "image_to_pdf"
    const val PDF_TO_IMAGE = "pdf_to_image"
    const val VIEW_PDF = "view_pdf"
    const val APPEARANCE = "appearance"
}

@Composable
fun EasyDocNavHost() {
    val navController = rememberNavController()
    val pendingUri by PendingPdfIntent.uri.collectAsState()

    // Opened via the system "Open with" chooser for a PDF (see PendingPdfIntent) — jump
    // straight to View PDF instead of leaving the user on Home to find it themselves.
    LaunchedEffect(pendingUri) {
        if (pendingUri != null) {
            navController.navigate(Routes.VIEW_PDF)
        }
    }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onToolSelected = { route -> navController.navigate(route) },
                onOpenAppearance = { navController.navigate(Routes.APPEARANCE) }
            )
        }
        composable(Routes.APPEARANCE) {
            AppearanceScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.PICKER_DEMO) {
            PdfPickerScreen()
        }
        composable(Routes.MERGE) {
            MergeScreen(onDone = {
                navController.popBackStack(Routes.HOME, inclusive = false)
            })
        }
        composable(Routes.SPLIT) {
            SplitScreen(onDone = {
                navController.popBackStack(Routes.HOME, inclusive = false)
            })
        }
        composable(Routes.COMPRESS) {
            CompressScreen(onDone = {
                navController.popBackStack(Routes.HOME, inclusive = false)
            })
        }
        composable(Routes.IMAGE_TO_PDF) {
            ImageToPdfScreen(onDone = {
                navController.popBackStack(Routes.HOME, inclusive = false)
            })
        }
        composable(Routes.PDF_TO_IMAGE) {
            PdfToImageScreen(onDone = {
                navController.popBackStack(Routes.HOME, inclusive = false)
            })
        }
        composable(Routes.VIEW_PDF) {
            ViewPdfScreen(
                initialUri = PendingPdfIntent.consume(),
                onDone = { navController.popBackStack(Routes.HOME, inclusive = false) }
            )
        }
    }
}
