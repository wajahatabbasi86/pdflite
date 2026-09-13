package com.easydoc.pdflite.nav

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.easydoc.pdflite.ui.home.HomeScreen
import com.easydoc.pdflite.ui.merge.MergeScreen
import com.easydoc.pdflite.ui.picker.PdfPickerScreen
import com.easydoc.pdflite.ui.split.SplitScreen

/**
 * Single NavHost for the whole app. Per docs/REQUIREMENTS.md §1.3, navigation stays
 * flat: Home -> Tool Screen -> Result Screen, no deep nesting. Each build step (3-7)
 * adds its own tool route here rather than introducing nested graphs.
 */
object Routes {
    const val HOME = "home"
    const val PICKER_DEMO = "picker"
    const val MERGE = "merge"
    const val SPLIT = "split"
}

@Composable
fun EasyDocNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(onToolSelected = { route -> navController.navigate(route) })
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
    }
}
