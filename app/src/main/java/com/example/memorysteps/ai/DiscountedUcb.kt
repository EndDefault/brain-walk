package com.example.memorysteps.ai

import com.example.memorysteps.difficulty.LearningAction
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

data class ArmState(val hasEverApplied: Boolean = false, val effectiveCount: Double = 0.0, val rewardSum: Double = 0.0) {
    init {
        require(effectiveCount.isFinite() && effectiveCount >= 0)
        require(rewardSum.isFinite() && rewardSum >= 0 && rewardSum <= effectiveCount + 1e-9)
        require(effectiveCount != 0.0 || rewardSum == 0.0)
    }
}
data class BanditChoice(val action: LearningAction, val reason: String, val scores: Map<LearningAction, Double>)

object DiscountedUcb {
    const val VERSION = "discounted-ucb-v1"
    const val DISCOUNT = 0.95
    const val EXPLORATION = 0.3
    val actions = listOf(LearningAction.KEEP, LearningAction.ADJUST_MEMORY, LearningAction.ADJUST_SOLVE)

    fun reward(firstCorrect: Int): Double {
        require(firstCorrect in 0..10)
        return maxOf(0.0, 1.0 - abs(firstCorrect / 10.0 - 0.8) / 0.8)
    }

    fun choose(allowed: Set<LearningAction>, state: Map<LearningAction, ArmState>): BanditChoice {
        require(allowed.isNotEmpty() && allowed.all { it in actions })
        val eligible = actions.filter { it in allowed }
        val untried = eligible.firstOrNull { state[it]?.hasEverApplied != true }
        if (untried != null) return BanditChoice(untried, "UNTRIED", emptyMap())
        val total = actions.sumOf { state[it]?.effectiveCount ?: 0.0 }
        val scores = eligible.associateWith { action ->
            val arm = state.getValue(action)
            if (arm.effectiveCount == 0.0) Double.POSITIVE_INFINITY
            else arm.rewardSum / arm.effectiveCount + EXPLORATION * sqrt(ln(1 + total) / arm.effectiveCount)
        }
        // maxBy returns the first maximum, preserving the specified action priority.
        return BanditChoice(eligible.maxBy { scores.getValue(it) }, "UCB", scores)
    }

    /** Called once for the next completed bundle, never for the input bundle. */
    fun update(state: Map<LearningAction, ArmState>, selected: LearningAction, reward: Double): Map<LearningAction, ArmState> {
        require(selected in actions && reward in 0.0..1.0)
        return actions.associateWith { action ->
            val old = state[action] ?: ArmState()
            val count = old.effectiveCount * DISCOUNT
            val sum = if (count == 0.0) 0.0 else old.rewardSum * DISCOUNT
            ArmState(old.hasEverApplied || action == selected, count + if (action == selected) 1 else 0,
                sum + if (action == selected) reward else 0.0)
        }
    }
}
