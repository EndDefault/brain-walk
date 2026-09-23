package com.example.memorysteps.game

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GameRoundTest {
    private class FakeClock(var now: Long = 1_000L) : MonotonicClock {
        override fun nowMillis(): Long = now
        fun advance(ms: Long) { now += ms }
    }

    private class Fixture(solveLimit: Long? = null, waitMs: Long = 3_000L) {
        val clock = FakeClock()
        val problem = ProblemGenerator(Random(1), ProblemIdSource { "problem-1" })
            .generate(GameType.NUMBER, GameConditions(waitMs = waitMs, solveLimitMs = solveLimit))
        val round = GameRound(problem, clock)
        val correct = problem.options.single { it.item == problem.target }.id
        val wrong = problem.options.filter { it.item != problem.target }.map { it.id }

        fun startSolving() {
            round.onMemoryShown()
            clock.advance(2_000)
            round.next()
            clock.advance(problem.conditions.waitMs)
            round.tick()
            round.onOptionsShown()
        }

        fun answer(id: String) = round.answer(problem.id, id)
    }

    @Test
    fun memoryClockStartsOnlyWhenTargetIsShown() {
        val f = Fixture()
        f.clock.advance(60_000)
        assertEquals(RoundPhase.READY, f.round.tick().phase)
        assertNull(f.round.state.actualMemoryMs)
        assertEquals(20_000L, f.round.onMemoryShown().remainingStageMs)
        f.clock.advance(1_000)
        assertEquals(19_000L, f.round.onMemoryShown().remainingStageMs)
    }

    @Test
    fun nextRecordsActualExposureAndStartsFullWaitOnlyOnce() {
        val f = Fixture()
        f.round.onMemoryShown()
        f.clock.advance(1_234)
        val waiting = f.round.next()
        assertEquals(RoundPhase.WAIT, waiting.phase)
        assertEquals(1_234L, waiting.actualMemoryMs)
        assertTrue(waiting.usedNextButton)
        assertEquals(3_000L, waiting.remainingStageMs)
        f.clock.advance(500)
        assertEquals(2_500L, f.round.next().remainingStageMs)
    }

    @Test
    fun autoMemoryEndIncludesExposureWithoutNext() {
        val f = Fixture()
        f.round.onMemoryShown()
        f.clock.advance(19_999)
        assertEquals(RoundPhase.MEMORY, f.round.tick().phase)
        f.clock.advance(1)
        val waiting = f.round.tick()
        assertEquals(RoundPhase.WAIT, waiting.phase)
        assertEquals(20_000L, waiting.actualMemoryMs)
        assertFalse(waiting.usedNextButton)
    }

    @Test
    fun nextAtMemoryDeadlineIsAutomaticCompletion() {
        val f = Fixture()
        f.round.onMemoryShown()
        f.clock.advance(20_000)
        assertFalse(f.round.next().usedNextButton)
        assertEquals(RoundPhase.WAIT, f.round.state.phase)
    }

    @Test
    fun delayedMemoryTickDoesNotSkipWaitOrHideExtraExposure() {
        val f = Fixture()
        f.round.onMemoryShown()
        f.clock.advance(25_000)
        val state = f.round.tick()
        assertEquals(25_000L, state.actualMemoryMs)
        assertEquals(RoundPhase.WAIT, state.phase)
        assertEquals(3_000L, state.remainingStageMs)
    }

    @Test
    fun waitAndOptionsLayoutDoNotConsumeSolveTime() {
        val f = Fixture(solveLimit = 15_000, waitMs = 3_500)
        f.round.onMemoryShown()
        f.round.next()
        f.clock.advance(3_499)
        assertEquals(RoundPhase.WAIT, f.round.onOptionsShown().phase)
        assertTrue(f.answer(f.correct) is AnswerResponse.Rejected)
        f.clock.advance(1)
        assertEquals(RoundPhase.OPTIONS_PENDING, f.round.tick().phase)
        f.clock.advance(60_000)
        assertEquals(RoundPhase.OPTIONS_PENDING, f.round.tick().phase)
        assertTrue(f.answer(f.correct) is AnswerResponse.Rejected)
        assertEquals(15_000L, f.round.onOptionsShown().remainingStageMs)
        f.clock.advance(700)
        val accepted = f.answer(f.correct) as AnswerResponse.Accepted
        assertEquals(700L, accepted.snapshot.result?.solveElapsedMs)
    }

    @Test
    fun repeatedOptionsShownDoesNotRestartClock() {
        val f = Fixture(solveLimit = 15_000)
        f.startSolving()
        f.clock.advance(5_000)
        assertEquals(10_000L, f.round.onOptionsShown().remainingStageMs)
    }

    @Test
    fun correctOnEachAllowedAttemptKeepsFirstAndTotalTimesDistinct() {
        for (wrongCount in 0..2) {
            val f = Fixture()
            f.startSolving()
            repeat(wrongCount) { index ->
                f.clock.advance(1_000)
                f.answer(f.wrong[index])
            }
            f.clock.advance(1_000)
            val result = (f.answer(f.correct) as AnswerResponse.Accepted).snapshot.result!!
            assertEquals(RoundOutcome.CORRECT, result.outcome)
            assertTrue(result.valid)
            assertTrue(result.finalCorrect)
            assertEquals(wrongCount == 0, result.firstChoiceCorrect)
            assertEquals(wrongCount + 1, result.attemptCount)
            assertEquals(1_000L, result.firstChoiceMs)
            assertEquals((wrongCount + 1) * 1_000L, result.solveElapsedMs)
            assertEquals(2_000L, result.actualMemoryMs)
        }
    }

    @Test
    fun repeatedWrongOptionConsumesNoAttemptAndNeverRestartsTimer() {
        val f = Fixture(solveLimit = 15_000)
        f.startSolving()
        f.clock.advance(10_000)
        f.answer(f.wrong[0])
        f.clock.advance(2_000)
        val rejected = f.answer(f.wrong[0]) as AnswerResponse.Rejected
        assertEquals(RejectionReason.ALREADY_CHOSEN, rejected.reason)
        assertEquals(2, rejected.snapshot.attemptsRemaining)
        assertEquals(setOf(f.wrong[0]), rejected.snapshot.disabledOptionIds)
        assertEquals(3_000L, rejected.snapshot.remainingStageMs)
        f.clock.advance(3_000)
        assertTrue(f.answer(f.correct) is AnswerResponse.Rejected)
        assertEquals(RoundOutcome.TIMEOUT, f.round.state.result?.outcome)
        assertEquals(1, f.round.state.result?.attemptCount)
    }

    @Test
    fun threeDistinctWrongAnswersFinishAndFourthChoiceIsIgnored() {
        val f = Fixture()
        f.startSolving()
        f.wrong.forEach { f.clock.advance(1_000); f.answer(it) }
        val terminal = f.round.state.result!!
        assertEquals(RoundOutcome.ATTEMPTS_EXHAUSTED, terminal.outcome)
        assertEquals(3, terminal.attemptCount)
        assertFalse(terminal.finalCorrect)
        assertEquals(0, f.round.state.attemptsRemaining)
        assertTrue(f.answer(f.correct) is AnswerResponse.Rejected)
        assertSame(terminal, f.round.state.result)
    }

    @Test
    fun correctJustBeforeDeadlineIsAccepted() {
        val f = Fixture(solveLimit = 15_000)
        f.startSolving()
        f.clock.advance(14_999)
        assertEquals(RoundOutcome.CORRECT, (f.answer(f.correct) as AnswerResponse.Accepted).snapshot.result?.outcome)
    }

    @Test
    fun answerAtOrAfterDeadlineTimesOutEvenWithoutTick() {
        for (elapsed in listOf(15_000L, 17_000L)) {
            val f = Fixture(solveLimit = 15_000)
            f.startSolving()
            f.clock.advance(elapsed)
            assertTrue(f.answer(f.correct) is AnswerResponse.Rejected)
            val result = f.round.state.result!!
            assertEquals(RoundOutcome.TIMEOUT, result.outcome)
            assertTrue(result.valid)
            assertEquals(0, result.attemptCount)
            assertNull(result.firstChoiceMs)
            assertFalse(result.firstChoiceCorrect)
            assertEquals(elapsed, result.solveElapsedMs)
        }
    }

    @Test
    fun tickCanFinishAnUnansweredTimedQuestion() {
        val f = Fixture(solveLimit = 40_000)
        f.startSolving()
        f.clock.advance(40_000)
        assertEquals(RoundOutcome.TIMEOUT, f.round.tick().result?.outcome)
    }

    @Test
    fun absentSolveLimitDoesNotMeanZeroSeconds() {
        val f = Fixture()
        f.startSolving()
        f.clock.advance(172_800_000)
        assertEquals(RoundPhase.SOLVE, f.round.tick().phase)
        assertNull(f.round.state.remainingStageMs)
        assertTrue(f.answer(f.correct) is AnswerResponse.Accepted)
        assertEquals(172_800_000L, f.round.state.result?.solveElapsedMs)
    }

    @Test
    fun unknownOptionAndStaleProblemDoNotCountAsWrongAnswers() {
        val f = Fixture()
        f.startSolving()
        val stale = f.round.answer("previous-problem", f.correct) as AnswerResponse.Rejected
        assertEquals(RejectionReason.WRONG_PROBLEM, stale.reason)
        val unknown = f.answer("unknown-option") as AnswerResponse.Rejected
        assertEquals(RejectionReason.UNKNOWN_OPTION, unknown.reason)
        assertEquals(3, f.round.state.attemptsRemaining)
        assertNull(f.round.state.result)
    }

    @Test
    fun answersBeforeMemoryOrDuringMemoryAreIgnored() {
        val f = Fixture()
        assertTrue(f.answer(f.correct) is AnswerResponse.Rejected)
        f.round.onMemoryShown()
        assertTrue(f.answer(f.correct) is AnswerResponse.Rejected)
        assertEquals(3, f.round.state.attemptsRemaining)
    }

    @Test
    fun interruptBeforeDisplayHasNoExposureOrSolveTime() {
        val f = Fixture()
        val result = f.round.interrupt().result!!
        assertEquals(RoundOutcome.INTERRUPTED, result.outcome)
        assertFalse(result.valid)
        assertNull(result.actualMemoryMs)
        assertNull(result.solveElapsedMs)
    }

    @Test
    fun interruptDuringMemoryPreservesPartialExposureAsInvalid() {
        val f = Fixture()
        f.round.onMemoryShown()
        f.clock.advance(1_337)
        val result = f.round.interrupt().result!!
        assertFalse(result.valid)
        assertEquals(1_337L, result.actualMemoryMs)
        assertNull(result.solveElapsedMs)
        assertFalse(result.usedNextButton)
    }

    @Test
    fun interruptDuringWaitOrOptionsPreparationHasNoSolveDuration() {
        for (phase in listOf(RoundPhase.WAIT, RoundPhase.OPTIONS_PENDING)) {
            val f = Fixture()
            f.round.onMemoryShown()
            f.clock.advance(2_000)
            f.round.next()
            if (phase == RoundPhase.OPTIONS_PENDING) { f.clock.advance(3_000); f.round.tick() }
            assertEquals(phase, f.round.state.phase)
            val result = f.round.interrupt().result!!
            assertFalse(result.valid)
            assertEquals(2_000L, result.actualMemoryMs)
            assertTrue(result.usedNextButton)
            assertNull(result.solveElapsedMs)
        }
    }

    @Test
    fun interruptDuringSolveRetainsChoicesButDoesNotScoreTheQuestion() {
        val f = Fixture()
        f.startSolving()
        f.clock.advance(500)
        f.answer(f.wrong[0])
        f.clock.advance(300)
        val result = f.round.interrupt().result!!
        assertFalse(result.valid)
        assertEquals(1, result.attemptCount)
        assertEquals(500L, result.firstChoiceMs)
        assertEquals(800L, result.solveElapsedMs)
        assertTrue(f.answer(f.correct) is AnswerResponse.Rejected)
    }

    @Test
    fun completedResultIsStableAfterAllLaterEvents() {
        val f = Fixture(solveLimit = 15_000)
        f.startSolving()
        f.answer(f.correct)
        val result = f.round.state.result
        f.clock.advance(100_000)
        assertSame(result, f.round.tick().result)
        assertSame(result, f.round.next().result)
        assertSame(result, f.round.onMemoryShown().result)
        assertSame(result, f.round.onOptionsShown().result)
        assertSame(result, f.round.interrupt().result)
        assertSame(result, f.answer(f.wrong[0]).snapshot.result)
    }

    @Test
    fun earlierSnapshotsCannotBeChangedByLaterAnswersOrByCaller() {
        val f = Fixture()
        f.startSolving()
        val before = f.round.state
        f.answer(f.wrong[0])
        assertTrue(before.choices.isEmpty())
        assertThrows(UnsupportedOperationException::class.java) {
            (f.round.state.choices as MutableList<ChoiceRecord>).clear()
        }
        assertEquals(1, f.round.state.choices.size)
    }

    @Test
    fun clockMayHaveNegativeOriginButMustNotGoBackwards() {
        val f = Fixture()
        f.clock.now = -100_000
        f.round.onMemoryShown()
        f.clock.advance(100)
        assertEquals(19_900L, f.round.tick().remainingStageMs)
        f.clock.advance(-1)
        assertThrows(IllegalStateException::class.java) { f.round.tick() }
        assertEquals(19_900L, f.round.state.remainingStageMs)
    }
}
