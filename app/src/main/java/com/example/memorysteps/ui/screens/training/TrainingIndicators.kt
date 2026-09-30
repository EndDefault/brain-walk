package com.example.memorysteps.ui.screens.training

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import com.example.memorysteps.R
import com.example.memorysteps.ui.components.PageTitle
import com.example.memorysteps.ui.theme.AppDimensions
import com.example.memorysteps.ui.theme.GameTextStyles

@Composable
internal fun StageTitle(text: String) {
    PageTitle(text, centered = true)
}

@Composable
internal fun Countdown(milliseconds: Long, prominent: Boolean = false) {
    val seconds = (milliseconds + 999) / 1000
    val description = stringResource(R.string.seconds_left, seconds)
    Text(seconds.toString(), Modifier.testTag(if (prominent) "wait-countdown" else "stage-countdown")
        .clearAndSetSemantics { contentDescription = description },
        style = if (prominent) GameTextStyles.waitCountdown else GameTextStyles.countdown, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
}

@Composable
internal fun SolveCountdown(milliseconds: Long, inlineLabel: Boolean = false) {
    val seconds = (milliseconds + 999) / 1000
    val description = stringResource(R.string.seconds_left, seconds)
    val modifier = Modifier.testTag("stage-countdown").clearAndSetSemantics { contentDescription = description }
    val label: @Composable () -> Unit = {
        Text(stringResource(R.string.remaining_time_label), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
    }
    val value: @Composable () -> Unit = {
        Text(stringResource(R.string.seconds_value, seconds), style = GameTextStyles.countdown, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
    }
    if (inlineLabel) Row(modifier, horizontalArrangement = Arrangement.spacedBy(AppDimensions.itemGap), verticalAlignment = Alignment.CenterVertically) {
        label(); value()
    } else Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppDimensions.tinyGap)) {
        label(); value()
    }
}

@Composable
internal fun AttemptsRemaining(count: Int) {
    Text(stringResource(R.string.attempts_left, count), Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
}
