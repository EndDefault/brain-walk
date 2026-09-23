package com.example.memorysteps.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.memorysteps.R
import com.example.memorysteps.game.TrainingMode
import com.example.memorysteps.data.CycleHistory

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
internal fun ActionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = true, enabled: Boolean = true) {
    val shape = RoundedCornerShape(8.dp)
    val padding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
    val bounds = modifier.fillMaxWidth().heightIn(min = 64.dp)
    if (primary) Button(onClick, bounds, enabled = enabled, shape = shape, contentPadding = padding) { Text(text, textAlign = TextAlign.Center) }
    else OutlinedButton(onClick, bounds, enabled = enabled, shape = shape, contentPadding = padding,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)) { Text(text, textAlign = TextAlign.Center) }
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
    onLearn: () -> Unit,
    onRecords: () -> Unit,
    onOpenGuide: () -> Unit,
) {
    Page {
        Column(Modifier.widthIn(max = 680.dp).fillMaxWidth().align(Alignment.CenterHorizontally),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.game_caption), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.brand_name), Modifier.fillMaxWidth().semantics { heading() },
                fontSize = 64.sp, lineHeight = 84.sp, fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
            HorizontalDivider(thickness = 3.dp, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            listOf(R.string.learn_menu to onLearn, R.string.home_title to onRecords, R.string.guide_button to onOpenGuide).forEach { (label, click) ->
                Button(click, Modifier.fillMaxWidth().heightIn(min = 88.dp), shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(24.dp), border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                    Text(stringResource(label), fontSize = 30.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
fun LearnScreen(active: CycleHistory?, enabled: Boolean, onStart: () -> Unit, onPractice: (TrainingMode) -> Unit,
    onStop: () -> Unit, onHome: () -> Unit) {
    Page {
        PageTitle(stringResource(R.string.learn_menu))
        Text(stringResource(R.string.mixed_description), style = MaterialTheme.typography.bodyLarge)
        ActionButton(stringResource(if (active == null) R.string.mixed_title else R.string.continue_training), onStart, enabled = enabled)
        if (active != null) {
            Text(stringResource(R.string.saved_progress, active.problems))
            ActionButton(stringResource(R.string.end_training), onStop, primary = false, enabled = enabled)
        }
        Text(stringResource(R.string.session_notice), style = MaterialTheme.typography.bodyMedium)
        HorizontalDivider()
        PageTitle(stringResource(R.string.practice_heading))
        Text(stringResource(R.string.practice_notice), style = MaterialTheme.typography.bodyMedium)
        BoxWithConstraints {
            val items = listOf(TrainingMode.COLOR to R.string.type_color, TrainingMode.PICTURE to R.string.type_picture, TrainingMode.NUMBER to R.string.type_number)
            if (maxWidth >= 600.dp && LocalDensity.current.fontScale <= 1.3f) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items.forEach { (mode, label) -> ActionButton(stringResource(label), { onPractice(mode) }, Modifier.weight(1f), primary = false, enabled = enabled) }
                }
            } else Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items.forEach { (mode, label) -> ActionButton(stringResource(label), { onPractice(mode) }, primary = false, enabled = enabled) }
            }
        }
        ActionButton(stringResource(R.string.home_button), onHome, primary = false)
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
