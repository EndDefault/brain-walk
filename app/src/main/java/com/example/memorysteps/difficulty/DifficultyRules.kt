package com.example.memorysteps.difficulty

import com.example.memorysteps.game.GameConditions
import com.example.memorysteps.game.ChoiceLayout

enum class LearningAction { KEEP, ADJUST_MEMORY, ADJUST_SOLVE, RESTORE_FULL, RESTORE_PARTIAL }
enum class AlgorithmMode { BANDIT, COMPARISON }
enum class ReductionAxis { MEMORY, SOLVE }

data class Performance(val firstCorrect: Boolean, val finalCorrect: Boolean,
    val actualMemoryMs: Long?, val solveMs: Long?, val attempts: Int)

data class BundleStatistics(
    val firstCorrect: Int, val memorySumMs: Long, val memoryCount: Int,
    val solveSumMs: Long, val weightedSumMs: Long, val solvedCount: Int,
) {
    val memoryMeanMs: Double? get() = if (memoryCount == 0) null else memorySumMs.toDouble() / memoryCount
    val solveMeanMs: Double? get() = if (solvedCount == 0) null else solveSumMs.toDouble() / solvedCount
    val weightedMeanMs: Double? get() = if (solvedCount == 0) null else weightedSumMs.toDouble() / solvedCount

    companion object {
        fun from(items: List<Performance>): BundleStatistics {
            require(items.size == 10)
            items.forEach {
                require(it.attempts in 0..3)
                require(!it.firstCorrect || (it.finalCorrect && it.attempts == 1))
                require(!it.finalCorrect || (it.attempts >= 1 && it.solveMs != null && it.solveMs >= 0))
                require(!it.firstCorrect || (it.actualMemoryMs != null && it.actualMemoryMs >= 0))
            }
            val first = items.filter { it.firstCorrect }
            val solved = items.filter { it.finalCorrect }
            return BundleStatistics(first.size, first.sumOf { it.actualMemoryMs!! }, first.size,
                solved.sumOf { it.solveMs!! }, solved.sumOf { it.solveMs!! * it.attempts }, solved.size)
        }
    }
}

data class Reduction(val id: Long, val axis: ReductionAxis, val remainingMs: Long)
data class Restoration(val reductionId: Long, val amountMs: Long)
data class Candidate(val action: LearningAction, val conditions: GameConditions)
data class RulePlan(val candidates: List<Candidate>, val fixed: Boolean,
    val restorations: List<Restoration> = emptyList(), val closeSolveHistory: Boolean = false)

object DifficultyRules {
    /** Round the rational millisecond mean to whole seconds, with positive halves up. */
    fun roundSeconds(sumMs: Long, count: Int = 1): Long {
        require(sumMs >= 0 && count > 0)
        return (sumMs + count * 500L) / (count * 1_000L)
    }

    fun evaluate(current: GameConditions, stats: BundleStatistics, reductions: List<Reduction> = emptyList()): RulePlan {
        require(stats.firstCorrect in 0..10)
        if (stats.firstCorrect <= 3) return restore(current, stats.firstCorrect <= 1, reductions)
        if (stats.firstCorrect <= 6) return RulePlan(listOf(Candidate(LearningAction.KEEP, current)), fixed = true)
        val candidates = buildList {
            if (stats.firstCorrect <= 8) add(Candidate(LearningAction.KEEP, current))
            if (stats.memoryCount > 0) {
                val seconds = (roundSeconds(stats.memorySumMs, stats.memoryCount) - if (stats.firstCorrect == 10) 1 else 0).coerceIn(5, 20)
                if (seconds * 1000 < current.memoryLimitMs) add(Candidate(LearningAction.ADJUST_MEMORY, current.copy(memoryLimitMs = seconds * 1000)))
            }
            solveCandidate(current, stats)?.takeIf { it != current }?.let { add(Candidate(LearningAction.ADJUST_SOLVE, it)) }
        }
        return if (candidates.isEmpty()) RulePlan(listOf(Candidate(LearningAction.KEEP, current)), fixed = true)
        else RulePlan(candidates, fixed = false)
    }

    private fun solveCandidate(current: GameConditions, stats: BundleStatistics): GameConditions? {
        val limit = current.solveLimitMs ?: return current.copy(solveLimitMs = 40_000)
        val next = if (stats.firstCorrect <= 8) {
            if (stats.solvedCount == 0) return null
            val threshold = limit + roundSeconds(limit, 2) * 1000
            if (stats.weightedSumMs >= threshold * stats.solvedCount) return null
            (roundSeconds(stats.solveSumMs, stats.solvedCount) + 2) * 1000
        } else limit - 5_000
        if (next >= limit) return null
        return when {
            next > 15_000 -> current.copy(solveLimitMs = next)
            current.optionCount < 16 -> {
                val layouts = ChoiceLayout.entries
                current.copy(optionCount = layouts[layouts.indexOf(current.layout) + 1].count, solveLimitMs = null)
            }
            else -> current.copy(solveLimitMs = 15_000, waitMs = minOf(10_000, current.waitMs + 1_000))
        }
    }

    private fun restore(current: GameConditions, full: Boolean, reductions: List<Reduction>): RulePlan {
        val action = if (full) LearningAction.RESTORE_FULL else LearningAction.RESTORE_PARTIAL
        if (current.waitMs > 3_000) return RulePlan(listOf(Candidate(action,
            current.copy(waitMs = maxOf(3_000, current.waitMs - if (full) 1_000 else 500)))), fixed = true)
        var next = current
        val events = mutableListOf<Restoration>()
        fun amount(axis: ReductionAxis, room: Long): Long {
            val entry = reductions.filter { it.axis == axis && it.remainingMs > 0 }.maxByOrNull { it.id } ?: return 0
            val requested = when {
                full -> entry.remainingMs
                axis == ReductionAxis.MEMORY -> roundSeconds(entry.remainingMs, 2) * 1000
                else -> (entry.remainingMs / 2_000) * 1000
            }
            val actual = minOf(room, requested)
            if (actual > 0) events += Restoration(entry.id, actual)
            return actual
        }
        next = next.copy(memoryLimitMs = current.memoryLimitMs + amount(ReductionAxis.MEMORY, 20_000 - current.memoryLimitMs))
        val clear = full && current.solveLimitMs == 40_000L
        if (clear) next = next.copy(solveLimitMs = null)
        else current.solveLimitMs?.let { next = next.copy(solveLimitMs = it + amount(ReductionAxis.SOLVE, 40_000 - it)) }
        return RulePlan(listOf(Candidate(action, next)), fixed = true, restorations = events, closeSolveHistory = clear)
    }

    fun comparison(plan: RulePlan, stats: BundleStatistics): Candidate {
        if (plan.fixed) return plan.candidates.single()
        val memoryFirst = stats.firstCorrect >= 9 || stats.solveMeanMs == null ||
            (stats.memoryMeanMs != null && stats.memorySumMs * stats.solvedCount <= stats.solveSumMs * stats.memoryCount)
        val order = if (memoryFirst) listOf(LearningAction.ADJUST_MEMORY, LearningAction.ADJUST_SOLVE, LearningAction.KEEP)
            else listOf(LearningAction.ADJUST_SOLVE, LearningAction.ADJUST_MEMORY, LearningAction.KEEP)
        return order.firstNotNullOf { action -> plan.candidates.firstOrNull { it.action == action } }
    }
}
