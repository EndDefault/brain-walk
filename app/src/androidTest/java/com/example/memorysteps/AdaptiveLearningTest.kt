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
    private suspend fun start(mode: TrainingMode = TrainingMode.MIXED) = repository.startFormal(mode, clock, token, now)
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

    @Test fun firstMixedGameProducesOneSharedDecisionOnlyAfterAllTenQuestions() = runBlocking {
        val game = start()
        assertTrue(game.slotConditions.all { it == GameConditions() })
        repeat(10) { index ->
            solve(game)
            if (index < 9) {
                assertEquals(0, ai.decisionCount())
                assertEquals(20_000L, ai.difficulty(LearningScope.COMBINED)!!.memoryMs)
                assertEquals(index + 1, ai.observeProfiles().first().single().collected)
                game.advance(game.state.problem.id); save(game)
            }
        }
        assertEquals(1, ai.decisionCount())
        assertEquals(0, ai.rewardCount())
        val bundle = ai.cycleBundle(game.state.sessionId)!!
        assertEquals(LearningScope.COMBINED, bundle.type)
        assertEquals(10, ai.memberCount(bundle.id))
        assertEquals(10, ai.observations(bundle.id).size)
        assertEquals(3, game.plan.distinct().size)
        assertEquals(1, ai.difficulties().size)
        assertEquals(5_000L, ai.difficulty(LearningScope.COMBINED)!!.memoryMs)
        assertEquals(10, db.learningDao().observeOverview().first().problems)
        assertTrue(game.slotConditions.all { it.memoryLimitMs == 20_000L })
        reopen()
        val next = start()
        assertTrue(next.slotConditions.all { it.memoryLimitMs == 5_000L })
        assertEquals(4, next.plan.count { it == GameType.PICTURE })
    }

    @Test fun stoppedPartialGameIsPreservedButNeverCombinedWithTheNextGame() = runBlocking {
        val seed = start()
        repeat(8) { solve(seed); seed.advance(seed.state.problem.id); save(seed) }
        repository.stop(seed.state.sessionId, now)
        assertEquals("CLOSED_BY_CYCLE_STOPPED", ai.cycleBundle(seed.state.sessionId)!!.status)
        assertEquals(0, ai.observeProfiles().first().single().collected)
        assertEquals(0, ai.decisionCount())
        var mixed = start()
        repeat(10) { index ->
            solve(mixed)
            if (index == 1) {
                assertEquals(0, ai.decisionCount())
                assertEquals(20_000L, ai.difficulty(LearningScope.COMBINED)!!.memoryMs)
                reopen()
                mixed = TrainingSession.restore(repository.recoverActive(now)!!, clock)
                assertEquals(index + 1, mixed.state.questionNumber)
                assertEquals(2, ai.observeProfiles().first().single().collected)
            }
            if (index < 9) { mixed.advance(mixed.state.problem.id); save(mixed) }
        }
        assertEquals(1, ai.decisionCount())
        assertEquals(10, ai.memberCount(ai.cycleBundle(mixed.state.sessionId)!!.id))
        assertEquals(8, ai.memberCount(ai.cycleBundle(seed.state.sessionId)!!.id))
        assertEquals(5_000L, ai.difficulty(LearningScope.COMBINED)!!.memoryMs)
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
        val memory = ai.arms(LearningScope.COMBINED, DiscountedUcb.VERSION).single { it.action == "ADJUST_MEMORY" }
        assertEquals(1.0, memory.effectiveCount, 1e-12)
        assertEquals(1.0, memory.rewardSum, 1e-12)
        assertEquals(2, ai.decisionCount())
        assertEquals("REWARDED", ai.decision(firstDecision.id)!!.rewardStatus)
        assertEquals(3, ai.observeArms().first().size)
    }

    @Test fun repeatedPerfectMixedGamesApplySolveLimitsAndEveryOptionStepInBanditMode() = runBlocking {
        assertSolveProgression(AlgorithmMode.BANDIT)
    }

    @Test fun repeatedPerfectMixedGamesApplySolveLimitsAndEveryOptionStepInComparisonMode() = runBlocking {
        assertSolveProgression(AlgorithmMode.COMPARISON)
    }

    private suspend fun assertSolveProgression(mode: AlgorithmMode) {
        repository.changeAlgorithm(mode, now)
        complete(start())
        assertEquals(5_000L, ai.difficulty(LearningScope.COMBINED)!!.memoryMs)
        var expected = GameConditions(memoryLimitMs = 5_000)
        var completedGames = 1

        // Exercise real answers -> persisted bundle -> chosen action -> next
        // mixed game's generated options, without seeding difficulty states.
        for (options in listOf(4, 6, 9, 12)) {
            assertEquals(options, expected.optionCount)
            for (limit in listOf<Long?>(40_000, 35_000, 30_000, 25_000, 20_000, null)) {
                reopen()
                val game = start()
                assertEquals(GameType.entries.toSet(), game.plan.toSet())
                assertTrue(game.slotConditions.all { it == expected })
                repeat(10) { index ->
                    assertEquals(expected, game.state.problem.conditions)
                    assertEquals(options, game.state.problem.options.size)
                    solve(game)
                    if (index < 9) {
                        assertEquals(completedGames, ai.decisionCount())
                        assertEquals(expected, ai.difficulty(LearningScope.COMBINED)!!.conditions())
                        game.advance(game.state.problem.id); save(game)
                    }
                }
                completedGames++
                expected = if (limit != null) expected.copy(solveLimitMs = limit)
                else expected.copy(optionCount = when (options) { 4 -> 6; 6 -> 9; 9 -> 12; else -> 16 }, solveLimitMs = null)
                val decision = ai.observeDecisions().first().first()
                assertEquals(mode.name, decision.source)
                assertEquals("ADJUST_SOLVE", decision.action)
                assertEquals("APPLIED", decision.status)
                assertEquals(expected, ai.difficulty(LearningScope.COMBINED)!!.conditions())
                assertEquals(completedGames, ai.decisionCount())
            }
        }
        reopen()
        val finalGame = start()
        assertEquals(16, expected.optionCount)
        assertNull(expected.solveLimitMs)
        assertTrue(finalGame.slotConditions.all { it == expected })
        repeat(10) { index ->
            assertEquals(16, finalGame.state.problem.options.size)
            solve(finalGame)
            if (index < 9) { finalGame.advance(finalGame.state.problem.id); save(finalGame) }
        }
        assertEquals(if (mode == AlgorithmMode.BANDIT) 25 else 0, ai.rewardCount())
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
        assertTrue(ai.arms(LearningScope.COMBINED, DiscountedUcb.VERSION).all { it.effectiveCount == 0.0 })
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
        assertEquals(5_000L, ai.difficulty(LearningScope.COMBINED)!!.memoryMs)
        assertTrue(ai.observeBundles().first().any { it.status == "CLOSED_BY_CYCLE_STOPPED" })
        complete(start())
        assertEquals(0, ai.rewardCount())
        assertTrue(ai.observeDecisions().first().any { it.source == "COMPARISON" && it.action == "ADJUST_SOLVE" })
        assertTrue(ai.arms(LearningScope.COMBINED, DiscountedUcb.VERSION).all { it.effectiveCount == 0.0 })
        assertTrue(repository.changeAlgorithm(AlgorithmMode.BANDIT, now))
        complete(start())
        assertEquals(0, ai.rewardCount())
    }

    @Test fun restorationConsumesLatestRemainderOnceAndPersistsAcrossRestart() = runBlocking {
        repository.changeAlgorithm(AlgorithmMode.COMPARISON, now)
        complete(start(), memoryMs = 12_000)
        assertEquals(11_000L, ai.difficulty(LearningScope.COMBINED)!!.memoryMs)
        complete(start(), memoryMs = 8_000)
        assertEquals(7_000L, ai.difficulty(LearningScope.COMBINED)!!.memoryMs)
        val partial = start()
        complete(partial, firstCount = 2, failures = true)
        save(partial); save(partial)
        reopen()
        assertEquals(9_000L, ai.difficulty(LearningScope.COMBINED)!!.memoryMs)
        assertEquals(listOf(9_000L, 2_000L), ai.reductions(LearningScope.COMBINED).map { it.remainingMs })
        complete(start(), firstCount = 0, failures = true)
        assertEquals(11_000L, ai.difficulty(LearningScope.COMBINED)!!.memoryMs)
        complete(start(), firstCount = 0, failures = true)
        assertEquals(20_000L, ai.difficulty(LearningScope.COMBINED)!!.memoryMs)
        assertEquals(0, ai.rewardCount())
    }

    @Test fun interruptedGameRestoresSharedConditionsAndCompletesItsOriginalBundle() = runBlocking {
        complete(start())
        val mixed = start(TrainingMode.MIXED)
        repeat(4) { solve(mixed); mixed.advance(mixed.state.problem.id); save(mixed) }
        reopen()
        val restored = TrainingSession.restore(repository.recoverActive(now)!!, clock)
        assertTrue(restored.slotConditions.all { it.memoryLimitMs == 5_000L })
        assertEquals(4, ai.memberCount(ai.cycleBundle(mixed.state.sessionId)!!.id))
        restored.resume(restored.state.problem.id); save(restored)
        assertEquals(restored.slotConditions[restored.state.questionNumber - 1], restored.state.problem.conditions)
        repeat(6) { index ->
            solve(restored)
            if (index < 5) { restored.advance(restored.state.problem.id); save(restored) }
        }
        assertEquals(10, ai.memberCount(ai.cycleBundle(mixed.state.sessionId)!!.id))
        assertEquals(2, ai.decisionCount())
        assertEquals(1, ai.rewardCount())
    }
}
