package com.example.memorysteps.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.memorysteps.R
import com.example.memorysteps.game.GameConditions
import com.example.memorysteps.game.GameType
import java.util.Locale

@Composable
internal fun typeName(type: GameType): String = stringResource(
    when (type) {
        GameType.COLOR -> R.string.type_color
        GameType.PICTURE -> R.string.type_picture
        GameType.NUMBER -> R.string.type_number
    },
)

@Composable
internal fun conditionsText(conditions: GameConditions): String {
    val solve = conditions.solveLimitMs?.let { stringResource(R.string.adaptive_seconds, seconds(it)) }
        ?: stringResource(R.string.adaptive_unlimited)
    return stringResource(
        R.string.adaptive_conditions,
        seconds(conditions.memoryLimitMs), seconds(conditions.waitMs), conditions.optionCount, solve,
    )
}

private fun seconds(ms: Long): String =
    if (ms % 1000 == 0L) (ms / 1000).toString() else String.format(Locale.KOREA, "%.1f", ms / 1000.0)
