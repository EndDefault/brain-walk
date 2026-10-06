package com.example.memorysteps.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.memorysteps.R
import com.example.memorysteps.game.TrainingMode
import com.example.memorysteps.ui.components.DialogActionButton
import com.example.memorysteps.ui.screens.guide.GuideScreen
import com.example.memorysteps.ui.screens.home.HomeScreen
import com.example.memorysteps.ui.screens.learn.LearnScreen
import com.example.memorysteps.ui.screens.records.RecordsScreen
import com.example.memorysteps.ui.screens.training.TrainingScreen
import com.example.memorysteps.ui.screens.training.TrainingViewModel

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
    val preparing by trainingModel.preparing.collectAsStateWithLifecycle()
    val preparationError by trainingModel.preparationError.collectAsStateWithLifecycle()
    val storageError by trainingModel.storageError.collectAsStateWithLifecycle()
    val overview by trainingModel.overview.collectAsStateWithLifecycle()
    val types by trainingModel.typeStatistics.collectAsStateWithLifecycle()
    val history by trainingModel.history.collectAsStateWithLifecycle()
    val profiles by trainingModel.profiles.collectAsStateWithLifecycle()
    var confirmEnd by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(trainingModel) {
        trainingModel.openTraining.collect {
            navController.navigate(TRAINING) { launchSingleTop = true }
        }
    }
    NavHost(navController = navController, startDestination = HOME) {
        composable(HOME) {
            HomeScreen(
                onLearn = { navController.navigate(LEARN) { launchSingleTop = true } },
                onRecords = { navController.navigate(RECORDS) { launchSingleTop = true } },
                onOpenGuide = { navController.navigate(GUIDE) { launchSingleTop = true } },
            )
        }
        composable(LEARN) {
            LearnScreen(
                active = active,
                enabled = ready && !busy,
                onStart = { trainingModel.start(TrainingMode.MIXED) },
                onPractice = { trainingModel.start(it, practice = true) },
                onStop = { confirmEnd = true },
                onHome = { navController.popBackStack(HOME, false) },
            )
        }
        composable(RECORDS) {
            RecordsScreen(
                overview, types, history, profiles,
                activeGame = active != null,
                onHome = { navController.popBackStack(HOME, false) },
            )
        }
        composable(GUIDE) {
            GuideScreen(
                onReturnHome = { navController.popBackStack() },
            )
        }
        composable(TRAINING) {
            val current = training
            if (current != null) {
                TrainingScreen(
                    current, trainingModel,
                    onBack = { navController.popBackStack(LEARN, false) },
                    onHome = { navController.popBackStack(HOME, false) },
                )
            } else {
                LaunchedEffect(Unit) { navController.popBackStack(HOME, false) }
            }
        }
    }
    if (confirmEnd) AlertDialog(
        onDismissRequest = { confirmEnd = false },
        title = { Text(stringResource(R.string.confirm_end)) },
        text = { Text(stringResource(R.string.confirm_end_description)) },
        confirmButton = {
            DialogActionButton(stringResource(R.string.end_confirm)) {
                trainingModel.stopTraining()
                confirmEnd = false
            }
        },
        dismissButton = {
            DialogActionButton(stringResource(R.string.cancel)) { confirmEnd = false }
        },
    )
    if (storageError) AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.storage_error)) },
        text = { Text(stringResource(R.string.storage_error_description)) },
        confirmButton = {
            DialogActionButton(stringResource(R.string.retry_button), trainingModel::reload)
        },
    )
    if (preparing) AlertDialog(
        onDismissRequest = trainingModel::cancelPreparation,
        title = { Text(stringResource(if (preparationError) R.string.author_failed else R.string.author_preparing)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (!preparationError) CircularProgressIndicator()
                Text(stringResource(if (preparationError) R.string.author_failed_detail else R.string.author_preparing_detail))
            }
        },
        confirmButton = {
            DialogActionButton(stringResource(if (preparationError) R.string.retry_button else R.string.cancel)) {
                if (preparationError) trainingModel.retryPreparation() else trainingModel.cancelPreparation()
            }
        },
        dismissButton = { DialogActionButton(stringResource(R.string.author_fallback), trainingModel::startFallback) },
    )
}
