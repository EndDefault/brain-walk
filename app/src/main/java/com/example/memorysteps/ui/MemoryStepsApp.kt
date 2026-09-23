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
private const val LEARN = "learn"
private const val RECORDS = "records"

@Composable
fun MemoryStepsApp() {
    val navController = rememberNavController()
    val trainingModel: TrainingViewModel = viewModel()
    val training by trainingModel.state.collectAsStateWithLifecycle()
    val active by trainingModel.active.collectAsStateWithLifecycle()
    val ready by trainingModel.ready.collectAsStateWithLifecycle()
    val busy by trainingModel.busy.collectAsStateWithLifecycle()
    val storageError by trainingModel.storageError.collectAsStateWithLifecycle()
    val overview by trainingModel.overview.collectAsStateWithLifecycle()
    val types by trainingModel.typeStatistics.collectAsStateWithLifecycle()
    val history by trainingModel.history.collectAsStateWithLifecycle()
    var confirmEnd by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(trainingModel) {
        trainingModel.openTraining.collect { navController.navigate(TRAINING) { launchSingleTop = true } }
    }
    NavHost(navController = navController, startDestination = HOME) {
        composable(HOME) {
            HomeScreen(onLearn = { navController.navigate(LEARN) { launchSingleTop = true } },
                onRecords = { navController.navigate(RECORDS) { launchSingleTop = true } },
                onOpenGuide = { navController.navigate(GUIDE) { launchSingleTop = true } })
        }
        composable(LEARN) {
            LearnScreen(active, ready && !busy, onStart = { trainingModel.start(com.example.memorysteps.game.TrainingMode.MIXED) },
                onPractice = { trainingModel.start(it, practice = true) }, onStop = { confirmEnd = true },
                onHome = { navController.popBackStack(HOME, false) })
        }
        composable(RECORDS) { RecordsScreen(overview, types, history) { navController.popBackStack(HOME, false) } }
        composable(GUIDE) {
            GuideScreen(onReturnHome = { navController.popBackStack() })
        }
        composable(TRAINING) {
            val current = training
            if (current != null) TrainingScreen(current, trainingModel, onHome = { navController.popBackStack(LEARN, false) })
            else LaunchedEffect(Unit) { navController.popBackStack(HOME, false) }
        }
    }
    if (confirmEnd) AlertDialog(onDismissRequest = { confirmEnd = false },
        title = { Text(stringResource(R.string.confirm_end)) },
        text = { Text(stringResource(R.string.confirm_end_description)) },
        confirmButton = { TextButton(onClick = { trainingModel.stopTraining(); confirmEnd = false }) { Text(stringResource(R.string.end_confirm)) } },
        dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text(stringResource(R.string.cancel)) } },
    )
    if (storageError) AlertDialog(onDismissRequest = {},
        title = { Text(stringResource(R.string.storage_error)) },
        text = { Text(stringResource(R.string.storage_error_description)) },
        confirmButton = { TextButton(onClick = { trainingModel.reload() }) { Text(stringResource(R.string.retry_button)) } },
    )
}
