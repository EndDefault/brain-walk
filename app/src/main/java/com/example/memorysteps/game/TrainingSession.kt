package com.example.memorysteps.game

import kotlin.random.Random
import java.util.UUID

enum class TrainingMode(val singleType: GameType?) {
    MIXED(null), COLOR(GameType.COLOR), PICTURE(GameType.PICTURE), NUMBER(GameType.NUMBER),
}

data class CompletedProblem(val type: GameType, val result: RoundResult)

data class SessionCheckpoint(
    val sessionId: String,
    val mode: TrainingMode,
    val plan: List<GameType>,
    val index: Int,
    val problem: MemoryProblem,
    val result: RoundResult,
    val completed: List<CompletedProblem>,
    val showSummary: Boolean,
)

class TrainingSnapshot internal constructor(
    val sessionId: String,
    val mode: TrainingMode,
    val problem: MemoryProblem,
    val round: RoundSnapshot,
    val questionNumber: Int,
    val completed: List<CompletedProblem>,
    val showSummary: Boolean,
    val practice: Boolean,
) {
    val total: Int get() = 10
    val complete: Boolean get() = completed.size == total
    val firstCorrect: Int get() = completed.count { it.result.firstChoiceCorrect }
    val finalCorrect: Int get() = completed.count { it.result.finalCorrect }
}

/** Pure game state. The repository checkpoints formal training before publishing it. */
class TrainingSession(
    private val mode: TrainingMode,
    completedMixedCycles: Int,
    private val clock: MonotonicClock,
    private val generator: ProblemGenerator = ProblemGenerator(),
    random: Random = Random.Default,
    private val conditions: GameConditions = GameConditions(),
    private val practice: Boolean = false,
    private val restored: SessionCheckpoint? = null,
) {
    private val sessionId = restored?.sessionId ?: UUID.randomUUID().toString()
    val plan: List<GameType> = frozenCopy(
        restored?.plan ?: mode.singleType?.let { type -> List(10) { type } } ?: run {
            require(completedMixedCycles >= 0)
            val extraType = GameType.entries[completedMixedCycles % 3]
            GameType.entries.flatMap { type -> List(if (type == extraType) 4 else 3) { type } }.shuffled(random)
        },
    )
    private var index = restored?.index ?: 0
    private var round = restored?.let { GameRound.restore(it.problem, clock, it.result) }
        ?: GameRound(generator.generate(plan[index], conditions), clock)
    private val completed = restored?.completed?.toMutableList() ?: mutableListOf()
    private var showSummary = restored?.showSummary ?: false

    init {
        require(plan.size == 10 && index in plan.indices)
        require(restored == null || (restored.mode == mode && restored.problem.type == plan[index]))
    }

    companion object {
        fun restore(checkpoint: SessionCheckpoint, clock: MonotonicClock) = TrainingSession(
            checkpoint.mode, 0, clock, conditions = checkpoint.problem.conditions, restored = checkpoint,
        )
    }

    val state: TrainingSnapshot get() = TrainingSnapshot(
        sessionId, mode, round.problem, round.state, index + 1, frozenCopy(completed), showSummary, practice,
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
