package com.example.memorysteps.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

private const val HOME = "home"
private const val GUIDE = "guide"

@Composable
fun MemoryStepsApp() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = HOME) {
        composable(HOME) {
            HomeScreen(onOpenGuide = { navController.navigate(GUIDE) { launchSingleTop = true } })
        }
        composable(GUIDE) {
            GuideScreen(onReturnHome = { navController.popBackStack() })
        }
    }
}
