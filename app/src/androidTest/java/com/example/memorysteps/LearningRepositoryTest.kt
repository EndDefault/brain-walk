package com.example.memorysteps

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.memorysteps.data.*
import com.example.memorysteps.game.*
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LearningRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "records-test-${UUID.randomUUID()}.db"
    private lateinit var db: LearningDatabase
    private lateinit var repository: LearningRepository
    private var now = 100L
    private val clock = MonotonicClock { now }
    private val token = "test-process"

    @Before fun open() { db = LearningDatabase.open(context, name); repository = LearningRepository(db) }
    @After fun close() { db.close(); context.deleteDatabase(name) }
    private fun reopen() { db.close(); db = LearningDatabase.open(context, name); repository = LearningRepository(db) }
    private fun session(mode: TrainingMode = TrainingMode.MIXED, cycles: Int = 0) = TrainingSession(mode, cycles, clock, random = Random(2))
    private suspend fun save(session: TrainingSession) = repository.save(session.state, token, 1_000_000 + now)
    private suspend fun solve(session: TrainingSession, wrongFirst: Boolean = false) {
        val problem = session.state.problem
        session.memoryShown(problem.id); save(session)
        now += 1_200; session.next(problem.id); save(session)
        now += 3_000; session.tick(); save(session)
        session.optionsShown(problem.id); save(session)
        if (wrongFirst) { now += 250; session.answer(problem.id, problem.options.first { it.item != problem.target }.id); save(session) }
        now += 500; session.answer(problem.id, problem.options.single { it.item == problem.target }.id); save(session)
    }

    @Test fun reopeningPreservesCompletedAnswersAndInvalidatesOnlyUnfinishedProblem() = runBlocking {
        val game = session()
        repository.create(game, token, 1_000_000)
        solve(game, wrongFirst = true)
        val finished = game.state.problem
        game.advance(finished.id); save(game)
        val interrupted = game.state.problem
        game.memoryShown(interrupted.id); save(game)
        reopen()
        now = 5 // Simulate a new monotonic origin: no old timer is resumed.
        val saved = repository.recoverActive(2_000_000)!!
        assertEquals(1, saved.completed.size)
        assertEquals(2, saved.completed.single().result.attemptCount)
        assertEquals(250L, saved.completed.single().result.firstChoiceMs)
        assertEquals(750L, saved.completed.single().result.solveElapsedMs)
        assertEquals(1_200L, saved.completed.single().result.actualMemoryMs)
        assertFalse(saved.completed.single().result.firstChoiceCorrect)
        assertEquals(RoundOutcome.INTERRUPTED, saved.result.outcome)
        val restored = TrainingSession.restore(saved, clock)
        restored.resume(interrupted.id)
        assertEquals(2, restored.state.questionNumber)
        assertNotEquals(interrupted.id, restored.state.problem.id)
        assertNotEquals(interrupted.target, restored.state.problem.target)
        assertEquals(interrupted.conditions, restored.state.problem.conditions)
        repository.save(restored.state, "new-process", 2_000_001)
        val overview = db.learningDao().observeOverview().first()
        assertEquals(1, overview.problems)
        assertEquals(1, overview.finalCorrect)
        assertEquals(0, overview.completedCycles)
        assertEquals("COMPLETED", db.learningDao().problem(finished.id)!!.status)
        assertEquals("INVALID", db.learningDao().problem(interrupted.id)!!.status)
    }

    @Test fun completionIsAtomicAndIdempotentAndMixedRotationSurvivesReopen() = runBlocking {
        val game = session()
        repository.create(game, token, 1_000_000)
        repeat(10) { index ->
            solve(game)
            if (index < 9) { game.advance(game.state.problem.id); save(game) }
        }
        coroutineScope { repeat(5) { launch(Dispatchers.IO) { save(game) } } }
        reopen()
        assertEquals(1, db.learningDao().completedMixedCycles())
        assertNull(db.learningDao().progress()!!.activeCycleId)
        assertEquals(10, db.learningDao().observeOverview().first().problems)
        assertEquals(10, db.learningDao().observeOverview().first().firstCorrect)
        assertEquals(1, db.learningDao().observeOverview().first().completedCycles)
        val next = session(cycles = db.learningDao().completedMixedCycles())
        assertEquals(4, next.plan.count { it == GameType.PICTURE })
    }

    @Test fun stoppedTrainingKeepsCompletedLearningRecords() = runBlocking {
        val game = session()
        repository.create(game, token, 1_000_000)
        solve(game)
        game.advance(game.state.problem.id); save(game)
        repository.stop(game.state.sessionId, 2_000_000)
        reopen()
        assertNull(repository.recoverActive(2_000_001))
        assertEquals(1, db.learningDao().observeOverview().first().problems)
        assertEquals("STOPPED", db.learningDao().cycle(game.state.sessionId)!!.status)
        assertEquals(0, db.learningDao().completedMixedCycles())
    }

    @Test fun contentOrderAndEveryConditionRoundTripForAllThreeTypes() = runBlocking {
        TrainingMode.entries.filter { it.singleType != null }.forEach { mode ->
            val game = TrainingSession(mode, 0, clock, conditions = GameConditions(7_000, 9_500, 16, 15_000))
            repository.create(game, token, 1_000_000)
            val expected = game.state.problem
            val saved = repository.recoverActive(1_000_001)!!
            assertEquals(expected.target, saved.problem.target)
            assertEquals(expected.options, saved.problem.options)
            assertEquals(expected.generatorVersion, saved.problem.generatorVersion)
            assertEquals(expected.conditions, saved.problem.conditions)
            repository.stop(game.state.sessionId, 1_000_002)
        }
    }

    @Test fun failedCreationRollsBackCycleSlotsAndActivePointer() = runBlocking {
        fun collision() = TrainingSession(TrainingMode.MIXED, 0, clock,
            ProblemGenerator(Random(1), ProblemIdSource { "duplicate-problem-id" }))
        val first = collision()
        repository.create(first, token, 1_000_000)
        repository.stop(first.state.sessionId, 1_000_001)
        val second = collision()
        try { repository.create(second, token, 1_000_002); fail("Expected duplicate identity failure") }
        catch (_: IllegalStateException) { }
        assertEquals(1, db.learningDao().cycleCount())
        assertEquals(1, db.learningDao().problemCount())
        assertNull(db.learningDao().cycle(second.state.sessionId))
        assertTrue(db.learningDao().slots(second.state.sessionId).isEmpty())
        assertNull(db.learningDao().progress()!!.activeCycleId)
    }

    @Test fun staleProcessCannotOverwriteAnInvalidatedAttempt() = runBlocking {
        val game = session()
        repository.create(game, token, 1_000_000)
        repository.recoverActive(2_000_000)
        val p = game.state.problem
        game.memoryShown(p.id); now += 1_000; game.next(p.id); now += 3_000
        game.tick(); game.optionsShown(p.id); game.answer(p.id, p.options.single { it.item == p.target }.id)
        try { save(game); fail("Expected stale write rejection") } catch (_: IllegalStateException) { }
        assertEquals("INVALID", db.learningDao().problem(p.id)!!.status)
        assertEquals(0, db.learningDao().observeOverview().first().problems)
    }

    @Test fun practiceCannotEnterLearningTables() = runBlocking {
        val practice = TrainingSession(TrainingMode.NUMBER, 0, clock, practice = true)
        try { repository.create(practice, token, 1_000_000); fail("Practice must stay separate") }
        catch (_: IllegalArgumentException) { }
        assertEquals(0, db.learningDao().cycleCount())
        assertEquals(0, db.learningDao().problemCount())
    }
}
