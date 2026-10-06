package com.example.memorysteps.ui.screens.records

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.memorysteps.R
import com.example.memorysteps.data.CycleHistory
import com.example.memorysteps.ui.components.MenuPanel
import com.example.memorysteps.ui.theme.AppDimensions
import com.example.memorysteps.ui.theme.MenuStyle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun recordDate(at: Long, pattern: String = "MM.dd"): String = DateTimeFormatter.ofPattern(pattern)
    .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(at))

@Composable
internal fun MetricTile(label: String, value: String, detail: String, accent: Color, modifier: Modifier = Modifier) {
    MenuPanel(modifier) {
        Box(Modifier.size(36.dp, 4.dp).background(accent, RoundedCornerShape(2.dp)))
        Text(label, style = MaterialTheme.typography.titleLarge, color = MenuStyle.muted)
        Text(value, style = MaterialTheme.typography.displaySmall, color = MenuStyle.ink)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MenuStyle.muted)
    }
}

@Composable
internal fun RateBar(label: String, correct: Int, total: Int, accent: Color, modifier: Modifier = Modifier) {
    val percent = accuracyPercent(correct, total)
    val value = percent?.let { stringResource(R.string.rate_value, it) } ?: "—"
    val detail = if (percent == null) stringResource(R.string.metric_no_data)
        else stringResource(R.string.metric_fraction, correct, total)
    Column(modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(AppDimensions.smallGap)) {
        Text(label, style = MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimensions.itemGap)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, color = accent)
            Text(detail, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MenuStyle.muted)
        }
        Canvas(Modifier.fillMaxWidth().height(14.dp).clearAndSetSemantics {}) {
            drawLine(MenuStyle.track, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), size.height, StrokeCap.Butt)
            if (percent != null && percent > 0) drawLine(accent, Offset(0f, size.height / 2),
                Offset(size.width * percent / 100, size.height / 2), size.height, StrokeCap.Butt)
        }
    }
}

@Composable
internal fun TrendChart(games: List<CycleHistory>) {
    if (games.isEmpty()) {
        Text(stringResource(R.string.trend_empty), style = MaterialTheme.typography.bodyLarge, color = MenuStyle.muted)
        return
    }
    val values = games.map { checkNotNull(accuracyPercent(it.firstCorrect, it.problems)) }
    val labels = games.map { recordDate(it.completedAt ?: it.createdAt) }
    val accessiblePoints = games.mapIndexed { i, game -> stringResource(R.string.trend_point,
        recordDate(game.completedAt ?: game.createdAt, "MM.dd HH:mm"), game.firstCorrect, game.problems, values[i]) }.joinToString("; ")
    Text(stringResource(R.string.rate_value, values.last()), style = MaterialTheme.typography.displaySmall, color = MenuStyle.teal)
    Row(Modifier.fillMaxWidth().testTag("records-trend").semantics { contentDescription = accessiblePoints },
        horizontalArrangement = Arrangement.spacedBy(AppDimensions.mediumGap)) {
        Column(Modifier.height(210.dp), verticalArrangement = Arrangement.SpaceBetween) {
            listOf(100, 50, 0).forEach { Text(stringResource(R.string.rate_value, it), style = MaterialTheme.typography.bodyMedium, color = MenuStyle.muted) }
        }
        Canvas(Modifier.weight(1f).height(210.dp).clearAndSetSemantics {}) {
            val inset = 16.dp.toPx()
            val top = inset
            val bottom = size.height - inset
            val width = (size.width - inset * 2).coerceAtLeast(1f)
            listOf(0f, .5f, 1f).forEach { fraction ->
                val y = top + (bottom - top) * fraction
                drawLine(MenuStyle.line, Offset(inset, y), Offset(size.width - inset, y), 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())))
            }
            val points = values.mapIndexed { i, value -> Offset(
                if (values.size == 1) size.width / 2 else inset + width * i / (values.size - 1),
                bottom - (bottom - top) * value / 100f)
            }
            if (points.size > 1) {
                drawPath(Path().apply {
                    moveTo(points.first().x, bottom)
                    points.forEach { lineTo(it.x, it.y) }
                    lineTo(points.last().x, bottom); close()
                }, MenuStyle.teal.copy(alpha = .09f))
                drawPath(Path().apply {
                    moveTo(points.first().x, points.first().y)
                    points.drop(1).forEach { lineTo(it.x, it.y) }
                }, MenuStyle.teal, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            }
            points.forEach { point ->
                drawCircle(Color.White, 6.dp.toPx(), point)
                drawCircle(MenuStyle.teal, 4.dp.toPx(), point)
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(labels.first(), style = MaterialTheme.typography.bodyMedium, color = MenuStyle.muted)
        if (games.size > 1) Text(labels.last(), style = MaterialTheme.typography.bodyMedium, color = MenuStyle.muted)
    }
    Text(stringResource(if (games.size == 1) R.string.trend_one else R.string.trend_direction),
        style = MaterialTheme.typography.bodyMedium, color = MenuStyle.muted)
}
