package com.example.memorysteps.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.memorysteps.R
import com.example.memorysteps.game.*
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun typeName(type: GameType): String = stringResource(when (type) {
    GameType.COLOR -> R.string.type_color
    GameType.PICTURE -> R.string.type_picture
    GameType.NUMBER -> R.string.type_number
})

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
            Row(Modifier.widthIn(max = 1100.dp).fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.question_progress, state.questionNumber, typeName(state.problem.type)),
                        Modifier.testTag("question-progress"), style = MaterialTheme.typography.titleLarge)
                    if (state.practice) Text(stringResource(R.string.practice_label), style = MaterialTheme.typography.bodyMedium)
                }
                OutlinedButton(openMenu, Modifier.heightIn(min = 64.dp).testTag("pause-button"),
                    shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(16.dp),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)) {
                    Text(stringResource(R.string.pause_button), textAlign = TextAlign.Center)
                }
            }
            // Only the stage body scrolls. The primary action never moves with the image.
            BoxWithConstraints(Modifier.weight(1f).widthIn(max = 1100.dp).fillMaxWidth().testTag("training-stage")) {
                val viewportHeight = maxHeight
                val viewportWidth = maxWidth
                val density = LocalDensity.current
                val wideControls = maxWidth >= 600.dp * density.fontScale && density.fontScale <= 1.3f
                val timed = state.problem.conditions.solveLimitMs != null
                val controlHeight = with(density) {
                    val timerHeight = 122.sp.toDp() + 4.dp
                    52.sp.toDp() + if (wideControls) (if (timed) 80.sp else 42.sp).toDp()
                    else 42.sp.toDp() + if (timed) timerHeight + 16.dp else 0.dp
                }
                val rows = state.problem.conditions.layout.rows
                val tileHeight = ((viewportHeight - controlHeight - 64.dp - 16.dp * (rows - 1)) / rows)
                    .coerceIn(96.dp, 184.dp)
                val targetSize = minOf(maxWidth - 48.dp, (maxHeight * 0.52f).coerceIn(220.dp, 340.dp))
                key(id, phase, state.showSummary) {
                    Column(Modifier.fillMaxWidth().testTag("stage-scroll").verticalScroll(rememberScrollState()).heightIn(min = viewportHeight)
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
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
                                if (wideControls) Row(horizontalArrangement = Arrangement.spacedBy(40.dp), verticalAlignment = Alignment.CenterVertically) {
                                    AttemptsRemaining(state.round.attemptsRemaining)
                                    state.problem.conditions.solveLimitMs?.let { SolveCountdown(state.round.remainingStageMs ?: it, inlineLabel = true) }
                                } else {
                                    AttemptsRemaining(state.round.attemptsRemaining)
                                    state.problem.conditions.solveLimitMs?.let { SolveCountdown(state.round.remainingStageMs ?: it) }
                                }
                                AnswerOptions(state, busy, tileHeight, viewportWidth - 48.dp) { viewModel.answer(id, it) }
                            }
                            phase == RoundPhase.FINISHED -> {
                                val result = checkNotNull(state.round.result)
                                when (result.outcome) {
                                    RoundOutcome.CORRECT -> Text(stringResource(R.string.correct_result),
                                        Modifier.fillMaxWidth().testTag("correct-feedback").semantics { liveRegion = LiveRegionMode.Polite },
                                        fontSize = 56.sp, lineHeight = 76.sp, fontWeight = FontWeight.Bold,
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
                if (label != null) Box(Modifier.widthIn(max = 800.dp).fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
                    Button(onClick = {
                        when {
                            state.showSummary -> onHome()
                            phase == RoundPhase.MEMORY -> viewModel.next(id)
                            state.round.result?.outcome == RoundOutcome.INTERRUPTED -> viewModel.resume(id)
                            phase == RoundPhase.FINISHED -> viewModel.advance(id)
                        }
                    }, enabled = !busy && phase != RoundPhase.READY,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp).testTag("stage-action"),
                        shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(20.dp)) {
                        Text(stringResource(label), fontSize = 28.sp, lineHeight = 38.sp, textAlign = TextAlign.Center)
                    }
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

@Composable
private fun StageTitle(text: String) {
    Text(text, Modifier.fillMaxWidth().semantics { heading() }, fontSize = 38.sp, lineHeight = 52.sp,
        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
}

@Composable
private fun Countdown(milliseconds: Long, prominent: Boolean = false) {
    val seconds = (milliseconds + 999) / 1000
    val description = stringResource(R.string.seconds_left, seconds)
    Text(seconds.toString(), Modifier.testTag(if (prominent) "wait-countdown" else "stage-countdown")
        .clearAndSetSemantics { contentDescription = description },
        fontSize = if (prominent) 160.sp else 64.sp, lineHeight = if (prominent) 190.sp else 80.sp,
        fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
}

@Composable
private fun SolveCountdown(milliseconds: Long, inlineLabel: Boolean = false) {
    val seconds = (milliseconds + 999) / 1000
    val description = stringResource(R.string.seconds_left, seconds)
    val modifier = Modifier.testTag("stage-countdown").clearAndSetSemantics { contentDescription = description }
    val label: @Composable () -> Unit = {
        Text(stringResource(R.string.remaining_time_label), fontSize = 30.sp, lineHeight = 42.sp,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
    val value: @Composable () -> Unit = {
        Text(stringResource(R.string.seconds_value, seconds), fontSize = 64.sp, lineHeight = 80.sp,
            fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
    }
    if (inlineLabel) Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        label(); value()
    } else Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        label(); value()
    }
}

@Composable
private fun AttemptsRemaining(count: Int) {
    Text(stringResource(R.string.attempts_left, count), Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        fontSize = 30.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
}

@Composable
private fun AnswerOptions(state: TrainingSnapshot, busy: Boolean, tileHeight: Dp, availableWidth: Dp, onAnswer: (String) -> Unit) {
    val columns = state.problem.conditions.layout.columns
    val minTileWidth = with(LocalDensity.current) { 64.sp.toDp() + 24.dp }.coerceAtLeast(96.dp)
    val gridWidth = maxOf(availableWidth, minTileWidth * columns + 16.dp * (columns - 1))
    val showNumbers = state.problem.conditions.layout.rows == 2
    Box(Modifier.fillMaxWidth().testTag("options-scroll").horizontalScroll(rememberScrollState())) {
        Column(Modifier.width(gridWidth), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            state.problem.options.chunked(columns).forEach { options ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    options.forEach { option ->
                        val wrong = option.id in state.round.disabledOptionIds
                        val wrongDescription = stringResource(R.string.wrong_choice)
                        val number = stringResource(R.string.choice_number, state.problem.options.indexOf(option) + 1)
                        Surface(onClick = { onAnswer(option.id) },
                            enabled = state.round.phase == RoundPhase.SOLVE && !wrong && !busy,
                            modifier = Modifier.weight(1f).heightIn(min = if (showNumbers) maxOf(164.dp, tileHeight) else tileHeight).testTag("option-${option.id}")
                                .semantics { if (wrong) stateDescription = wrongDescription; if (!showNumbers) contentDescription = number },
                            shape = RoundedCornerShape(8.dp),
                            color = if (wrong) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)) {
                            Column(Modifier.padding(8.dp).alpha(if (wrong) 0.4f else 1f),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (showNumbers) Text(number, style = MaterialTheme.typography.bodyMedium)
                                MemoryItemView(option.item, Modifier.fillMaxWidth().height(if (showNumbers) 124.dp else tileHeight - 16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrainingSummary(state: TrainingSnapshot) {
    StageTitle(stringResource(if (state.practice) R.string.practice_summary else R.string.summary_title))
    Text(stringResource(R.string.summary_first, state.firstCorrect), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
    Text(stringResource(R.string.summary_final, state.finalCorrect), textAlign = TextAlign.Center)
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

@Composable
private fun PauseMenu(restart: Boolean, enabled: Boolean, onContinue: () -> Unit, onBack: () -> Unit, onHome: () -> Unit) {
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(24.dp).widthIn(max = 520.dp).fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.background) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                StageTitle(stringResource(R.string.pause_title))
                if (restart) Text(stringResource(R.string.pause_notice), textAlign = TextAlign.Center)
                ActionButton(stringResource(if (restart) R.string.resume_question else R.string.pause_continue), onContinue, enabled = enabled)
                ActionButton(stringResource(R.string.pause_back), onBack, primary = false, enabled = enabled)
                ActionButton(stringResource(R.string.home_button), onHome, primary = false, enabled = enabled)
            }
        }
    }
}
