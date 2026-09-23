package com.example.memorysteps.ui

import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.memorysteps.R
import com.example.memorysteps.data.*
import com.example.memorysteps.game.GameType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun RecordsScreen(overview: LearningOverview, types: List<TypeStatistics>, history: List<CycleHistory>, onHome: () -> Unit) {
    Page {
        PageTitle(stringResource(R.string.home_title))
        Text(stringResource(R.string.completed_games, overview.completedCycles), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.saved_questions, overview.problems), style = MaterialTheme.typography.titleLarge)
        if (overview.problems == 0) Text(stringResource(R.string.empty_record)) else {
            Text(stringResource(R.string.record_first, overview.firstCorrect, overview.problems))
            Text(stringResource(R.string.record_final, overview.finalCorrect, overview.problems))
        }
        HorizontalDivider()
        PageTitle(stringResource(R.string.type_records))
        GameType.entries.forEach { type ->
            val stats = types.firstOrNull { it.type == type.name }
            Text(if (stats == null) stringResource(R.string.type_empty, typeName(type))
                else stringResource(R.string.summary_type, typeName(type), stats.firstCorrect, stats.problems))
        }
        HorizontalDivider()
        PageTitle(stringResource(R.string.recent_records))
        history.forEach { cycle ->
            val date = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(cycle.createdAt))
            Text(date, style = MaterialTheme.typography.titleLarge)
            val status = stringResource(when (cycle.status) {
                "COMPLETED" -> R.string.record_completed
                "STOPPED" -> R.string.record_stopped
                else -> R.string.record_active
            })
            Text(stringResource(R.string.cycle_record, status, cycle.problems, cycle.firstCorrect))
            HorizontalDivider()
        }
        Text(stringResource(R.string.records_notice), style = MaterialTheme.typography.bodyMedium)
        ActionButton(stringResource(R.string.home_button), onHome, primary = false)
    }
}
