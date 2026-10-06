package com.example.memorysteps.ui.screens.training

import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.example.memorysteps.R
import com.example.memorysteps.game.GameType
import com.example.memorysteps.game.TrainingSnapshot
import com.example.memorysteps.ui.components.typeName

@Composable
internal fun TrainingSummary(state: TrainingSnapshot) {
    StageTitle(stringResource(if (state.practice) R.string.practice_summary else R.string.summary_title))
    Text(stringResource(R.string.summary_first, state.firstCorrect, state.total), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
    Text(stringResource(R.string.summary_final, state.finalCorrect, state.total), textAlign = TextAlign.Center)
    HorizontalDivider()
    GameType.entries.forEach { type ->
        val results = state.completed.filter { it.type == type }
        if (results.isNotEmpty()) Text(stringResource(R.string.summary_type, typeName(type),
            results.count { it.result.firstChoiceCorrect }, results.size), textAlign = TextAlign.Center)
    }
    Text(stringResource(if (state.practice) R.string.practice_notice else R.string.session_notice),
        style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    if (!state.practice) Text(stringResource(R.string.adaptive_notice), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
}
