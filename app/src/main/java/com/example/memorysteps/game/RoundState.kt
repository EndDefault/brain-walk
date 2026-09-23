package com.example.memorysteps.game

/** A process-local monotonic clock; never use a wall-clock timestamp for answers. */
fun interface MonotonicClock {
    fun nowMillis(): Long
}

enum class RoundPhase { READY, MEMORY, WAIT, OPTIONS_PENDING, SOLVE, FINISHED }
enum class RoundOutcome { CORRECT, ATTEMPTS_EXHAUSTED, TIMEOUT, INTERRUPTED }
enum class RejectionReason { WRONG_PROBLEM, NOT_SOLVING, UNKNOWN_OPTION, ALREADY_CHOSEN }

data class ChoiceRecord(
    val optionId: String,
    val item: MemoryItem,
    val correct: Boolean,
    val elapsedMs: Long,
    val monotonicTimestampMs: Long,
)

class RoundResult internal constructor(
    val problemId: String,
    val outcome: RoundOutcome,
    val actualMemoryMs: Long?,
    val usedNextButton: Boolean,
    val solveElapsedMs: Long?,
    val choices: List<ChoiceRecord>,
) {
    val valid: Boolean get() = outcome != RoundOutcome.INTERRUPTED
    val finalCorrect: Boolean get() = outcome == RoundOutcome.CORRECT
    val firstChoiceCorrect: Boolean get() = choices.firstOrNull()?.correct == true
    val firstChoiceMs: Long? get() = choices.firstOrNull()?.elapsedMs
    val attemptCount: Int get() = choices.size
}

class RoundSnapshot internal constructor(
    val problemId: String,
    val phase: RoundPhase,
    val remainingStageMs: Long?,
    val actualMemoryMs: Long?,
    val usedNextButton: Boolean,
    val choices: List<ChoiceRecord>,
    val result: RoundResult?,
) {
    val attemptsRemaining: Int get() = 3 - choices.size
    val disabledOptionIds: Set<String> get() = choices.filterNot { it.correct }.map { it.optionId }.toSet()
}

sealed interface AnswerResponse {
    val snapshot: RoundSnapshot

    data class Accepted(val choice: ChoiceRecord, override val snapshot: RoundSnapshot) : AnswerResponse
    data class Rejected(val reason: RejectionReason, override val snapshot: RoundSnapshot) : AnswerResponse
}
