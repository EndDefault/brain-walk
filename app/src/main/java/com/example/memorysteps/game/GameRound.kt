package com.example.memorysteps.game

/**
 * One problem, owned by a single event dispatcher (the future ViewModel).
 * No UI, scheduler, persistence, or Android clock dependency lives here.
 * Call display callbacks only after their content is actually visible.
 */
class GameRound(val problem: MemoryProblem, private val clock: MonotonicClock) {
    companion object {
        /** Restore only terminal data; never resume a timer from another process. */
        fun restore(problem: MemoryProblem, clock: MonotonicClock, result: RoundResult): GameRound {
            require(result.problemId == problem.id)
            return GameRound(problem, clock).apply {
                this.result = result
                phase = RoundPhase.FINISHED
                actualMemoryMs = result.actualMemoryMs
                usedNextButton = result.usedNextButton
                choices.addAll(result.choices)
            }
        }
    }
    private var phase = RoundPhase.READY
    private var lastObservedMs: Long? = null
    private var memoryStartedMs: Long? = null
    private var waitStartedMs: Long? = null
    private var solveStartedMs: Long? = null
    private var actualMemoryMs: Long? = null
    private var usedNextButton = false
    private val choices = mutableListOf<ChoiceRecord>()
    private var result: RoundResult? = null

    /** Reading a snapshot never advances time. Use tick() to process clock changes. */
    val state: RoundSnapshot get() = snapshot()

    fun onMemoryShown(): RoundSnapshot {
        if (phase == RoundPhase.FINISHED) return state
        val now = observeTime()
        advanceTime(now)
        if (phase == RoundPhase.READY) {
            memoryStartedMs = now
            phase = RoundPhase.MEMORY
        }
        return state
    }

    fun next(): RoundSnapshot {
        if (phase == RoundPhase.FINISHED) return state
        val now = observeTime()
        advanceTime(now)
        if (phase == RoundPhase.MEMORY) endMemory(now, early = true)
        return state
    }

    fun tick(): RoundSnapshot {
        if (phase == RoundPhase.FINISHED) return state
        advanceTime(observeTime())
        return state
    }

    /** Options preparation/layout time must not count as solve time. */
    fun onOptionsShown(): RoundSnapshot {
        if (phase == RoundPhase.FINISHED) return state
        val now = observeTime()
        advanceTime(now)
        if (phase == RoundPhase.OPTIONS_PENDING) {
            solveStartedMs = now
            phase = RoundPhase.SOLVE
        }
        return state
    }

    fun answer(problemId: String, optionId: String): AnswerResponse {
        if (problemId != problem.id) return reject(RejectionReason.WRONG_PROBLEM)
        if (phase != RoundPhase.FINISHED) advanceTime(observeTime())
        if (phase != RoundPhase.SOLVE) return reject(RejectionReason.NOT_SOLVING)
        val option = problem.options.firstOrNull { it.id == optionId }
            ?: return reject(RejectionReason.UNKNOWN_OPTION)
        if (choices.any { it.optionId == optionId }) return reject(RejectionReason.ALREADY_CHOSEN)

        val now = checkNotNull(lastObservedMs)
        val choice = ChoiceRecord(
            option.id,
            option.item,
            option.item == problem.target,
            now - checkNotNull(solveStartedMs),
            now,
        )
        choices += choice
        when {
            choice.correct -> finish(RoundOutcome.CORRECT, now)
            choices.size == 3 -> finish(RoundOutcome.ATTEMPTS_EXHAUSTED, now)
        }
        return AnswerResponse.Accepted(choice, state)
    }

    /** A hidden or abandoned unfinished question is invalid, never a scored failure. */
    fun interrupt(): RoundSnapshot {
        if (phase == RoundPhase.FINISHED) return state
        finish(RoundOutcome.INTERRUPTED, observeTime())
        return state
    }

    private fun observeTime(): Long {
        val now = clock.nowMillis()
        val previous = lastObservedMs
        check(previous == null || now >= previous) { "Monotonic clock moved backwards" }
        lastObservedMs = now
        return now
    }

    private fun advanceTime(now: Long) {
        when (phase) {
            RoundPhase.MEMORY -> if (now - checkNotNull(memoryStartedMs) >= problem.conditions.memoryLimitMs) {
                endMemory(now, early = false)
            }
            RoundPhase.WAIT -> if (now - checkNotNull(waitStartedMs) >= problem.conditions.waitMs) {
                phase = RoundPhase.OPTIONS_PENDING
            }
            RoundPhase.SOLVE -> {
                val limit = problem.conditions.solveLimitMs
                if (limit != null && now - checkNotNull(solveStartedMs) >= limit) {
                    finish(RoundOutcome.TIMEOUT, now)
                }
            }
            else -> Unit
        }
    }

    private fun endMemory(now: Long, early: Boolean) {
        // If a tick was delayed, preserve actual exposure rather than the nominal limit.
        actualMemoryMs = now - checkNotNull(memoryStartedMs)
        usedNextButton = early
        waitStartedMs = now
        phase = RoundPhase.WAIT
    }

    private fun finish(outcome: RoundOutcome, now: Long) {
        result = RoundResult(
            problem.id,
            outcome,
            actualMemoryMs ?: memoryStartedMs?.let { now - it },
            usedNextButton,
            solveStartedMs?.let { now - it },
            frozenCopy(choices),
        )
        phase = RoundPhase.FINISHED
    }

    private fun reject(reason: RejectionReason) = AnswerResponse.Rejected(reason, state)

    private fun snapshot(): RoundSnapshot {
        fun remaining(limit: Long, start: Long?): Long =
            (limit - (checkNotNull(lastObservedMs) - checkNotNull(start))).coerceAtLeast(0L)

        val remaining = when (phase) {
            RoundPhase.MEMORY -> remaining(problem.conditions.memoryLimitMs, memoryStartedMs)
            RoundPhase.WAIT -> remaining(problem.conditions.waitMs, waitStartedMs)
            RoundPhase.SOLVE -> problem.conditions.solveLimitMs?.let { remaining(it, solveStartedMs) }
            else -> null
        }
        return RoundSnapshot(
            problem.id, phase, remaining, actualMemoryMs ?: result?.actualMemoryMs,
            usedNextButton, frozenCopy(choices), result,
        )
    }
}
