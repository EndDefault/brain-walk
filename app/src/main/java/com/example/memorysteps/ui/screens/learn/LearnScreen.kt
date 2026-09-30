package com.example.memorysteps.ui.screens.learn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import com.example.memorysteps.R
import com.example.memorysteps.data.CycleHistory
import com.example.memorysteps.game.TrainingMode
import com.example.memorysteps.ui.components.ActionButton
import com.example.memorysteps.ui.components.Page
import com.example.memorysteps.ui.components.PageTitle
import com.example.memorysteps.ui.components.SectionTitle
import com.example.memorysteps.ui.theme.AppDimensions

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
        Text(stringResource(R.string.adaptive_notice), style = MaterialTheme.typography.bodyMedium)
        HorizontalDivider()
        SectionTitle(stringResource(R.string.practice_heading))
        Text(stringResource(R.string.practice_notice), style = MaterialTheme.typography.bodyMedium)
        BoxWithConstraints {
            val items = listOf(TrainingMode.COLOR to R.string.type_color, TrainingMode.PICTURE to R.string.type_picture, TrainingMode.NUMBER to R.string.type_number)
            if (maxWidth >= AppDimensions.wideLayoutMinWidth && LocalDensity.current.fontScale <= AppDimensions.wideLayoutMaxFontScale) {
                Row(horizontalArrangement = Arrangement.spacedBy(AppDimensions.itemGap)) {
                    items.forEach { (mode, label) -> ActionButton(stringResource(label), { onPractice(mode) }, Modifier.weight(1f), primary = false, enabled = enabled) }
                }
            } else Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.itemGap)) {
                items.forEach { (mode, label) -> ActionButton(stringResource(label), { onPractice(mode) }, primary = false, enabled = enabled) }
            }
        }
        ActionButton(stringResource(R.string.home_button), onHome, primary = false)
    }
}
