package com.example.memorysteps.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.memorysteps.R
import com.example.memorysteps.game.GameType
import com.example.memorysteps.game.RoundOutcome
import com.example.memorysteps.game.RoundPhase
import com.example.memorysteps.game.TrainingSnapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.awaitCancellation

@Composable
internal fun typeName(type: GameType): String = stringResource(when (type) {
    GameType.COLOR -> R.string.type_color
    GameType.PICTURE -> R.string.type_picture
    GameType.NUMBER -> R.string.type_number
})

@Composable
fun TrainingScreen(state: TrainingSnapshot, viewModel: TrainingViewModel, onHome: () -> Unit) {
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    // An outgoing navigation entry must not pause a newly started cycle.
    val sessionId = remember(owner) { state.sessionId }
    val id = state.problem.id
    val phase = state.round.phase
    BackHandler { viewModel.pause(sessionId); onHome() }
    DisposableEffect(owner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) viewModel.pause(sessionId)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); viewModel.pause(sessionId) }
    }
    LaunchedEffect(owner, viewModel) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) { delay(100); viewModel.tick() }
        }
    }
    LaunchedEffect(id, phase, state.showSummary) {
        if (!state.showSummary && (phase == RoundPhase.READY || phase == RoundPhase.OPTIONS_PENDING)) {
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                // Navigation may still be entering. Cross a drawn frame after resume.
                withFrameNanos { }; withFrameNanos { }
                if (phase == RoundPhase.READY) viewModel.memoryShown(id) else viewModel.optionsShown(id)
                awaitCancellation()
            }
        }
    }
    // Reset scrolling for each problem/phase so content never begins offscreen.
    key(id, phase, state.showSummary) {
        Page {
            if (state.practice) Text(stringResource(R.string.practice_label), style = MaterialTheme.typography.titleLarge)
            if (state.showSummary) {
                PageTitle(stringResource(if (state.practice) R.string.practice_summary else R.string.summary_title))
                Text(stringResource(R.string.summary_first, state.firstCorrect), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.summary_final, state.finalCorrect))
                HorizontalDivider()
                GameType.entries.forEach { type ->
                    val results = state.completed.filter { it.type == type }
                    if (results.isNotEmpty()) Text(stringResource(R.string.summary_type, typeName(type),
                        results.count { it.result.firstChoiceCorrect }, results.size))
                }
                Text(stringResource(if (state.practice) R.string.practice_notice else R.string.session_notice), style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(stringResource(R.string.question_progress, state.questionNumber, typeName(state.problem.type)),
                    modifier = Modifier.testTag("question-progress"), style = MaterialTheme.typography.titleLarge)
                when (phase) {
                    RoundPhase.READY, RoundPhase.MEMORY -> {
                        PageTitle(stringResource(R.string.memory_prompt))
                        Countdown(state.round.remainingStageMs ?: state.problem.conditions.memoryLimitMs)
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            MemoryItemView(state.problem.target, Modifier.size(180.dp).testTag("memory-target"), large = true)
                        }
                        if (phase == RoundPhase.MEMORY) ActionButton(stringResource(R.string.next_button), { viewModel.next(id) }, enabled = !busy)
                    }
                    RoundPhase.WAIT -> {
                        PageTitle(stringResource(R.string.wait_prompt))
                        Countdown(state.round.remainingStageMs ?: 0)
                        Box(Modifier.height(180.dp))
                    }
                    RoundPhase.OPTIONS_PENDING, RoundPhase.SOLVE -> {
                        PageTitle(stringResource(R.string.solve_prompt))
                        Text(stringResource(R.string.attempts_left, state.round.attemptsRemaining),
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        if (state.problem.conditions.solveLimitMs == null) Text(stringResource(R.string.unlimited))
                        else Countdown(state.round.remainingStageMs ?: state.problem.conditions.solveLimitMs!!)
                        if (state.round.choices.isNotEmpty()) Text(stringResource(R.string.wrong_feedback))
                        state.problem.options.chunked(state.problem.conditions.layout.columns).forEach { options ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                options.forEach { option ->
                                    val wrong = option.id in state.round.disabledOptionIds
                                    Surface(
                                        onClick = { viewModel.answer(id, option.id) },
                                        enabled = phase == RoundPhase.SOLVE && !wrong && !busy,
                                        modifier = Modifier.weight(1f).heightIn(min = 150.dp).testTag("option-${option.id}"),
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (wrong) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(if (wrong) 2.dp else 1.dp, MaterialTheme.colorScheme.outline),
                                    ) {
                                        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(stringResource(R.string.choice_number, state.problem.options.indexOf(option) + 1),
                                                style = MaterialTheme.typography.bodyMedium)
                                            MemoryItemView(option.item, Modifier.fillMaxWidth().height(100.dp))
                                            if (wrong) Text(stringResource(R.string.wrong_choice), style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    RoundPhase.FINISHED -> {
                        val result = checkNotNull(state.round.result)
                        if (result.outcome == RoundOutcome.INTERRUPTED) {
                            PageTitle(stringResource(R.string.interrupted_title))
                            Text(stringResource(R.string.interrupted_description))
                            ActionButton(stringResource(R.string.resume_question), { viewModel.resume(id) }, enabled = !busy)
                        } else {
                            PageTitle(stringResource(when (result.outcome) {
                                RoundOutcome.CORRECT -> R.string.correct_result
                                RoundOutcome.TIMEOUT -> R.string.timeout_result
                                else -> R.string.wrong_result
                            }))
                            Text(stringResource(R.string.answer_label))
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                MemoryItemView(state.problem.target, Modifier.size(160.dp), large = true)
                            }
                            ActionButton(stringResource(if (state.complete) R.string.show_results else R.string.next_question),
                                { viewModel.advance(id) }, enabled = !busy)
                        }
                    }
                }
            }
            ActionButton(stringResource(R.string.learn_return), { viewModel.pause(sessionId); onHome() }, primary = false, enabled = !busy)
        }
    }
}

@Composable
private fun Countdown(milliseconds: Long) {
    Text(stringResource(R.string.seconds_left, (milliseconds + 999) / 1000), style = MaterialTheme.typography.bodyLarge)
}
