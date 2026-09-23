package com.example.memorysteps.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.memorysteps.R
import com.example.memorysteps.game.TrainingMode
import com.example.memorysteps.game.TrainingSnapshot

@Composable
internal fun Page(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 1000.dp).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp), content = content,
            )
        }
    }
}

@Composable
internal fun PageTitle(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
}

@Composable
internal fun ActionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = true) {
    val shape = RoundedCornerShape(8.dp)
    val padding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
    val bounds = modifier.fillMaxWidth().heightIn(min = 64.dp)
    if (primary) Button(onClick, bounds, shape = shape, contentPadding = padding) { Text(text) }
    else OutlinedButton(onClick, bounds, shape = shape, contentPadding = padding) { Text(text) }
}

@Composable
internal fun modeName(mode: TrainingMode): String = stringResource(when (mode) {
    TrainingMode.MIXED -> R.string.mixed_name
    TrainingMode.COLOR -> R.string.color_title
    TrainingMode.PICTURE -> R.string.picture_title
    TrainingMode.NUMBER -> R.string.number_title
})

@Composable
fun HomeScreen(
    training: TrainingSnapshot?,
    onStart: (TrainingMode) -> Unit,
    onContinue: () -> Unit,
    onDiscard: () -> Unit,
    onOpenGuide: () -> Unit,
) {
    Page {
        Text(stringResource(R.string.brand_name), style = MaterialTheme.typography.titleLarge)
        HorizontalDivider()
        PageTitle(stringResource(R.string.home_title))
        if (training != null && !training.complete) {
            Text(stringResource(R.string.progress_saved, modeName(training.mode), training.completed.size))
            ActionButton(stringResource(R.string.continue_training), onContinue)
            ActionButton(stringResource(R.string.end_training), onDiscard, primary = false)
        } else {
            if (training?.complete == true) {
                Text(stringResource(R.string.last_result, training.firstCorrect), style = MaterialTheme.typography.bodyLarge)
            } else Text(stringResource(R.string.empty_record), style = MaterialTheme.typography.bodyLarge)
            PageTitle(stringResource(R.string.training_heading))
            Text(stringResource(R.string.mixed_description), style = MaterialTheme.typography.bodyMedium)
            ActionButton(stringResource(R.string.mixed_title), { onStart(TrainingMode.MIXED) })
            Text(stringResource(R.string.single_description), style = MaterialTheme.typography.bodyMedium)
            TrainingMode.entries.filter { it.singleType != null }.forEach { mode ->
                ActionButton(modeName(mode), { onStart(mode) }, primary = false)
            }
        }
        HorizontalDivider()
        Text(stringResource(R.string.session_notice), style = MaterialTheme.typography.bodyMedium)
        ActionButton(stringResource(R.string.guide_button), onOpenGuide, primary = false)
    }
}

@Composable
fun GuideScreen(onReturnHome: () -> Unit) {
    Page {
        PageTitle(stringResource(R.string.guide_title))
        Text(stringResource(R.string.guide_description), style = MaterialTheme.typography.bodyLarge)
        listOf(
            R.string.remember_title to R.string.remember_description,
            R.string.wait_title to R.string.wait_description,
            R.string.solve_title to R.string.solve_description,
        ).forEachIndexed { index, (title, description) ->
            HorizontalDivider()
            Text("${index + 1}. ${stringResource(title)}", style = MaterialTheme.typography.titleLarge)
            Text(stringResource(description), style = MaterialTheme.typography.bodyLarge)
        }
        ActionButton(stringResource(R.string.home_button), onReturnHome)
    }
}
