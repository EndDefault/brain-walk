package com.example.memorysteps.data

import androidx.room.*
import com.example.memorysteps.ai.author.*
import com.example.memorysteps.game.*
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID
import kotlin.random.Random

@Entity(tableName = "authored_plans", indices = [Index("requestKey"), Index(value = ["cycleId"], unique = true)])
data class AuthoredPlanEntity(@PrimaryKey val id: String, val requestKey: String, val source: String,
    val modelVersion: String?, val problems: String, val elapsedMs: Long, val fallbackReason: String?,
    val createdAt: Long, val cycleId: String? = null)

@Dao
interface AuthoredPlanDao {
    @Insert suspend fun insert(plan: AuthoredPlanEntity)
    @Query("SELECT * FROM authored_plans WHERE requestKey = :key AND cycleId IS NULL ORDER BY createdAt DESC LIMIT 1")
    suspend fun ready(key: String): AuthoredPlanEntity?
    @Query("SELECT * FROM authored_plans WHERE id = :id") suspend fun get(id: String): AuthoredPlanEntity?
    @Query("SELECT * FROM authored_plans WHERE cycleId = :cycle") suspend fun forCycle(cycle: String): AuthoredPlanEntity?
    @Query("UPDATE authored_plans SET cycleId = :cycle WHERE id = :id AND cycleId IS NULL")
    suspend fun consume(id: String, cycle: String): Int
    @Query("DELETE FROM authored_plans WHERE cycleId IS NULL AND requestKey != :key") suspend fun discardStale(key: String)
    @Query("""SELECT p.* FROM problem_attempts p JOIN cycle_slots s ON p.cycleId=s.cycleId AND p.slotIndex=s.slotIndex
        WHERE p.status='COMPLETED' AND s.type=:type AND s.memoryMs=:memory AND s.waitMs=:wait
        AND s.optionCount=:options AND ((s.solveMs IS NULL AND :solve IS NULL) OR s.solveMs=:solve)
        ORDER BY p.updatedAt DESC, p.id DESC LIMIT 60""")
    suspend fun recent(type: String, memory: Long, wait: Long, options: Int, solve: Long?): List<ProblemEntity>
}

class QuestionPlanRepository(private val db: LearningDatabase) {
    suspend fun request(at: Long): AuthorRequest? = db.withTransaction {
        val adaptive = AdaptiveLearning(db)
        adaptive.initialize(at)
        if (db.learningDao().progress()?.activeCycleId != null) return@withTransaction null
        adaptive.applyPending(at)
        val difficulty = checkNotNull(db.adaptiveDao().difficulty(LearningScope.COMBINED))
        val conditions = difficulty.conditions()
        val records = JSONObject()
        val excluded = mutableSetOf<String>()
        for (type in GameType.entries) {
            val rows = db.authoredPlanDao().recent(type.name, conditions.memoryLimitMs, conditions.waitMs, conditions.optionCount, conditions.solveLimitMs)
            val problems = rows.associate { it.id to ProblemContentCodec.decode(it.id, it.content, it.generatorVersion, conditions) }
            val recent = rows.take(4).map { AuthorCatalog.id(problems.getValue(it.id).target) }.distinct()
            excluded.addAll(recent)
            val confusions = mutableListOf<Pair<String, String>>()
            for (row in rows.filter { !it.firstCorrect }) {
                val problem = problems.getValue(row.id)
                val target = AuthorCatalog.id(problem.target)
                val choice = db.learningDao().choices(row.id).firstOrNull()
                if (target !in recent && choice != null && !choice.correct) {
                    confusions += target to AuthorCatalog.id(problem.options.single { it.id == choice.optionId }.item)
                }
            }
            val confusion = confusions.groupingBy { it }.eachCount().maxByOrNull { it.value }
            val correctTimes = rows.filter { it.firstCorrect }.mapNotNull { it.firstChoiceMs }
            records.put(type.name, JSONObject().put("n", rows.size).put("first_correct", rows.count { it.firstCorrect })
                .put("mean_correct_ms", if (correctTimes.isEmpty()) 0 else correctTimes.average().toLong())
                .put("recent", JSONArray(recent)).put("confusion", confusion?.let {
                    JSONArray(listOf(it.key.first, it.key.second, it.value))
                } ?: JSONObject.NULL))
        }
        val rotation = db.learningDao().completedMixedCycles()
        val identity = "${db.adaptiveDao().config()?.epochId}:${difficulty.conditionVersion}:$rotation:$records"
        val key = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).joinToString("") { "%02x".format(it) }
        val slots = GameType.entries.flatMap { type -> List(if (type.ordinal == rotation % 3) 4 else 3) { type } }.shuffled(Random(key.hashCode()))
        val context = JSONObject().put("contract", "question-author-v1").put("option_count", conditions.optionCount)
            .put("slots", JSONArray(slots.map { it.name })).put("records", records).toString()
        AuthorRequest(key, slots, conditions, context, excluded)
    }

    suspend fun cached(request: AuthorRequest): AuthoredPlanEntity? = db.authoredPlanDao().ready(request.key)
    suspend fun save(request: AuthorRequest, problems: List<MemoryProblem>, source: String, elapsed: Long, reason: String? = null): AuthoredPlanEntity {
        require(problems.size == 10 && problems.map { it.type } == request.slots)
        require(problems.all { it.conditions == request.conditions && it.generationSource == source })
        val row = AuthoredPlanEntity(UUID.randomUUID().toString(), request.key, source,
            if (source == "MODEL") NativeQuestionAuthor.MODEL_VERSION else null,
            JSONArray(problems.map { JSONObject().put("id", it.id).put("generator", it.generatorVersion)
                .put("content", JSONObject(ProblemContentCodec.encode(it))) }).toString(), elapsed, reason, System.currentTimeMillis())
        db.withTransaction { db.authoredPlanDao().discardStale(request.key); db.authoredPlanDao().insert(row) }
        return row
    }
    fun decode(row: AuthoredPlanEntity, conditions: List<GameConditions>): List<MemoryProblem> {
        val payload = JSONArray(row.problems)
        require(payload.length() == 10 && conditions.size == 10)
        return List(10) { i ->
            val item = payload.getJSONObject(i)
            ProblemContentCodec.decode(item.getString("id"), item.getJSONObject("content").toString(), item.getString("generator"), conditions[i])
        }
    }
    suspend fun fallback(request: AuthorRequest, reason: String): AuthoredPlanEntity {
        val generator = ProblemGenerator()
        val problems = request.slots.map { type ->
            val p = generator.generate(type, request.conditions)
            MemoryProblem(p.id, p.generatorVersion, p.conditions, p.target, p.options, "RULE_FALLBACK")
        }
        return save(request, problems, "RULE_FALLBACK", 0, reason)
    }
}
