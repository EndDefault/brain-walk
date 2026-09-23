package com.example.memorysteps.difficulty

import com.example.memorysteps.game.GameConditions
import org.junit.Assert.*
import org.junit.Test

class DifficultyRulesTest {
    private fun stats(c: Int = 8, memory: Long = 12_500, solve: Long = 18_000, weighted: Long = solve) =
        BundleStatistics(c, memory * c, c, solve * 10, weighted * 10, 10)
    private fun candidate(c: GameConditions, s: BundleStatistics, action: LearningAction) =
        DifficultyRules.evaluate(c, s).candidates.firstOrNull { it.action == action }?.conditions

    @Test fun memoryRoundsHalfUpAndTenCorrectSubtractsOneSecond() {
        assertEquals(13_000L, candidate(GameConditions(), stats(), LearningAction.ADJUST_MEMORY)!!.memoryLimitMs)
        assertEquals(12_000L, candidate(GameConditions(), stats(c = 10), LearningAction.ADJUST_MEMORY)!!.memoryLimitMs)
        assertNull(candidate(GameConditions(memoryLimitMs = 5_000), stats(c = 10, memory = 5_400), LearningAction.ADJUST_MEMORY))
    }
    @Test fun memoryUsesActualExposureIncludingAutomaticAdvanceAndNoStepCap() {
        assertEquals(5_000L, candidate(GameConditions(), stats(c = 10, memory = 1_200), LearningAction.ADJUST_MEMORY)!!.memoryLimitMs)
        assertNull(candidate(GameConditions(), stats(memory = 20_600), LearningAction.ADJUST_MEMORY))
    }
    @Test fun sevenCorrectIntroducesFortySecondLimit() {
        assertEquals(40_000L, candidate(GameConditions(), stats(c = 7), LearningAction.ADJUST_SOLVE)!!.solveLimitMs)
    }
    @Test fun weightedThresholdIsStrictAndComparedBeforeRounding() {
        val c = GameConditions(solveLimitMs = 25_000)
        assertNull(candidate(c, stats(weighted = 38_000), LearningAction.ADJUST_SOLVE))
        assertEquals(20_000L, candidate(c, stats(weighted = 37_900), LearningAction.ADJUST_SOLVE)!!.solveLimitMs)
        assertNull(candidate(GameConditions(solveLimitMs = 20_000), stats(weighted = 20_000), LearningAction.ADJUST_SOLVE))
    }
    @Test fun fifteenSecondBoundaryChangesOnlySelectedSolveCandidate() {
        val c = GameConditions(solveLimitMs = 20_000)
        assertEquals(c.copy(solveLimitMs = 16_000), candidate(c, stats(solve = 13_500), LearningAction.ADJUST_SOLVE))
        assertEquals(c.copy(optionCount = 6, solveLimitMs = null), candidate(c, stats(solve = 13_000), LearningAction.ADJUST_SOLVE))
        assertEquals(c.copy(optionCount = 6, solveLimitMs = null), candidate(c, stats(solve = 2_000), LearningAction.ADJUST_SOLVE))
        // Rules only propose values: the input remains unchanged and memory is separate.
        assertEquals(20_000L, c.memoryLimitMs)
        assertEquals(4, c.optionCount)
    }
    @Test fun nineCorrectIgnoresWeightedGateAndAdvancesOneOptionStep() {
        listOf(4 to 6, 6 to 9, 9 to 12, 12 to 16).forEach { (from, to) ->
            val c = GameConditions(optionCount = from, solveLimitMs = 20_000)
            assertEquals(c.copy(optionCount = to, solveLimitMs = null), candidate(c, stats(c = 9, weighted = 90_000), LearningAction.ADJUST_SOLVE))
        }
    }
    @Test fun maximumOptionsIncreasesWaitWithoutRequiringMinimumMemory() {
        val c = GameConditions(waitMs = 9_500, optionCount = 16, solveLimitMs = 20_000)
        assertEquals(c.copy(waitMs = 10_000, solveLimitMs = 15_000), candidate(c, stats(c = 9), LearningAction.ADJUST_SOLVE))
        assertNull(candidate(c.copy(waitMs = 10_000, solveLimitMs = 15_000), stats(c = 9), LearningAction.ADJUST_SOLVE))
    }
    @Test fun everyAccuracyBoundaryHasCorrectSourceAndKeepEligibility() {
        (0..10).forEach { c ->
            val plan = DifficultyRules.evaluate(GameConditions(), stats(c = c))
            assertEquals(c <= 6, plan.fixed)
            when (c) {
                0, 1 -> assertEquals(LearningAction.RESTORE_FULL, plan.candidates.single().action)
                2, 3 -> assertEquals(LearningAction.RESTORE_PARTIAL, plan.candidates.single().action)
                4, 5, 6 -> assertEquals(LearningAction.KEEP, plan.candidates.single().action)
                7, 8 -> assertTrue(plan.candidates.any { it.action == LearningAction.KEEP })
                else -> assertFalse(plan.candidates.any { it.action == LearningAction.KEEP })
            }
        }
        val bounded = GameConditions(5_000, 10_000, 16, 15_000)
        val plan = DifficultyRules.evaluate(bounded, stats(c = 10, memory = 5_000))
        assertTrue(plan.fixed)
        assertEquals(Candidate(LearningAction.KEEP, bounded), plan.candidates.single())
    }
    @Test fun waitRestorationDoesNotAlsoRestoreMemoryOrSolve() {
        val c = GameConditions(10_000, 3_500, 16, 20_000)
        val reductions = listOf(Reduction(1, ReductionAxis.MEMORY, 10_000))
        listOf(0, 1, 2, 3).forEach { score ->
            val plan = DifficultyRules.evaluate(c, stats(c = score), reductions)
            assertEquals(c.copy(waitMs = 3_000), plan.candidates.single().conditions)
            assertTrue(plan.restorations.isEmpty())
        }
    }
    @Test fun partialRestorationRoundsMemoryAndFloorsSolve() {
        val plan = DifficultyRules.evaluate(GameConditions(10_000, solveLimitMs = 25_000), stats(c = 2),
            listOf(Reduction(1, ReductionAxis.MEMORY, 5_000), Reduction(2, ReductionAxis.SOLVE, 5_000)))
        assertEquals(GameConditions(13_000, solveLimitMs = 27_000), plan.candidates.single().conditions)
        assertEquals(listOf(Restoration(1, 3_000), Restoration(2, 2_000)), plan.restorations)
    }
    @Test fun remainingOneSecondSolveReductionSurvivesPartialRestoration() {
        val c = GameConditions(solveLimitMs = 30_000)
        val plan = DifficultyRules.evaluate(c, stats(c = 3), listOf(Reduction(1, ReductionAxis.SOLVE, 1_000)))
        assertEquals(c, plan.candidates.single().conditions)
        assertTrue(plan.restorations.isEmpty())
    }
    @Test fun fullRestorationUsesOnlyLatestRemainingReductionPerAxis() {
        val reductions = listOf(Reduction(1, ReductionAxis.MEMORY, 4_000), Reduction(2, ReductionAxis.MEMORY, 3_000))
        val plan = DifficultyRules.evaluate(GameConditions(13_000), stats(c = 0), reductions)
        assertEquals(16_000L, plan.candidates.single().conditions.memoryLimitMs)
        assertEquals(listOf(Restoration(2, 3_000)), plan.restorations)
    }
    @Test fun fortyReachedByRestorationIsNotClearedUntilNextFullFall() {
        val c = GameConditions(solveLimitMs = 35_000)
        val plan = DifficultyRules.evaluate(c, stats(c = 1), listOf(Reduction(1, ReductionAxis.SOLVE, 5_000)))
        assertEquals(40_000L, plan.candidates.single().conditions.solveLimitMs)
        assertFalse(plan.closeSolveHistory)
        val next = DifficultyRules.evaluate(c.copy(solveLimitMs = 40_000), stats(c = 1))
        assertNull(next.candidates.single().conditions.solveLimitMs)
        assertTrue(next.closeSolveHistory)
        assertEquals(40_000L, DifficultyRules.evaluate(c.copy(solveLimitMs = 40_000), stats(c = 2)).candidates.single().conditions.solveLimitMs)
    }
    @Test fun comparisonUsesUnroundedMeansAndFallsBackToFeasibleCandidate() {
        val s = stats(memory = 12_400, solve = 12_300)
        assertEquals(LearningAction.ADJUST_SOLVE, DifficultyRules.comparison(DifficultyRules.evaluate(GameConditions(), s), s).action)
        val tied = stats(memory = 12_400, solve = 12_400)
        assertEquals(LearningAction.ADJUST_MEMORY, DifficultyRules.comparison(DifficultyRules.evaluate(GameConditions(), tied), tied).action)
        val noMemory = stats(c = 9, memory = 20_000)
        assertEquals(LearningAction.ADJUST_SOLVE, DifficultyRules.comparison(DifficultyRules.evaluate(GameConditions(), noMemory), noMemory).action)
    }
    @Test fun statisticsUseDifferentPopulationsAndNeverAverageEmptySetAsZero() {
        val items = List(7) { Performance(true, true, 1_000, 10_000, 1) } +
            Performance(false, true, 20_000, 10_000, 2) + Performance(false, true, 20_000, 10_000, 3) +
            Performance(false, false, 20_000, 90_000, 3)
        val s = BundleStatistics.from(items)
        assertEquals(7, s.firstCorrect)
        assertEquals(1_000.0, s.memoryMeanMs!!, 0.0)
        assertEquals(10_000.0, s.solveMeanMs!!, 0.0)
        assertEquals(120_000.0 / 9, s.weightedMeanMs!!, 0.0001)
        val failed = BundleStatistics.from(List(10) { Performance(false, false, 20_000, 15_000, 0) })
        assertEquals(0, failed.firstCorrect)
        assertNull(failed.memoryMeanMs); assertNull(failed.solveMeanMs); assertNull(failed.weightedMeanMs)
    }
}
