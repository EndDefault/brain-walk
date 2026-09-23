package com.example.memorysteps.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "training_cycles")
data class CycleEntity(
    @PrimaryKey val id: String,
    val mode: String,
    val status: String,
    val createdAt: Long,
    val completedAt: Long?,
    val rotationIndex: Int,
    val currentSlot: Int,
    val showSummary: Boolean,
    val appVersion: String,
    @ColumnInfo(defaultValue = "NULL") val modeEpochId: String? = null,
)

@Entity(tableName = "cycle_slots", primaryKeys = ["cycleId", "slotIndex"],
    foreignKeys = [ForeignKey(entity = CycleEntity::class, parentColumns = ["id"], childColumns = ["cycleId"])],
    indices = [Index(value = ["finalizedProblemId"], unique = true)])
data class SlotEntity(
    val cycleId: String, val slotIndex: Int, val type: String,
    val memoryMs: Long, val waitMs: Long, val optionCount: Int, val solveMs: Long?,
    val conditionVersion: String = "initial-v1",
    val finalizedProblemId: String? = null,
    @ColumnInfo(defaultValue = "NULL") val appliedDecisionId: String? = null,
)

@Entity(tableName = "problem_attempts",
    foreignKeys = [ForeignKey(entity = SlotEntity::class, parentColumns = ["cycleId", "slotIndex"], childColumns = ["cycleId", "slotIndex"])],
    indices = [Index(value = ["cycleId", "slotIndex", "revision"], unique = true)])
data class ProblemEntity(
    @PrimaryKey val id: String, val cycleId: String, val slotIndex: Int, val revision: Int,
    val runToken: String, val generatorVersion: String, val content: String,
    val generatedAt: Long, val updatedAt: Long, val phase: String, val status: String,
    val outcome: String?, val invalidReason: String?, val actualMemoryMs: Long?,
    val usedNextButton: Boolean, val solveElapsedMs: Long?, val firstChoiceMs: Long?,
    val firstCorrect: Boolean, val finalCorrect: Boolean, val attempts: Int,
    // Initial value for incomplete/v1 records; completed formal records are assigned atomically.
    val learningStatus: String = "PENDING_ALGORITHM",
)

@Entity(tableName = "choice_attempts", primaryKeys = ["problemId", "attemptIndex"],
    foreignKeys = [ForeignKey(entity = ProblemEntity::class, parentColumns = ["id"], childColumns = ["problemId"])],
    indices = [Index(value = ["problemId", "optionId"], unique = true)])
data class ChoiceEntity(
    val problemId: String, val attemptIndex: Int, val optionId: String,
    val correct: Boolean, val elapsedMs: Long, val monotonicAt: Long, val wallClockAt: Long,
)

@Entity(tableName = "training_progress")
data class ProgressEntity(@PrimaryKey val singleton: Int = 1, val activeCycleId: String?)

data class LearningOverview(val completedCycles: Int, val problems: Int, val firstCorrect: Int, val finalCorrect: Int)
data class TypeStatistics(val type: String, val problems: Int, val firstCorrect: Int, val finalCorrect: Int)
data class CycleHistory(
    val id: String, val mode: String, val status: String, val createdAt: Long, val completedAt: Long?,
    val problems: Int, val firstCorrect: Int,
)

private const val HISTORY_QUERY = """
    SELECT c.id, c.mode, c.status, c.createdAt, c.completedAt,
        COUNT(p.id) AS problems, COALESCE(SUM(p.firstCorrect), 0) AS firstCorrect
    FROM training_cycles c LEFT JOIN cycle_slots s ON c.id = s.cycleId
    LEFT JOIN problem_attempts p ON s.finalizedProblemId = p.id
"""

