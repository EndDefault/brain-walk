package com.example.memorysteps.ai

import com.example.memorysteps.difficulty.LearningAction.*
import org.junit.Assert.*
import org.junit.Test

class DiscountedUcbTest {
    @Test fun rewardsMatchNextBundleExamples() {
        mapOf(0 to 0.0, 7 to 0.875, 8 to 1.0, 9 to 0.875, 10 to 0.75).forEach { (c, reward) ->
            assertEquals(reward, DiscountedUcb.reward(c), 1e-12)
        }
    }
    @Test fun exploresOnlyFeasibleActionsInStableOrder() {
        assertEquals(KEEP, DiscountedUcb.choose(setOf(ADJUST_SOLVE, ADJUST_MEMORY, KEEP), emptyMap()).action)
        assertEquals(ADJUST_MEMORY, DiscountedUcb.choose(setOf(ADJUST_SOLVE, ADJUST_MEMORY), emptyMap()).action)
        assertEquals(ADJUST_SOLVE, DiscountedUcb.choose(setOf(ADJUST_SOLVE), emptyMap()).action)
    }
    @Test fun triesUnseenBeforePreviouslyAppliedButUnrewardedActions() {
        val states = mapOf(KEEP to ArmState(true), ADJUST_MEMORY to ArmState(false), ADJUST_SOLVE to ArmState(true, 2.0, 2.0))
        assertEquals(ADJUST_MEMORY, DiscountedUcb.choose(DiscountedUcb.actions.toSet(), states).action)
        val afterApplied = states + (ADJUST_MEMORY to ArmState(true, 1.0, 0.8))
        val result = DiscountedUcb.choose(DiscountedUcb.actions.toSet(), afterApplied)
        assertEquals(KEEP, result.action)
        assertEquals(Double.POSITIVE_INFINITY, result.scores.getValue(KEEP), 0.0)
    }
    @Test fun learnedScoreTiesUseActionPriority() {
        val states = DiscountedUcb.actions.associateWith { ArmState(true, 1.0, 0.8) }
        assertEquals(KEEP, DiscountedUcb.choose(DiscountedUcb.actions.toSet(), states).action)
        assertEquals(ADJUST_MEMORY, DiscountedUcb.choose(setOf(ADJUST_MEMORY, ADJUST_SOLVE), states).action)
    }
    @Test fun confidenceUsesAllTypeActionsNotOnlyAllowedOnes() {
        val states = mapOf(KEEP to ArmState(true, 10.0, 5.0), ADJUST_MEMORY to ArmState(true, 1.0, 0.5), ADJUST_SOLVE to ArmState(true, 2.0, 1.0))
        val result = DiscountedUcb.choose(setOf(ADJUST_MEMORY), states)
        assertEquals(0.5 + 0.3 * kotlin.math.sqrt(kotlin.math.ln(14.0)), result.scores.getValue(ADJUST_MEMORY), 1e-12)
    }
    @Test fun discountsAllArmsThenAddsOneNewReward() {
        val updated = DiscountedUcb.update(mapOf(KEEP to ArmState(true, 2.0, 1.5), ADJUST_MEMORY to ArmState(true, 1.0, 0.75)), ADJUST_MEMORY, 1.0)
        assertEquals(1.9, updated.getValue(KEEP).effectiveCount, 1e-12)
        assertEquals(1.425, updated.getValue(KEEP).rewardSum, 1e-12)
        assertEquals(1.95, updated.getValue(ADJUST_MEMORY).effectiveCount, 1e-12)
        assertEquals(1.7125, updated.getValue(ADJUST_MEMORY).rewardSum, 1e-12)
        assertFalse(updated.getValue(ADJUST_SOLVE).hasEverApplied)
    }
    @Test fun zeroCountsRemainFiniteAndDoNotCreateSpuriousRewards() {
        val updated = DiscountedUcb.update(mapOf(KEEP to ArmState(true)), ADJUST_SOLVE, 0.0)
        assertEquals(ArmState(true), updated.getValue(KEEP))
        assertEquals(ArmState(true, 1.0, 0.0), updated.getValue(ADJUST_SOLVE))
    }
}
