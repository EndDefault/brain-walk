package com.example.memorysteps.ui

import com.example.memorysteps.data.CycleHistory
import com.example.memorysteps.ui.screens.records.accuracyPercent
import com.example.memorysteps.ui.screens.records.recentCompletedGames
import org.junit.Assert.*
import org.junit.Test

class RecordMetricsTest {
    @Test fun emptyRecordsAreNotReportedAsZeroPerformance() {
        assertNull(accuracyPercent(0, 0))
        assertEquals(0, accuracyPercent(0, 10))
        assertEquals(67, accuracyPercent(2, 3))
        assertEquals(100, accuracyPercent(Int.MAX_VALUE, Int.MAX_VALUE))
    }

    @Test fun interruptedAndEmptyCyclesDoNotBecomeTrendPoints() {
        val completed = cycle("ok", 10)
        val stopped = cycle("stop", 20).copy(status = "STOPPED", problems = 3)
        val active = cycle("active", 30).copy(status = "IN_PROGRESS")
        val empty = cycle("empty", 40).copy(problems = 0)
        assertEquals(listOf(completed), recentCompletedGames(listOf(empty, stopped, completed, active)))
    }

    @Test fun lastTenCompletedGamesAreChronologicalAndNotCalledAllTimeBest() {
        val games = (1L..15L).map { cycle("game-$it", it) }
        assertEquals(games.takeLast(10), recentCompletedGames(games.reversed()))
        assertTrue(recentCompletedGames(emptyList()).isEmpty())
    }

    private fun cycle(id: String, at: Long) = CycleHistory(id, "MIXED", "COMPLETED", at, at + 100, 10, 7)
}
