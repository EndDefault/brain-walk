package com.example.memorysteps.ui.screens.training

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import com.example.memorysteps.R
import com.example.memorysteps.game.RoundPhase
import com.example.memorysteps.game.TrainingSnapshot
import com.example.memorysteps.ui.components.MemoryItemView
import com.example.memorysteps.ui.theme.AppDimensions
import com.example.memorysteps.ui.theme.GameTextStyles

@Composable
internal fun AnswerOptions(state: TrainingSnapshot, busy: Boolean, tileHeight: Dp, availableWidth: Dp, onAnswer: (String) -> Unit) {
    val columns = state.problem.conditions.layout.columns
    val minTileWidth = with(LocalDensity.current) { GameTextStyles.optionNumber.fontSize.toDp() * 1.6f + AppDimensions.pagePadding }.coerceAtLeast(AppDimensions.optionMinSize)
    val gridWidth = maxOf(availableWidth, minTileWidth * columns + AppDimensions.itemGap * (columns - 1))
    val showNumbers = state.problem.conditions.layout.rows == 2
    Box(Modifier.fillMaxWidth().testTag("options-scroll").horizontalScroll(rememberScrollState())) {
        Column(Modifier.width(gridWidth), verticalArrangement = Arrangement.spacedBy(AppDimensions.itemGap)) {
            state.problem.options.chunked(columns).forEach { options ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppDimensions.itemGap)) {
                    options.forEach { option ->
                        val wrong = option.id in state.round.disabledOptionIds
                        val wrongDescription = stringResource(R.string.wrong_choice)
                        val number = stringResource(R.string.choice_number, state.problem.options.indexOf(option) + 1)
                        Surface(onClick = { onAnswer(option.id) },
                            enabled = state.round.phase == RoundPhase.SOLVE && !wrong && !busy,
                            modifier = Modifier.weight(1f).heightIn(min = if (showNumbers) maxOf(AppDimensions.labeledOptionMinHeight, tileHeight) else tileHeight).testTag("option-${option.id}")
                                .semantics { if (wrong) stateDescription = wrongDescription; if (!showNumbers) contentDescription = number },
                            shape = MaterialTheme.shapes.small,
                            color = if (wrong) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(AppDimensions.borderWidth, MaterialTheme.colorScheme.outline)) {
                            Column(Modifier.padding(AppDimensions.smallGap).alpha(if (wrong) AppDimensions.disabledOptionAlpha else 1f),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppDimensions.tinyGap)) {
                                if (showNumbers) Text(number, style = MaterialTheme.typography.bodyMedium)
                                MemoryItemView(option.item, Modifier.fillMaxWidth().height(if (showNumbers) AppDimensions.labeledOptionImageHeight else tileHeight - AppDimensions.itemGap))
                            }
                        }
                    }
                }
            }
        }
    }
}
