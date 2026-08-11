package com.example.suicanfcreader.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.suicanfcreader.view.screens.TopScreen
import com.example.suicanfcreader.view.screens.SettingsScreen
import com.example.suicanfcreader.view.screens.StatsScreen
import com.example.suicanfcreader.viewModel.TopScreenViewModel
import com.example.suicanfcreader.viewModel.TopScreenViewModelFactory

/**
 * Provides the navigation in the app.
 */
@Composable
fun SuicaNFCReaderNavigation(
    navController: NavHostController,
    modifier: Modifier,
    viewModel: TopScreenViewModel
) {
    NavHost(
        navController = navController,
        startDestination = Screen.TopScreen.route,
        modifier = modifier
    ) {
        composable(Screen.TopScreen.route) {
             TopScreen(viewModel, onOpenStats = { navController.navigate(Screen.Stats.route) })
        }
        composable(Screen.Stats.route) {
            StatsScreen(viewModel, onBack = { navController.popBackStack() })
        }
        composable(Screen.Settings.route) {
            SettingsScreen(viewModel, onBack = { navController.popBackStack() })
        }
    }
}
