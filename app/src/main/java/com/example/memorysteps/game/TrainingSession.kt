package com.example.memorysteps.game

import kotlin.random.Random
import java.util.UUID

enum class TrainingMode(val singleType: GameType?) {
    MIXED(null), COLOR(GameType.COLOR), PICTURE(GameType.PICTURE), NUMBER(GameType.NUMBER),
}

data class CompletedProblem(val type: GameType, val result: RoundResult)

class TrainingSnapshot internal constructor(
    val sessionId: String,
    val mode: TrainingMode,
    val problem: MemoryProblem,
    val round: RoundSnapshot,
    val questionNumber: Int,
    val completed: List<CompletedProblem>,
    val showSummary: Boolean,
) {
    val total: Int get() = 10
    val complete: Boolean get() = completed.size == total
    val firstCorrect: Int get() = completed.count { it.result.firstChoiceCorrect }
    val finalCorrect: Int get() = completed.count { it.result.finalCorrect }
}

/** In-memory cycle. Durable records and adaptive conditions belong to the later repository. */
class TrainingSession(
    private val mode: TrainingMode,
    completedMixedCycles: Int,
    private val clock: MonotonicClock,
    private val generator: ProblemGenerator = ProblemGenerator(),
    random: Random = Random.Default,
    private val conditions: GameConditions = GameConditions(),
) {
    private val sessionId = UUID.randomUUID().toString()
    val plan: List<GameType> = frozenCopy(
        mode.singleType?.let { type -> List(10) { type } } ?: run {
            require(completedMixedCycles >= 0)
            val extraType = GameType.entries[completedMixedCycles % 3]
            GameType.entries.flatMap { type -> List(if (type == extraType) 4 else 3) { type } }.shuffled(random)
        },
    )
    private var index = 0
    private var round = GameRound(generator.generate(plan[index], conditions), clock)
    private val completed = mutableListOf<CompletedProblem>()
    private var showSummary = false

    val state: TrainingSnapshot get() = TrainingSnapshot(
        sessionId, mode, round.problem, round.state, index + 1, frozenCopy(completed), showSummary,
    )

    fun memoryShown(id: String) = update(id) { onMemoryShown() }
    fun optionsShown(id: String) = update(id) { onOptionsShown() }
    fun next(id: String) = update(id) { next() }
    fun answer(id: String, optionId: String) = update(id) { answer(id, optionId) }
    fun tick() = update(round.problem.id) { tick() }
    fun interrupt() = update(round.problem.id) { interrupt() }

    fun advance(id: String): TrainingSnapshot {
        if (id != round.problem.id || showSummary) return state
        val result = round.state.result ?: return state
        if (!result.valid) return state
        if (index == plan.lastIndex) showSummary = true else {
            index++
            round = GameRound(generator.generate(plan[index], conditions), clock)
        }
        return state
    }

    fun resume(id: String): TrainingSnapshot {
        if (id == round.problem.id && round.state.result?.outcome == RoundOutcome.INTERRUPTED) {
            round = GameRound(generator.generate(plan[index], conditions, round.problem.target), clock)
        }
        return state
    }

    private fun update(id: String, event: GameRound.() -> Unit): TrainingSnapshot {
        if (id != round.problem.id || showSummary) return state
        round.event()
        val result = round.state.result
        if (result != null && result.valid && completed.none { it.result.problemId == id }) {
            completed += CompletedProblem(round.problem.type, result)
        }
        return state
    }
}