@Dao
interface LearningDao {
    @Insert suspend fun insertCycle(cycle: CycleEntity)
    @Update suspend fun updateCycle(cycle: CycleEntity)
    @Insert suspend fun insertSlots(slots: List<SlotEntity>)
    @Insert suspend fun insertProblem(problem: ProblemEntity)
    @Update suspend fun updateProblem(problem: ProblemEntity)
    @Insert suspend fun insertChoice(choice: ChoiceEntity)
    @Upsert suspend fun setProgress(progress: ProgressEntity)
    @Query("SELECT * FROM training_progress WHERE singleton = 1") suspend fun progress(): ProgressEntity?
    @Query("SELECT * FROM training_cycles WHERE id = :id") suspend fun cycle(id: String): CycleEntity?
    @Query("SELECT * FROM cycle_slots WHERE cycleId = :id ORDER BY slotIndex") suspend fun slots(id: String): List<SlotEntity>
    @Query("SELECT * FROM problem_attempts WHERE id = :id") suspend fun problem(id: String): ProblemEntity?
    @Query("SELECT * FROM problem_attempts WHERE cycleId = :id ORDER BY slotIndex, revision") suspend fun problems(id: String): List<ProblemEntity>
    @Query("SELECT * FROM choice_attempts WHERE problemId = :id ORDER BY attemptIndex") suspend fun choices(id: String): List<ChoiceEntity>
    @Query("UPDATE cycle_slots SET finalizedProblemId = :problemId WHERE cycleId = :cycleId AND slotIndex = :slot AND finalizedProblemId IS NULL")
    suspend fun finalizeSlot(cycleId: String, slot: Int, problemId: String): Int
    @Query("SELECT COUNT(*) FROM training_cycles WHERE status = 'COMPLETED' AND mode = 'MIXED'") suspend fun completedMixedCycles(): Int
    @Query("SELECT COUNT(*) FROM problem_attempts") suspend fun problemCount(): Int
    @Query("SELECT COUNT(*) FROM training_cycles") suspend fun cycleCount(): Int
    @Query("""SELECT
        (SELECT COUNT(*) FROM training_cycles WHERE status = 'COMPLETED') AS completedCycles,
        COUNT(*) AS problems, COALESCE(SUM(firstCorrect), 0) AS firstCorrect,
        COALESCE(SUM(finalCorrect), 0) AS finalCorrect
        FROM problem_attempts WHERE status = 'COMPLETED'""")
    fun observeOverview(): Flow<LearningOverview>
    @Query("""SELECT s.type, COUNT(p.id) AS problems, COALESCE(SUM(p.firstCorrect), 0) AS firstCorrect,
        COALESCE(SUM(p.finalCorrect), 0) AS finalCorrect FROM cycle_slots s
        JOIN problem_attempts p ON s.finalizedProblemId = p.id GROUP BY s.type""")
    fun observeTypes(): Flow<List<TypeStatistics>>
    @Query(HISTORY_QUERY + " GROUP BY c.id ORDER BY c.createdAt DESC, c.id DESC LIMIT 30")
    fun observeHistory(): Flow<List<CycleHistory>>
    @Query(HISTORY_QUERY + " WHERE c.id = (SELECT activeCycleId FROM training_progress WHERE singleton = 1) GROUP BY c.id")
    fun observeActive(): Flow<CycleHistory?>
}

@Database(entities = [CycleEntity::class, SlotEntity::class, ProblemEntity::class, ChoiceEntity::class, ProgressEntity::class,
    AlgorithmEpochEntity::class, AlgorithmConfigEntity::class, DifficultyEntity::class, BundleEntity::class,
    BundleMemberEntity::class, DecisionEntity::class, BanditArmEntity::class, RewardEntity::class,
    ReductionEntity::class, RestorationEntity::class],
    version = 3, exportSchema = true, autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)])
abstract class LearningDatabase : RoomDatabase() {
    abstract fun learningDao(): LearningDao
    abstract fun adaptiveDao(): AdaptiveDao
    companion object {
        @Volatile private var instance: LearningDatabase? = null
        fun get(context: Context): LearningDatabase = instance ?: synchronized(this) {
            instance ?: open(context.applicationContext, "memory-steps.db").also { instance = it }
        }
        // No destructive fallback: future changes must supply a preserving migration.
        fun open(context: Context, name: String): LearningDatabase =
            Room.databaseBuilder(context, LearningDatabase::class.java, name).build()
    }
}
