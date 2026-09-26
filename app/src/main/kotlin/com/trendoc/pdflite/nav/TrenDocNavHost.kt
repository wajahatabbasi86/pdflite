package com.trendoc.pdflite.nav

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.trendoc.pdflite.billing.EntitlementRepository
import com.trendoc.pdflite.onboarding.OnboardingRepository
import com.trendoc.pdflite.onboarding.OnboardingScreen
import com.trendoc.pdflite.ui.billing.BillingScreen
import com.trendoc.pdflite.ui.common.AdBanner
import com.trendoc.pdflite.ui.compress.CompressScreen
import com.trendoc.pdflite.ui.fillforms.FillFormsScreen
import com.trendoc.pdflite.ui.files.FilesScreen
import com.trendoc.pdflite.ui.home.HomeScreen
import com.trendoc.pdflite.ui.imagetopdf.ImageToPdfScreen
import com.trendoc.pdflite.ui.merge.MergeScreen
import com.trendoc.pdflite.ui.pdftoimage.PdfToImageScreen
import com.trendoc.pdflite.ui.picker.PdfPickerScreen
import com.trendoc.pdflite.ui.recents.RecentsScreen
import com.trendoc.pdflite.ui.settings.AppearanceScreen
import com.trendoc.pdflite.ui.split.SplitScreen
import com.trendoc.pdflite.ui.stamp.StampTextScreen
import com.trendoc.pdflite.ui.view.ViewPdfScreen

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
    const val FILL_FORMS = "fill_forms"
    const val ADD_TEXT = "add_text"
    const val APPEARANCE = "appearance"
    const val BILLING = "billing"
    const val FILES = "files"
    const val RECENTS = "recents"
    const val ONBOARDING = "onboarding"
}

