package com.example.memorysteps.game

import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class TrainingSessionTest {
    private class Fixture(mode: TrainingMode = TrainingMode.MIXED, cycle: Int = 0) {
        var now = 0L
        private var id = 0
        val session = TrainingSession(mode, cycle, MonotonicClock { now },
            ProblemGenerator(Random(12), ProblemIdSource { "problem-${id++}" }), Random(5))
        fun solve(correct: Boolean = true) {
            val problem = session.state.problem
            session.memoryShown(problem.id)
            now += 1_000
            session.next(problem.id)
            now += 3_000
            session.tick()
            session.optionsShown(problem.id)
            val choices = problem.options.filter { (it.item == problem.target) == correct }
            choices.forEach { now += 100; session.answer(problem.id, it.id) }
        }
    }

    @Test fun mixedPlanRotatesFourQuestionTypeAndAlwaysHasTen() {
        repeat(6) { cycle ->
            val session = Fixture(cycle = cycle).session
            assertEquals(10, session.plan.size)
            GameType.entries.forEach { type ->
                assertEquals(if (type.ordinal == cycle % 3) 4 else 3, session.plan.count { it == type })
            }
        }
    }

    @Test fun singleTypeModesContainOnlyTheirTenQuestions() {
        TrainingMode.entries.filter { it.singleType != null }.forEach { mode ->
            assertEquals(List(10) { mode.singleType }, Fixture(mode).session.plan)
        }
    }

    @Test fun tenAnswersLeadToSummaryAndCannotAddEleventhQuestion() {
        val f = Fixture()
        repeat(10) { index ->
            assertEquals(index + 1, f.session.state.questionNumber)
            f.solve(correct = index % 2 == 0)
            val id = f.session.state.problem.id
            assertEquals(index + 1, f.session.state.completed.size)
            assertFalse(f.session.state.showSummary)
            f.session.advance(id)
            f.session.advance(id) // duplicate button event from the previous UI
        }
        assertTrue(f.session.state.showSummary)
        assertTrue(f.session.state.complete)
        assertEquals(5, f.session.state.firstCorrect)
        assertEquals(5, f.session.state.finalCorrect)
        val lastId = f.session.state.problem.id
        f.solve()
        f.session.advance(lastId)
        assertEquals(10, f.session.state.completed.size)
        assertEquals(lastId, f.session.state.problem.id)
    }

    @Test fun unfinishedRoundCannotBeSkipped() {
        val f = Fixture()
        val id = f.session.state.problem.id
        f.session.advance(id)
        assertEquals(id, f.session.state.problem.id)
        assertEquals(1, f.session.state.questionNumber)
        assertTrue(f.session.state.completed.isEmpty())
    }

    @Test fun interruptAndResumeReplaceOnlyTheUnfinishedQuestion() {
        val f = Fixture()
        f.solve()
        f.session.advance(f.session.state.problem.id)
        val previous = f.session.state.problem
        f.session.memoryShown(previous.id)
        f.session.interrupt()
        assertEquals(1, f.session.state.completed.size)
        assertFalse(f.session.state.round.result!!.valid)
        f.session.resume(previous.id)
        val replacement = f.session.state.problem
        assertNotEquals(previous.id, replacement.id)
        assertNotEquals(previous.target, replacement.target)
        assertEquals(previous.conditions, replacement.conditions)
        assertEquals(previous.type, replacement.type)
        assertEquals(2, f.session.state.questionNumber)
        assertEquals(1, f.session.state.completed.size)
        f.session.answer(previous.id, previous.options.first().id)
        f.session.memoryShown(previous.id)
        f.session.next(previous.id)
        assertEquals(RoundPhase.READY, f.session.state.round.phase)
    }

    @Test fun completedRoundIsCountedOnceAcrossHomeAndLifecycleEvents() {
        val f = Fixture()
        f.solve()
        val id = f.session.state.problem.id
        repeat(5) { f.session.interrupt(); f.session.tick(); f.session.resume(id) }
        assertEquals(1, f.session.state.completed.size)
        assertEquals(RoundOutcome.CORRECT, f.session.state.round.result!!.outcome)
    }

    @Test fun sessionSnapshotsDoNotExposeMutableLists() {
        val f = Fixture()
        val before = f.session.state
        f.solve()
        assertTrue(before.completed.isEmpty())
        assertThrows(UnsupportedOperationException::class.java) {
            (f.session.plan as MutableList<GameType>).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            (f.session.state.completed as MutableList<CompletedProblem>).clear()
        }
    }
}
