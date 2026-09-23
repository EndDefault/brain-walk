package com.example.memorysteps

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.memorysteps.ai.DiscountedUcb
import com.example.memorysteps.data.*
import com.example.memorysteps.difficulty.AlgorithmMode
import com.example.memorysteps.game.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdaptiveLearningTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "adaptive-test-${UUID.randomUUID()}.db"
    private lateinit var db: LearningDatabase
    private lateinit var repository: LearningRepository
    private var now = 100L
    private val clock = MonotonicClock { now }
    private val token = "adaptive-test-process"
    private val ai get() = db.adaptiveDao()
    @Before fun open() { db = LearningDatabase.open(context, name); repository = LearningRepository(db) }
    @After fun close() { db.close(); context.deleteDatabase(name) }
    private fun reopen() { db.close(); open() }
    private suspend fun start(mode: TrainingMode = TrainingMode.COLOR) = repository.startFormal(mode, clock, token, now)
    private suspend fun save(game: TrainingSession) = repository.save(game.state, token, now)
    private suspend fun solve(game: TrainingSession, firstCorrect: Boolean = true, fail: Boolean = false, memoryMs: Long = 1_200) {
        val p = game.state.problem
        game.memoryShown(p.id); save(game)
        now += memoryMs; game.next(p.id); save(game)
        now += p.conditions.waitMs; game.tick(); save(game)
        game.optionsShown(p.id); save(game)
        if (fail) {
            p.options.filter { it.item != p.target }.take(3).forEach { now += 500; game.answer(p.id, it.id); save(game) }
        } else {
            if (!firstCorrect) { now += 250; game.answer(p.id, p.options.first { it.item != p.target }.id); save(game) }
            now += 500; game.answer(p.id, p.options.single { it.item == p.target }.id); save(game)
        }
    }
    private suspend fun complete(game: TrainingSession, firstCount: Int = 10, failures: Boolean = false, memoryMs: Long = 1_200) {
        repeat(10) { i ->
            solve(game, firstCorrect = i < firstCount, fail = failures && i >= firstCount, memoryMs = memoryMs)
            if (i < 9) { game.advance(game.state.problem.id); save(game) }
        }
    }

    @Test fun threeMixedGamesProduceThreeIndependentDecisionsOnlyAtCycleEnd() = runBlocking {
        repeat(3) {
            val game = start(TrainingMode.MIXED)
            assertTrue(game.slotConditions.all { it == GameConditions() })
            repeat(10) { index ->
                solve(game)
                if (index < 9) {
                    assertTrue(ai.difficulties().all { it.memoryMs == 20_000L })
                    game.advance(game.state.problem.id); save(game)
                }
            }
        }
        assertEquals(3, ai.decisionCount())
        assertEquals(0, ai.rewardCount())
        assertTrue(ai.difficulties().all { it.memoryMs == 5_000L })
        assertEquals(30, db.learningDao().observeOverview().first().problems)
        reopen()
        val next = start(TrainingMode.MIXED)
        assertTrue(next.slotConditions.all { it.memoryLimitMs == 5_000L })
        assertEquals(4, next.plan.count { it == GameType.COLOR })
    }

    @Test fun pendingDecisionAndRecordOnlyRemainderSurviveReopeningMidCycle() = runBlocking {
        val seed = start()
        repeat(8) { solve(seed); seed.advance(seed.state.problem.id); save(seed) }
        repository.stop(seed.state.sessionId, now)
        var mixed = start(TrainingMode.MIXED)
        var reopened = false
        repeat(10) { index ->
            solve(mixed)
            if (!reopened && ai.pendingDecisions().isNotEmpty()) {
                assertEquals(20_000L, ai.difficulty("COLOR")!!.memoryMs)
                reopen()
                mixed = TrainingSession.restore(repository.recoverActive(now)!!, clock)
                assertEquals(index + 1, mixed.state.questionNumber)
                reopened = true
            }
            if (index < 9) { mixed.advance(mixed.state.problem.id); save(mixed) }
        }
        assertTrue(reopened)
        val color = db.learningDao().problems(mixed.state.sessionId).filter { it.content.contains("COLOR:") }
        assertEquals(2, color.count { it.learningStatus == "INCLUDED" })
        assertEquals(2, color.count { it.learningStatus == "AFTER_BUNDLE_COMPLETE" })
        assertEquals(5_000L, ai.difficulty("COLOR")!!.memoryMs)
        assertEquals(20_000L, ai.difficulty("PICTURE")!!.memoryMs)
        assertEquals(18, db.learningDao().observeOverview().first().problems)
    }

    @Test fun onlyNextBundleRewardsChosenActionAndDuplicateCallbacksDoNotLearnAgain() = runBlocking {
        complete(start())
        val firstDecision = ai.observeDecisions().first().single()
        assertEquals("ADJUST_MEMORY", firstDecision.action)
        assertEquals("WAITING", firstDecision.rewardStatus)
        assertEquals(0, ai.rewardCount())
        val next = start()
        assertEquals(5_000L, next.state.problem.conditions.memoryLimitMs)
        complete(next, firstCount = 8)
        coroutineScope { repeat(5) { launch(Dispatchers.IO) { save(next) } } }
        reopen()
        assertEquals(1, ai.rewardCount())
        assertEquals(1.0, ai.reward(firstDecision.id)!!.reward, 1e-12)
        val memory = ai.arms("COLOR", DiscountedUcb.VERSION).single { it.action == "ADJUST_MEMORY" }
        assertEquals(1.0, memory.effectiveCount, 1e-12)
        assertEquals(1.0, memory.rewardSum, 1e-12)
        assertEquals(2, ai.decisionCount())
        assertEquals("REWARDED", ai.decision(firstDecision.id)!!.rewardStatus)
        assertTrue(ai.arms("PICTURE", DiscountedUcb.VERSION).all { it.effectiveCount == 0.0 })
    }

    @Test fun transactionFailureRollsBackAnswerRewardDecisionAndCycleCompletionTogether() = runBlocking {
        complete(start())
        val next = start()
        repeat(9) { solve(next); next.advance(next.state.problem.id); save(next) }
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_decision BEFORE INSERT ON ai_decisions BEGIN SELECT RAISE(ABORT, 'test fault'); END")
        try { solve(next); fail("Expected transaction failure") } catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals(19, db.learningDao().observeOverview().first().problems)
        assertEquals(0, ai.rewardCount())
        assertEquals(1, ai.decisionCount())
        assertEquals(next.state.sessionId, db.learningDao().progress()!!.activeCycleId)
        assertTrue(ai.arms("COLOR", DiscountedUcb.VERSION).all { it.effectiveCount == 0.0 })
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_decision")
        val recovered = TrainingSession.restore(repository.recoverActive(now)!!, clock)
        recovered.resume(recovered.state.problem.id); save(recovered)
        solve(recovered)
        assertEquals(20, db.learningDao().observeOverview().first().problems)
        assertEquals(1, ai.rewardCount())
        assertNull(db.learningDao().progress()!!.activeCycleId)
    }

    @Test fun modeChangeSeparatesIncompleteBundlesAndNeverRewardsComparisonResults() = runBlocking {
        complete(start())
        val first = ai.observeDecisions().first().single()
        val unfinished = start()
        repeat(4) { solve(unfinished); unfinished.advance(unfinished.state.problem.id); save(unfinished) }
        assertFalse(repository.changeAlgorithm(AlgorithmMode.COMPARISON, now))
        repository.stop(unfinished.state.sessionId, now)
        assertTrue(repository.changeAlgorithm(AlgorithmMode.COMPARISON, now))
        assertEquals("CANCELLED_BY_MODE_CHANGE", ai.decision(first.id)!!.rewardStatus)
        assertEquals(5_000L, ai.difficulty("COLOR")!!.memoryMs)
        assertTrue(ai.observeBundles().first().any { it.status == "CLOSED_BY_MODE_CHANGE" })
        complete(start())
        assertEquals(0, ai.rewardCount())
        assertTrue(ai.observeDecisions().first().any { it.source == "COMPARISON" && it.action == "ADJUST_SOLVE" })
        assertTrue(ai.arms("COLOR", DiscountedUcb.VERSION).all { it.effectiveCount == 0.0 })
        assertTrue(repository.changeAlgorithm(AlgorithmMode.BANDIT, now))
        complete(start())
        assertEquals(0, ai.rewardCount())
    }

    @Test fun restorationConsumesLatestRemainderOnceAndPersistsAcrossRestart() = runBlocking {
        repository.changeAlgorithm(AlgorithmMode.COMPARISON, now)
        complete(start(), memoryMs = 12_000)
        assertEquals(11_000L, ai.difficulty("COLOR")!!.memoryMs)
        complete(start(), memoryMs = 8_000)
        assertEquals(7_000L, ai.difficulty("COLOR")!!.memoryMs)
        val partial = start()
        complete(partial, firstCount = 2, failures = true)
        save(partial); save(partial)
        reopen()
        assertEquals(9_000L, ai.difficulty("COLOR")!!.memoryMs)
        assertEquals(listOf(9_000L, 2_000L), ai.reductions("COLOR").map { it.remainingMs })
        complete(start(), firstCount = 0, failures = true)
        assertEquals(11_000L, ai.difficulty("COLOR")!!.memoryMs)
        complete(start(), firstCount = 0, failures = true)
        assertEquals(20_000L, ai.difficulty("COLOR")!!.memoryMs)
        assertEquals(0, ai.rewardCount())
    }

    @Test fun mixedCycleRestoresEveryTypesFrozenConditionsInsteadOfCopyingCurrentProblem() = runBlocking {
        complete(start())
        val mixed = start(TrainingMode.MIXED)
        repeat(4) { solve(mixed); mixed.advance(mixed.state.problem.id); save(mixed) }
        reopen()
        val restored = TrainingSession.restore(repository.recoverActive(now)!!, clock)
        restored.plan.forEachIndexed { index, type ->
            assertEquals(if (type == GameType.COLOR) 5_000L else 20_000L, restored.slotConditions[index].memoryLimitMs)
        }
        restored.resume(restored.state.problem.id); save(restored)
        assertEquals(restored.slotConditions[restored.state.questionNumber - 1], restored.state.problem.conditions)
    }
}
