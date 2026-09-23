package com.example.memorysteps

import android.content.ContentValues
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.memorysteps.data.*
import com.example.memorysteps.game.*
import com.example.memorysteps.ai.DiscountedUcb
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdaptiveMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "adaptive-migration-test-${UUID.randomUUID()}.db"
    private var db: LearningDatabase? = null
    @After fun cleanup() { db?.close(); context.deleteDatabase(name) }

    @Test fun versionOneUpgradePreservesOriginalDataWithoutReusingPartialOrSingleTypeGames() = runBlocking {
        val expectedContent = createVersionOneFixture()
        val upgraded = LearningDatabase.open(context, name).also { db = it }
        val records = upgraded.learningDao()
        // Opening the DAO triggers both migrations and Room's v3 schema validation.
        assertEquals(15, records.problemCount())
        assertEquals(expectedContent, records.problem("legacy-p-13")!!.content)
        assertEquals(1, records.choices("legacy-p-13").size)
        assertEquals("legacy-cycle-1", records.progress()!!.activeCycleId)
        assertEquals(14, records.observeOverview().first().problems)
        val repository = LearningRepository(upgraded)
        val restored = repository.recoverActive(2_000_000)!!
        assertEquals(4, restored.completed.size)
        assertEquals(RoundOutcome.INTERRUPTED, restored.result.outcome)
        assertEquals("legacy-p-14", restored.problem.id)
        assertTrue(restored.slotConditions.all { it == GameConditions() })
        val ai = upgraded.adaptiveDao()
        assertEquals(0, ai.decisionCount())
        assertEquals(0, ai.rewardCount())
        assertTrue(ai.pendingDecisions().isEmpty())
        assertEquals(20_000L, ai.difficulty(LearningScope.COMBINED)!!.memoryMs)
        assertNull(ai.member("legacy-p-0"))
        assertEquals("LEGACY_RECORD_ONLY", records.problem("legacy-p-0")!!.learningStatus)
        assertNull(ai.member("legacy-p-4"))
        assertNull(ai.member("legacy-p-13"))
        repository.stop("legacy-cycle-1", 2_000_001)
        assertEquals(20_000L, ai.difficulty(LearningScope.COMBINED)!!.memoryMs)
        assertEquals(14, records.observeOverview().first().problems)
        upgraded.close()
        val reopened = LearningDatabase.open(context, name).also { db = it }
        val after = LearningRepository(reopened)
        assertNull(after.recoverActive(2_000_002))
        assertEquals(0, reopened.adaptiveDao().decisionCount())
        assertEquals(0, reopened.adaptiveDao().rewardCount())
        val game = after.startFormal(TrainingMode.MIXED, MonotonicClock { 10L }, "new-process", 2_000_003)
        assertTrue(game.slotConditions.all { it == GameConditions() })
        assertEquals(expectedContent, reopened.learningDao().problem("legacy-p-13")!!.content)
    }

    @Test fun versionTwoUpgradeArchivesPerTypePolicyAndFinishesExistingGameWithoutMixingLearning() = runBlocking {
        val expected = createVersionOneFixture(version = 2)
        var upgraded = LearningDatabase.open(context, name).also { db = it }
        var repository = LearningRepository(upgraded)
        var now = 3_000_000L
        val clock = MonotonicClock { now }
        val checkpoint = repository.recoverActive(now)!!
        val ai = upgraded.adaptiveDao()
        val epoch = ai.config()!!.epochId
        assertEquals(LearningScope.COMBINED, ai.config()!!.learningScope)
        assertNotEquals("old-epoch", epoch)
        assertEquals("CLOSED_BY_SCOPE_CHANGE", ai.bundle("old-open")!!.status)
        assertEquals("CANCELLED_BY_SCOPE_CHANGE", ai.decision("old-pending")!!.status)
        assertEquals("CANCELLED_BY_SCOPE_CHANGE", ai.decision("old-applied")!!.rewardStatus)
        assertEquals(2.0, ai.arms("COLOR", DiscountedUcb.VERSION).single().effectiveCount, 0.0)
        assertEquals(4_000L, ai.reductions("COLOR").single().remainingMs)
        assertEquals(10_000L, ai.difficulty("COLOR")!!.memoryMs)
        assertEquals(20_000L, ai.difficulty(LearningScope.COMBINED)!!.memoryMs)
        assertEquals(3, ai.observeArms().first().size)
        assertTrue(ai.observeArms().first().all { it.effectiveCount == 0.0 })
        assertEquals(listOf(20_000L, 15_000L, 10_000L), checkpoint.slotConditions.take(3).map { it.memoryLimitMs })
        assertEquals(expected, upgraded.learningDao().problem("legacy-p-13")!!.content)
        upgraded.close()
        upgraded = LearningDatabase.open(context, name).also { db = it }
        repository = LearningRepository(upgraded)
        val game = TrainingSession.restore(repository.recoverActive(++now)!!, clock)
        assertEquals(epoch, upgraded.adaptiveDao().config()!!.epochId)
        game.resume(game.state.problem.id)
        repository.save(game.state, "new-process", now)
        repeat(6) { index ->
            val p = game.state.problem
            suspend fun save() = repository.save(game.state, "new-process", now)
            game.memoryShown(p.id); save()
            now += 1_000; game.next(p.id); save()
            now += p.conditions.waitMs; game.tick(); save()
            game.optionsShown(p.id); save()
            now += 500; game.answer(p.id, p.options.single { it.item == p.target }.id); save()
            if (index < 5) { game.advance(p.id); save() }
        }
        assertEquals(20, upgraded.learningDao().observeOverview().first().problems)
        assertEquals(0, upgraded.adaptiveDao().rewardCount())
        assertTrue(upgraded.adaptiveDao().observeDecisions().first().isEmpty())
        assertEquals("LEGACY_SCOPE_RECORD_ONLY", upgraded.learningDao().problem(game.state.problem.id)!!.learningStatus)
        val next = repository.startFormal(TrainingMode.MIXED, clock, "new-process", ++now)
        assertTrue(next.slotConditions.all { it == GameConditions() })
    }

    private fun createVersionOneFixture(version: Int = 1): String {
        val schema = InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.example.memorysteps.data.LearningDatabase/$version.json").bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        var expected = ""
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { sqlite ->
            val entities = schema.getJSONArray("entities")
            repeat(entities.length()) { index ->
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                sqlite.execSQL(entity.getString("createSql").replace("$" + "{TABLE_NAME}", table))
                entity.optJSONArray("indices")?.let { indices ->
                    repeat(indices.length()) { sqlite.execSQL(indices.getJSONObject(it).getString("createSql").replace("$" + "{TABLE_NAME}", table)) }
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            repeat(setup.length()) { sqlite.execSQL(setup.getString(it)) }
            fun row(table: String, values: Map<String, Any?>) {
                val content = ContentValues()
                values.forEach { (key, value) -> when (value) {
                    null -> content.putNull(key)
                    is String -> content.put(key, value)
                    is Int -> content.put(key, value)
                    is Long -> content.put(key, value)
                    is Double -> content.put(key, value)
                    is Boolean -> content.put(key, value)
                    else -> error("Unsupported fixture value")
                } }
                sqlite.insertOrThrow(table, null, content)
            }
            repeat(2) { cycle ->
                val complete = cycle == 0
                val cycleId = "legacy-cycle-$cycle"
                row("training_cycles", mapOf("id" to cycleId, "mode" to if (version == 1) "COLOR" else "MIXED", "status" to if (complete) "COMPLETED" else "IN_PROGRESS",
                    "createdAt" to cycle * 100L, "completedAt" to if (complete) 99L else null, "rotationIndex" to 0,
                    "currentSlot" to if (complete) 9 else 4, "showSummary" to complete, "appVersion" to "0.1.0"))
                repeat(10) { slot ->
                    val number = cycle * 10 + slot
                    val type = if (version == 1) GameType.COLOR else GameType.entries[slot % 3]
                    val memoryMs = if (version == 1) 20_000L else 20_000L - (slot % 3) * 5_000
                    val done = number < 14
                    val problemId = "legacy-p-$number"
                    row("cycle_slots", mapOf("cycleId" to cycleId, "slotIndex" to slot, "type" to type.name, "memoryMs" to memoryMs,
                        "waitMs" to 3_000L, "optionCount" to 4, "solveMs" to null, "conditionVersion" to "initial-v1",
                        "finalizedProblemId" to if (done) problemId else null))
                    if (number <= 14) {
                        val p = ProblemGenerator(Random(number), ProblemIdSource { problemId }).generate(type, GameConditions(memoryLimitMs = memoryMs))
                        val payload = ProblemContentCodec.encode(p)
                        if (number == 13) expected = payload
                        row("problem_attempts", mapOf("id" to problemId, "cycleId" to cycleId, "slotIndex" to slot, "revision" to 0,
                            "runToken" to "legacy-process", "generatorVersion" to p.generatorVersion, "content" to payload,
                            "generatedAt" to 1_000L + number, "updatedAt" to 2_000L + number,
                            "phase" to if (done) "FINISHED" else "MEMORY", "status" to if (done) "COMPLETED" else "ACTIVE",
                            "outcome" to if (done) "CORRECT" else null, "invalidReason" to null,
                            "actualMemoryMs" to if (done) 9_000L else null, "usedNextButton" to done,
                            "solveElapsedMs" to if (done) 600L else null, "firstChoiceMs" to if (done) 600L else null,
                            "firstCorrect" to done, "finalCorrect" to done, "attempts" to if (done) 1 else 0,
                            "learningStatus" to "PENDING_ALGORITHM"))
                        if (done) row("choice_attempts", mapOf("problemId" to problemId, "attemptIndex" to 1,
                            "optionId" to p.options.single { it.item == p.target }.id, "correct" to true,
                            "elapsedMs" to 600L, "monotonicAt" to 5_000L + number, "wallClockAt" to 2_000L + number))
                    }
                }
            }
            row("training_progress", mapOf("singleton" to 1, "activeCycleId" to "legacy-cycle-1"))
            if (version == 2) {
                row("algorithm_epochs", mapOf("id" to "old-epoch", "mode" to "BANDIT", "openedAt" to 1L, "closedAt" to null))
                row("algorithm_config", mapOf("singleton" to 1, "epochId" to "old-epoch"))
                sqlite.execSQL("UPDATE training_cycles SET modeEpochId = 'old-epoch'")
                GameType.entries.forEach { type -> row("difficulty_states", mapOf("type" to type.name,
                    "memoryMs" to 10_000L, "waitMs" to 3_000L, "optionCount" to 4, "solveMs" to null,
                    "conditionVersion" to "old-applied", "appliedDecisionId" to "old-applied")) }
                val conditions = AdaptiveCodec.conditions(GameConditions())
                listOf("old-open", "old-input-1", "old-input-2").forEach { id ->
                    row("learning_bundles", mapOf("id" to id, "type" to "COLOR", "epochId" to "old-epoch",
                        "conditionVersion" to "initial-v1", "conditions" to conditions, "generatorVersion" to "color-pair-v1",
                        "sourceDecisionId" to null, "status" to if (id == "old-open") "OPEN" else "COMPLETED", "origin" to "LIVE", "createdAt" to 1L))
                }
                row("bundle_members", mapOf("problemId" to "legacy-p-0", "bundleId" to "old-open", "ordinal" to 0))
                sqlite.execSQL("UPDATE problem_attempts SET learningStatus = 'INCLUDED' WHERE id = 'legacy-p-0'")
                listOf("old-pending", "old-applied").forEachIndexed { index, id ->
                    row("ai_decisions", mapOf("id" to id, "type" to "COLOR", "epochId" to "old-epoch", "inputBundleId" to "old-input-${index + 1}",
                        "algorithmVersion" to DiscountedUcb.VERSION, "source" to "BANDIT", "action" to "KEEP", "reason" to "UNTRIED",
                        "beforeVersion" to "initial-v1", "beforeConditions" to conditions, "afterConditions" to conditions,
                        "candidates" to "[]", "restorations" to "[]", "closeSolveHistory" to false,
                        "status" to if (index == 0) "PENDING" else "APPLIED", "rewardStatus" to if (index == 0) "PENDING_APPLY" else "WAITING",
                        "createdAt" to 1L, "appliedAt" to if (index == 0) null else 2L))
                }
                row("bandit_arms", mapOf("type" to "COLOR", "action" to "KEEP", "algorithmVersion" to DiscountedUcb.VERSION,
                    "hasEverApplied" to true, "effectiveCount" to 2.0, "rewardSum" to 1.5, "updatedAt" to 1L))
                row("reduction_history", mapOf("id" to 1, "type" to "COLOR", "axis" to "MEMORY", "decisionId" to "old-applied",
                    "beforeMs" to 14_000L, "afterMs" to 10_000L, "remainingMs" to 4_000L, "status" to "ACTIVE"))
            }
            sqlite.version = version
        }
        return expected
    }
}
