package com.example.memorysteps.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.memorysteps.R

private const val HOME = "home"
private const val GUIDE = "guide"
private const val TRAINING = "training"

@Composable
fun MemoryStepsApp() {
    val navController = rememberNavController()
    val trainingModel: TrainingViewModel = viewModel()
    val training by trainingModel.state.collectAsStateWithLifecycle()
    var confirmEnd by rememberSaveable { mutableStateOf(false) }
    fun openTraining() { navController.navigate(TRAINING) { launchSingleTop = true } }
    NavHost(navController = navController, startDestination = HOME) {
        composable(HOME) {
            HomeScreen(training, onStart = { trainingModel.start(it); openTraining() },
                onContinue = { openTraining() }, onDiscard = { confirmEnd = true },
                onOpenGuide = { navController.navigate(GUIDE) { launchSingleTop = true } })
        }
        composable(GUIDE) {
            GuideScreen(onReturnHome = { navController.popBackStack() })
        }
        composable(TRAINING) {
            val current = training
            if (current != null) TrainingScreen(current, trainingModel, onHome = { navController.popBackStack(HOME, false) })
            else LaunchedEffect(Unit) { navController.popBackStack(HOME, false) }
        }
    }
    if (confirmEnd) AlertDialog(onDismissRequest = { confirmEnd = false },
        title = { Text(stringResource(R.string.confirm_end)) },
        text = { Text(stringResource(R.string.confirm_end_description)) },
        confirmButton = { TextButton(onClick = { trainingModel.discard(); confirmEnd = false }) { Text(stringResource(R.string.end_confirm)) } },
        dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text(stringResource(R.string.cancel)) } },
    )
}
