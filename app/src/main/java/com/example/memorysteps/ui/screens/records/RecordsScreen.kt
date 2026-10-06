package com.example.memorysteps.ui.screens.records

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.memorysteps.R
import com.example.memorysteps.data.*
import com.example.memorysteps.game.GameType
import com.example.memorysteps.ui.components.*
import com.example.memorysteps.ui.theme.MenuStyle
import com.example.memorysteps.ui.theme.AppDimensions
import java.util.Locale

@Composable
fun RecordsScreen(overview: LearningOverview, types: List<TypeStatistics>, history: List<CycleHistory>,
    profiles: List<LearningProfile>, activeGame: Boolean, onHome: () -> Unit) {
    var showAllHistory by rememberSaveable { mutableStateOf(false) }
    val firstRate = accuracyPercent(overview.firstCorrect, overview.problems)
    MenuPage {
        MenuHeader(stringResource(R.string.home_title), stringResource(R.string.records_subtitle), onHome)
        AdaptiveMenuRow(listOf(
            { MetricTile(stringResource(R.string.metric_games), "${overview.completedCycles}${stringResource(R.string.unit_games)}",
                stringResource(R.string.metric_total), MenuStyle.teal) },
            { MetricTile(stringResource(R.string.metric_questions), "${overview.problems}${stringResource(R.string.unit_questions)}",
                stringResource(R.string.metric_saved), MenuStyle.blue) },
            { MetricTile(stringResource(R.string.metric_accuracy), firstRate?.let { stringResource(R.string.rate_value, it) } ?: "—",
                if (firstRate == null) stringResource(R.string.metric_no_data)
                else stringResource(R.string.metric_fraction, overview.firstCorrect, overview.problems), MenuStyle.plum,
                Modifier.testTag("metric-first")) },
        ))
        if (overview.problems == 0) MenuPanel {
            Text(stringResource(R.string.empty_record), style = MaterialTheme.typography.bodyLarge)
        }
        AdaptiveMenuRow(listOf({ MenuPanel {
            SectionAccent(stringResource(R.string.type_records))
            Text(stringResource(R.string.type_chart_hint), style = MaterialTheme.typography.bodyMedium, color = MenuStyle.muted)
            GameType.entries.forEachIndexed { index, type ->
                val stats = types.firstOrNull { it.type == type.name }
                RateBar(typeName(type), stats?.firstCorrect ?: 0, stats?.problems ?: 0,
                    listOf(MenuStyle.teal, MenuStyle.blue, MenuStyle.plum)[index], Modifier.testTag("rate-${type.name}"))
            }
        } }, { MenuPanel {
            SectionAccent(stringResource(R.string.trend_title))
            TrendChart(recentCompletedGames(history))
            Text(stringResource(R.string.trend_notice), style = MaterialTheme.typography.bodyMedium, color = MenuStyle.muted)
            HorizontalDivider(color = MenuStyle.line)
            RateBar(stringResource(R.string.final_rate), overview.finalCorrect, overview.problems, MenuStyle.ink)
        } }))
        profiles.forEach { profile ->
            MenuPanel {
                SectionAccent(stringResource(if (activeGame) R.string.condition_active_title else R.string.condition_title))
                Text(stringResource(R.string.condition_hint), style = MaterialTheme.typography.bodyMedium, color = MenuStyle.muted)
                val c = profile.difficulty.conditions()
                AdaptiveMenuRow(listOf(
                    { ConditionValue(stringResource(R.string.condition_memory), secondsLabel(c.memoryLimitMs), "condition-memory") },
                    { ConditionValue(stringResource(R.string.condition_wait), secondsLabel(c.waitMs), "condition-wait") },
                    { ConditionValue(stringResource(R.string.condition_options), stringResource(R.string.condition_options_value, c.optionCount), "condition-options") },
                    { ConditionValue(stringResource(R.string.condition_solve), c.solveLimitMs?.let { secondsLabel(it) }
                        ?: stringResource(R.string.adaptive_unlimited), "condition-solve") },
                ))
                Text(if (profile.pending) stringResource(R.string.adaptive_pending)
                    else if (activeGame) stringResource(R.string.adaptive_collected, profile.collected)
                    else stringResource(R.string.challenge_notice), style = MaterialTheme.typography.bodyMedium, color = MenuStyle.muted)
            }
        }
        MenuPanel {
            SectionAccent(stringResource(R.string.recent_records))
            if (history.isEmpty()) Text(stringResource(R.string.history_empty))
            (if (showAllHistory) history else history.take(5)).forEach { cycle ->
                val status = stringResource(when (cycle.status) {
                    "COMPLETED" -> R.string.record_completed
                    "STOPPED" -> R.string.record_stopped
                    else -> R.string.record_active
                })
                Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.smallGap)) {
                    Text(recordDate(cycle.createdAt, "yyyy.MM.dd HH:mm"), style = MaterialTheme.typography.titleLarge)
                    Text(status, Modifier.background(MenuStyle.tealTint, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium, color = MenuStyle.teal)
                    Text(stringResource(R.string.history_fraction, cycle.firstCorrect, cycle.problems), style = MaterialTheme.typography.bodyLarge)
                    HorizontalDivider(color = MenuStyle.line)
                }
            }
            if (history.size > 5) ActionButton(stringResource(if (showAllHistory) R.string.history_less else R.string.history_more),
                { showAllHistory = !showAllHistory }, primary = false)
        }
        Text(stringResource(R.string.records_notice), style = MaterialTheme.typography.bodyMedium, color = MenuStyle.muted)
    }
}

@Composable
private fun ConditionValue(label: String, value: String, tag: String) {
    Column(Modifier.fillMaxWidth().testTag(tag).background(MenuStyle.background, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(AppDimensions.smallGap)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MenuStyle.muted)
        Text(value, style = MaterialTheme.typography.titleLarge, color = MenuStyle.ink)
    }
}

@Composable
private fun secondsLabel(ms: Long): String = stringResource(R.string.adaptive_seconds,
    if (ms % 1000 == 0L) (ms / 1000).toString() else String.format(Locale.KOREA, "%.1f", ms / 1000.0))
