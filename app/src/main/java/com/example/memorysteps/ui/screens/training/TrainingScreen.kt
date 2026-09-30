package com.example.memorysteps.ui.screens.training

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.memorysteps.R
import com.example.memorysteps.game.RoundOutcome
import com.example.memorysteps.game.RoundPhase
import com.example.memorysteps.game.TrainingSnapshot
import com.example.memorysteps.ui.components.ActionButton
import com.example.memorysteps.ui.components.ActionButtonSize
import com.example.memorysteps.ui.components.MemoryItemView
import com.example.memorysteps.ui.components.typeName
import com.example.memorysteps.ui.theme.AppDimensions
import com.example.memorysteps.ui.theme.GameTextStyles
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun TrainingScreen(state: TrainingSnapshot, viewModel: TrainingViewModel, onBack: () -> Unit, onHome: () -> Unit) {
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    // Outgoing navigation entries must not interrupt a newly started cycle.
    val sessionId = remember(owner) { state.sessionId }
    var menuOpen by rememberSaveable(sessionId) { mutableStateOf(false) }
    val id = state.problem.id
    val phase = state.round.phase
    val openMenu = { menuOpen = true; viewModel.pause(sessionId) }
    BackHandler(enabled = !menuOpen, onBack = openMenu)
    DisposableEffect(owner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) viewModel.pause(sessionId)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); viewModel.pause(sessionId) }
    }
    LaunchedEffect(owner, viewModel, menuOpen) {
        if (!menuOpen) owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) { delay(100); viewModel.tick() }
        }
    }
    LaunchedEffect(id, phase, state.showSummary, menuOpen) {
        if (!menuOpen && !state.showSummary && (phase == RoundPhase.READY || phase == RoundPhase.OPTIONS_PENDING)) {
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                withFrameNanos { }; withFrameNanos { }
                if (phase == RoundPhase.READY) viewModel.memoryShown(id) else viewModel.optionsShown(id)
                awaitCancellation()
            }
        }
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.safeDrawingPadding().fillMaxSize().testTag("training-frame"), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.widthIn(max = AppDimensions.gameMaxWidth).fillMaxWidth().padding(horizontal = AppDimensions.pagePadding, vertical = AppDimensions.smallGap),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppDimensions.itemGap)) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.question_progress, state.questionNumber, typeName(state.problem.type)),
                        Modifier.testTag("question-progress"), style = MaterialTheme.typography.titleLarge)
                    if (state.practice) Text(stringResource(R.string.practice_label), style = MaterialTheme.typography.bodyMedium)
                }
                ActionButton(
                    text = stringResource(R.string.pause_button),
                    onClick = openMenu,
                    modifier = Modifier.testTag("pause-button"),
                    primary = false,
                    fillWidth = false,
                )
            }
            // Only the stage body scrolls. The primary action never moves with the image.
            BoxWithConstraints(Modifier.weight(1f).widthIn(max = AppDimensions.gameMaxWidth).fillMaxWidth().testTag("training-stage")) {
                val viewportHeight = maxHeight
                val viewportWidth = maxWidth
                val density = LocalDensity.current
                val wideControls = maxWidth >= AppDimensions.wideLayoutMinWidth * density.fontScale && density.fontScale <= AppDimensions.wideLayoutMaxFontScale
                val timed = state.problem.conditions.solveLimitMs != null
                val controlHeight = with(density) {
                    val statusHeight = MaterialTheme.typography.headlineMedium.lineHeight.toDp()
                    val countdownHeight = GameTextStyles.countdown.lineHeight.toDp()
                    val timerHeight = statusHeight + countdownHeight + AppDimensions.tinyGap
                    MaterialTheme.typography.headlineLarge.lineHeight.toDp() +
                        if (wideControls) (if (timed) countdownHeight else statusHeight)
                        else statusHeight + if (timed) timerHeight + AppDimensions.itemGap else 0.dp
                }
                val rows = state.problem.conditions.layout.rows
                val tileHeight = ((viewportHeight - controlHeight - (AppDimensions.pagePadding * 2 + AppDimensions.itemGap) - AppDimensions.itemGap * (rows - 1)) / rows)
                    .coerceIn(AppDimensions.optionMinSize, AppDimensions.optionMaxHeight)
                val targetSize = minOf(maxWidth - AppDimensions.pagePadding * 2, (maxHeight * 0.52f).coerceIn(AppDimensions.targetMinSize, AppDimensions.targetMaxSize))
                key(id, phase, state.showSummary) {
                    Column(Modifier.fillMaxWidth().testTag("stage-scroll").verticalScroll(rememberScrollState()).heightIn(min = viewportHeight)
                        .padding(horizontal = AppDimensions.pagePadding, vertical = AppDimensions.itemGap),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppDimensions.itemGap, Alignment.CenterVertically)) {
                        // Conceal the target/options before the pause transaction completes.
                        if (!menuOpen) when {
                            state.showSummary -> TrainingSummary(state)
                            phase == RoundPhase.READY || phase == RoundPhase.MEMORY -> {
                                StageTitle(stringResource(R.string.memory_prompt))
                                Countdown(state.round.remainingStageMs ?: state.problem.conditions.memoryLimitMs)
                                MemoryItemView(state.problem.target, Modifier.size(targetSize).testTag("memory-target"), large = true)
                            }
                            phase == RoundPhase.WAIT -> {
                                StageTitle(stringResource(R.string.wait_prompt))
                                Countdown(state.round.remainingStageMs ?: 0, prominent = true)
                            }
                            phase == RoundPhase.OPTIONS_PENDING || phase == RoundPhase.SOLVE -> {
                                StageTitle(stringResource(R.string.solve_prompt))
                                if (wideControls) Row(horizontalArrangement = Arrangement.spacedBy(AppDimensions.wideGap), verticalAlignment = Alignment.CenterVertically) {
                                    AttemptsRemaining(state.round.attemptsRemaining)
                                    state.problem.conditions.solveLimitMs?.let { SolveCountdown(state.round.remainingStageMs ?: it, inlineLabel = true) }
                                } else {
                                    AttemptsRemaining(state.round.attemptsRemaining)
                                    state.problem.conditions.solveLimitMs?.let { SolveCountdown(state.round.remainingStageMs ?: it) }
                                }
                                AnswerOptions(state, busy, tileHeight, viewportWidth - AppDimensions.pagePadding * 2) { viewModel.answer(id, it) }
                            }
                            phase == RoundPhase.FINISHED -> {
                                val result = checkNotNull(state.round.result)
                                when (result.outcome) {
                                    RoundOutcome.CORRECT -> Text(stringResource(R.string.correct_result),
                                        Modifier.fillMaxWidth().testTag("correct-feedback").semantics { liveRegion = LiveRegionMode.Polite },
                                        style = MaterialTheme.typography.displayMedium,
                                        color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
                                    RoundOutcome.INTERRUPTED -> {
                                        StageTitle(stringResource(R.string.interrupted_title))
                                        Text(stringResource(R.string.interrupted_description), textAlign = TextAlign.Center)
                                    }
                                    else -> {
                                        StageTitle(stringResource(if (result.outcome == RoundOutcome.TIMEOUT) R.string.timeout_result else R.string.wrong_result))
                                        Text(stringResource(R.string.answer_label), textAlign = TextAlign.Center)
                                        MemoryItemView(state.problem.target, Modifier.size(targetSize), large = true)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (!menuOpen) {
                val label = when {
                    state.showSummary -> R.string.home_button
                    phase == RoundPhase.READY || phase == RoundPhase.MEMORY -> R.string.next_button
                    phase == RoundPhase.FINISHED && state.round.result?.outcome == RoundOutcome.INTERRUPTED -> R.string.resume_question
                    phase == RoundPhase.FINISHED -> if (state.complete) R.string.show_results else R.string.next_question
                    else -> null
                }
                if (label != null) Box(Modifier.widthIn(max = AppDimensions.gameActionMaxWidth).fillMaxWidth().padding(horizontal = AppDimensions.pagePadding, vertical = AppDimensions.itemGap)) {
                    ActionButton(
                        text = stringResource(label),
                        onClick = {
                            when {
                                state.showSummary -> onHome()
                                phase == RoundPhase.MEMORY -> viewModel.next(id)
                                state.round.result?.outcome == RoundOutcome.INTERRUPTED -> viewModel.resume(id)
                                phase == RoundPhase.FINISHED -> viewModel.advance(id)
                            }
                        },
                        enabled = !busy && phase != RoundPhase.READY,
                        modifier = Modifier.testTag("stage-action"),
                        size = ActionButtonSize.Large,
                    )
                }
            }
        }
    }
    if (menuOpen) PauseMenu(
        restart = state.round.result?.outcome == RoundOutcome.INTERRUPTED,
        enabled = !busy && phase == RoundPhase.FINISHED,
        onContinue = {
            if (state.round.result?.outcome == RoundOutcome.INTERRUPTED) viewModel.resume(id)
            menuOpen = false
        },
        onBack = { viewModel.pause(sessionId); onBack() },
        onHome = { viewModel.pause(sessionId); onHome() },
    )
}