@Composable
fun TrenDocNavHost() {
    val context = LocalContext.current
    val onboardingRepository = remember { OnboardingRepository(context) }
    // null = "still loading from DataStore" — the NavHost's startDestination must be known
    // before its first composition, so nothing renders (a beat shorter than the fastest
    // human blink) until we know whether onboarding has already been completed.
    val hasCompletedOnboarding by onboardingRepository.hasCompletedOnboarding.collectAsState(initial = null)
    val completed = hasCompletedOnboarding ?: return

    val navController = rememberNavController()
    val pendingUri by PendingPdfIntent.uri.collectAsState()

    // Opened via the system "Open with" chooser for a PDF (see PendingPdfIntent) — jump
    // straight to View PDF instead of leaving the user on Home to find it themselves.
    LaunchedEffect(pendingUri) {
        if (pendingUri != null) {
            navController.navigate(Routes.VIEW_PDF)
        }
    }

    // The banner lives here, below the NavHost on every single screen (not just Home) — one
    // shared instance instead of every screen composing its own, and it never eats into a
    // screen's own layout since it's a sibling below the NavHost's allotted space, not inside
    // it. Hidden entirely (not just invisible) whenever an ad-free window is active.
    val entitlementRepository = remember { EntitlementRepository(context) }
    val isAdFree by entitlementRepository.isAdFree.collectAsState(initial = false)

    Column(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = if (completed) Routes.HOME else Routes.ONBOARDING,
            modifier = Modifier.weight(1f)
        ) {
            composable(Routes.ONBOARDING) {
                val scope = rememberCoroutineScope()
                OnboardingScreen(onDone = {
                    scope.launch { onboardingRepository.setCompleted() }
                    navController.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                })
            }
            composable(Routes.HOME) {
                HomeScreen(
                    onToolSelected = { route -> navController.navigate(route) },
                    onOpenBilling = { navController.navigate(Routes.BILLING) },
                    onOpenRecents = { navController.navigate(Routes.RECENTS) },
                    onOpenFile = { uri -> PendingPdfIntent.uri.value = uri }
                )
            }
            composable(Routes.BILLING) {
                BillingScreen(onDone = { navController.popBackStack() })
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
                SplitScreen(
                    initialUri = PendingSplitUri.consume(),
                    // A plain pop (rather than popBackStack(HOME, ...)) so back returns to
                    // wherever this screen was actually reached from — Home normally, but
                    // View PDF when arrived via its "Extract Page" quick-action bridge.
                    onDone = { navController.popBackStack() }
                )
            }
            composable(Routes.COMPRESS) {
                CompressScreen(
                    initialUri = PendingCompressUri.consume(),
                    // Same reasoning as Split above — View PDF's "Compress" bridge is a
                    // second entry point into this screen besides Home.
                    onDone = { navController.popBackStack() }
                )
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
                    onDone = { navController.popBackStack(Routes.HOME, inclusive = false) },
                    onExtractPage = { uri ->
                        PendingSplitUri.uri.value = uri
                        navController.navigate(Routes.SPLIT)
                    },
                    onCompress = { uri ->
                        PendingCompressUri.uri.value = uri
                        navController.navigate(Routes.COMPRESS)
                    },
                    onFillForms = { uri ->
                        PendingFillFormsUri.uri.value = uri
                        navController.navigate(Routes.FILL_FORMS)
                    }
                )
            }
            composable(Routes.ADD_TEXT) {
                StampTextScreen(
                    initialUri = PendingAddTextUri.consume(),
                    onDone = { navController.popBackStack() }
                )
            }
            composable(Routes.FILL_FORMS) {
                FillFormsScreen(
                    initialUri = PendingFillFormsUri.consume(),
                    // Same reasoning as Split/Compress above — View PDF's "Fill Forms"
                    // bridge is a second entry point into this screen besides Home.
                    onDone = { navController.popBackStack() }
                )
            }
            composable(Routes.FILES) {
                FilesScreen(onOpenFile = { uri -> PendingPdfIntent.uri.value = uri })
            }
            composable(Routes.RECENTS) {
                RecentsScreen(onOpenFile = { uri -> PendingPdfIntent.uri.value = uri })
            }
        }

        val currentBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = currentBackStackEntry?.destination?.route

        if (currentRoute != Routes.ONBOARDING) {
            if (!isAdFree) {
                AdBanner()
            }
            BottomNavBar(
                currentRoute = currentRoute,
                onTabSelected = { route ->
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}

/** The four persistent bottom-nav tabs, matching the design reference's bar shown on
 * every screen (tool screens included), not just Home. "Tools" stays highlighted for
 * any tool route (Merge/Split/etc.), not only Home itself. */
@Composable
private fun BottomNavBar(currentRoute: String?, onTabSelected: (String) -> Unit) {
    val toolRoutes = setOf(
        Routes.HOME, Routes.MERGE, Routes.SPLIT, Routes.COMPRESS,
        Routes.IMAGE_TO_PDF, Routes.PDF_TO_IMAGE, Routes.VIEW_PDF, Routes.FILL_FORMS,
        Routes.ADD_TEXT
    )
    NavigationBar {
        NavigationBarItem(
            selected = currentRoute == Routes.FILES,
            onClick = { onTabSelected(Routes.FILES) },
            icon = { Icon(Icons.Filled.Folder, contentDescription = null) },
            label = { Text("Files") }
        )
        NavigationBarItem(
            selected = currentRoute in toolRoutes,
            onClick = { onTabSelected(Routes.HOME) },
            icon = { Icon(Icons.Outlined.Description, contentDescription = null) },
            label = { Text("Tools") }
        )
        NavigationBarItem(
            selected = currentRoute == Routes.RECENTS,
            onClick = { onTabSelected(Routes.RECENTS) },
            icon = { Icon(Icons.Filled.History, contentDescription = null) },
            label = { Text("Recents") }
        )
        NavigationBarItem(
            selected = currentRoute == Routes.APPEARANCE,
            onClick = { onTabSelected(Routes.APPEARANCE) },
            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            label = { Text("Settings") }
        )
    }
}
