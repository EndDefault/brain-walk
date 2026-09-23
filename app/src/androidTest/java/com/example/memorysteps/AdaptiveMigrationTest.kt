package com.example.memorysteps

import android.content.ContentValues
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.memorysteps.data.*
import com.example.memorysteps.game.*
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

    @Test fun versionOneUpgradePreservesOriginalDataAndCalibratesWithoutRetroactiveRewards() = runBlocking {
        val expectedContent = createVersionOneFixture()
        val upgraded = LearningDatabase.open(context, name).also { db = it }
        val records = upgraded.learningDao()
        // Opening the DAO triggers the generated migration and Room's v2 schema validation.
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
        assertEquals(1, ai.decisionCount())
        assertEquals(0, ai.rewardCount())
        val decision = ai.pendingDecisions().single()
        assertEquals("PENDING", decision.status)
        assertEquals(20_000L, ai.difficulty("COLOR")!!.memoryMs)
        assertNull(ai.member("legacy-p-0"))
        assertEquals("LEGACY_RECORD_ONLY", records.problem("legacy-p-0")!!.learningStatus)
        assertNotNull(ai.member("legacy-p-4"))
        assertNotNull(ai.member("legacy-p-13"))
        assertEquals(10, ai.memberCount(decision.inputBundleId))
        repository.stop("legacy-cycle-1", 2_000_001)
        assertEquals(8_000L, ai.difficulty("COLOR")!!.memoryMs)
        assertEquals(14, records.observeOverview().first().problems)
        upgraded.close()
        val reopened = LearningDatabase.open(context, name).also { db = it }
        val after = LearningRepository(reopened)
        assertNull(after.recoverActive(2_000_002))
        assertEquals(1, reopened.adaptiveDao().decisionCount())
        assertEquals(0, reopened.adaptiveDao().rewardCount())
        val game = after.startFormal(TrainingMode.COLOR, MonotonicClock { 10L }, "new-process", 2_000_003)
        assertEquals(8_000L, game.state.problem.conditions.memoryLimitMs)
        assertEquals(expectedContent, reopened.learningDao().problem("legacy-p-13")!!.content)
    }

    private fun createVersionOneFixture(): String {
        val schema = InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.example.memorysteps.data.LearningDatabase/1.json").bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
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
                    is Boolean -> content.put(key, value)
                    else -> error("Unsupported fixture value")
                } }
                sqlite.insertOrThrow(table, null, content)
            }
            repeat(2) { cycle ->
                val complete = cycle == 0
                val cycleId = "legacy-cycle-$cycle"
                row("training_cycles", mapOf("id" to cycleId, "mode" to "COLOR", "status" to if (complete) "COMPLETED" else "IN_PROGRESS",
                    "createdAt" to cycle * 100L, "completedAt" to if (complete) 99L else null, "rotationIndex" to 0,
                    "currentSlot" to if (complete) 9 else 4, "showSummary" to complete, "appVersion" to "0.1.0"))
                repeat(10) { slot ->
                    val number = cycle * 10 + slot
                    val done = number < 14
                    val problemId = "legacy-p-$number"
                    row("cycle_slots", mapOf("cycleId" to cycleId, "slotIndex" to slot, "type" to "COLOR", "memoryMs" to 20_000L,
                        "waitMs" to 3_000L, "optionCount" to 4, "solveMs" to null, "conditionVersion" to "initial-v1",
                        "finalizedProblemId" to if (done) problemId else null))
                    if (number <= 14) {
                        val p = ProblemGenerator(Random(number), ProblemIdSource { problemId }).generate(GameType.COLOR, GameConditions())
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
            sqlite.version = 1
        }
        return expected
    }
}
