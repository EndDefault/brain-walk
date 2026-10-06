package com.example.memorysteps.data

import androidx.room.*
import com.example.memorysteps.game.GameConditions
import kotlinx.coroutines.flow.Flow

object LearningScope {
    const val COMBINED = "COMBINED"
    const val PER_TYPE = "PER_TYPE"
}

@Entity(tableName = "algorithm_epochs")
data class AlgorithmEpochEntity(@PrimaryKey val id: String, val mode: String, val openedAt: Long, val closedAt: Long? = null)

@Entity(tableName = "algorithm_config")
data class AlgorithmConfigEntity(@PrimaryKey val singleton: Int = 1, val epochId: String,
    @ColumnInfo(defaultValue = "'PER_TYPE'") val learningScope: String = LearningScope.COMBINED)

@Entity(tableName = "difficulty_states")
data class DifficultyEntity(
    @PrimaryKey val type: String, val memoryMs: Long = 20_000, val waitMs: Long = 3_000,
    val optionCount: Int = 4, val solveMs: Long? = null,
    val conditionVersion: String = "initial-v1", val appliedDecisionId: String? = null,
) {
    fun conditions() = GameConditions(memoryMs, waitMs, optionCount, solveMs)
}

@Entity(tableName = "learning_bundles", indices = [Index(value = ["type", "epochId", "status"]), Index(value = ["cycleId"], unique = true)])
data class BundleEntity(
    @PrimaryKey val id: String, val type: String, val epochId: String, val conditionVersion: String,
    val conditions: String, val generatorVersion: String, val sourceDecisionId: String?,
    val status: String, val origin: String, val createdAt: Long, val completedAt: Long? = null,
    val firstCorrect: Int? = null, val memorySumMs: Long? = null, val memoryCount: Int? = null,
    val solveSumMs: Long? = null, val weightedSumMs: Long? = null, val solvedCount: Int? = null,
    @ColumnInfo(defaultValue = "NULL") val cycleId: String? = null,
)

@Entity(tableName = "bundle_members",
    foreignKeys = [ForeignKey(entity = BundleEntity::class, parentColumns = ["id"], childColumns = ["bundleId"]),
        ForeignKey(entity = ProblemEntity::class, parentColumns = ["id"], childColumns = ["problemId"])],
    indices = [Index(value = ["bundleId", "ordinal"], unique = true)])
data class BundleMemberEntity(@PrimaryKey val problemId: String, val bundleId: String, val ordinal: Int)

@Entity(tableName = "ai_decisions", indices = [Index(value = ["inputBundleId"], unique = true), Index(value = ["type", "status"])])
data class DecisionEntity(
    @PrimaryKey val id: String, val type: String, val epochId: String, val inputBundleId: String,
    val algorithmVersion: String, val source: String, val action: String, val reason: String,
    val beforeVersion: String, val beforeConditions: String, val afterConditions: String,
    val candidates: String, val restorations: String, val closeSolveHistory: Boolean,
    val status: String, val rewardStatus: String, val createdAt: Long, val appliedAt: Long? = null,
)

@Entity(tableName = "bandit_arms", primaryKeys = ["type", "action", "algorithmVersion"])
data class BanditArmEntity(val type: String, val action: String, val algorithmVersion: String,
    val hasEverApplied: Boolean, val effectiveCount: Double, val rewardSum: Double, val updatedAt: Long)

@Entity(tableName = "reward_receipts", indices = [Index(value = ["bundleId"], unique = true)])
data class RewardEntity(@PrimaryKey val decisionId: String, val bundleId: String, val firstCorrect: Int, val reward: Double, val appliedAt: Long)

@Entity(tableName = "reduction_history", indices = [Index(value = ["type", "axis", "status"])])
data class ReductionEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val type: String, val axis: String,
    val decisionId: String, val beforeMs: Long, val afterMs: Long, val remainingMs: Long, val status: String = "ACTIVE")

@Entity(tableName = "restoration_events", primaryKeys = ["decisionId", "reductionId"])
data class RestorationEntity(val decisionId: String, val reductionId: Long, val amountMs: Long)

data class LearningProfile(@Embedded val difficulty: DifficultyEntity, val collected: Int, val pending: Boolean)
data class LegacyObservation(@Embedded val problem: ProblemEntity, val type: String,
    val memoryMs: Long, val waitMs: Long, val optionCount: Int, val solveMs: Long?)

@Dao
interface AdaptiveDao {
    @Query("SELECT * FROM algorithm_config WHERE singleton = 1") suspend fun config(): AlgorithmConfigEntity?
    @Upsert suspend fun setConfig(config: AlgorithmConfigEntity)
    @Insert suspend fun insertEpoch(epoch: AlgorithmEpochEntity)
    @Update suspend fun updateEpoch(epoch: AlgorithmEpochEntity)
    @Query("SELECT * FROM algorithm_epochs WHERE id = :id") suspend fun epoch(id: String): AlgorithmEpochEntity?
    @Query("SELECT e.* FROM algorithm_epochs e JOIN algorithm_config c ON c.epochId = e.id") fun observeEpoch(): Flow<AlgorithmEpochEntity?>
    @Upsert suspend fun setDifficulty(state: DifficultyEntity)
    @Query("SELECT * FROM difficulty_states WHERE type = :type") suspend fun difficulty(type: String): DifficultyEntity?
    @Query("SELECT * FROM difficulty_states ORDER BY type") suspend fun difficulties(): List<DifficultyEntity>
    @Insert suspend fun insertBundle(bundle: BundleEntity)
    @Update suspend fun updateBundle(bundle: BundleEntity)
    @Query("SELECT * FROM learning_bundles WHERE type = :type AND epochId = :epoch AND status = 'OPEN'") suspend fun openBundles(type: String, epoch: String): List<BundleEntity>
    @Query("SELECT * FROM learning_bundles WHERE id = :id") suspend fun bundle(id: String): BundleEntity?
    @Query("SELECT * FROM learning_bundles WHERE cycleId = :cycleId") suspend fun cycleBundle(cycleId: String): BundleEntity?
    @Insert suspend fun insertMember(member: BundleMemberEntity)
    @Query("SELECT COUNT(*) FROM bundle_members WHERE bundleId = :id") suspend fun memberCount(id: String): Int
    @Query("SELECT * FROM bundle_members WHERE problemId = :id") suspend fun member(id: String): BundleMemberEntity?
    @Query("SELECT p.* FROM problem_attempts p JOIN bundle_members m ON m.problemId = p.id WHERE m.bundleId = :id ORDER BY m.ordinal")
    suspend fun observations(id: String): List<ProblemEntity>
    @Insert suspend fun insertDecision(decision: DecisionEntity)
    @Update suspend fun updateDecision(decision: DecisionEntity)
    @Query("SELECT * FROM ai_decisions WHERE id = :id") suspend fun decision(id: String): DecisionEntity?
    @Query("SELECT * FROM ai_decisions WHERE status = 'PENDING' ORDER BY createdAt, id") suspend fun pendingDecisions(): List<DecisionEntity>
    @Query("SELECT * FROM ai_decisions WHERE type = 'COMBINED' ORDER BY createdAt DESC, id DESC LIMIT 30") fun observeDecisions(): Flow<List<DecisionEntity>>
    @Upsert suspend fun setArm(arm: BanditArmEntity)
    @Query("SELECT * FROM bandit_arms WHERE type = :type AND algorithmVersion = :version ORDER BY action") suspend fun arms(type: String, version: String): List<BanditArmEntity>
    @Query("SELECT * FROM bandit_arms WHERE type = 'COMBINED' ORDER BY action") fun observeArms(): Flow<List<BanditArmEntity>>
    @Insert suspend fun insertReward(reward: RewardEntity)
    @Query("SELECT * FROM reward_receipts WHERE decisionId = :id") suspend fun reward(id: String): RewardEntity?
    @Query("SELECT COUNT(*) FROM reward_receipts") suspend fun rewardCount(): Int
    @Query("SELECT COUNT(*) FROM ai_decisions") suspend fun decisionCount(): Int
    @Insert suspend fun insertReduction(reduction: ReductionEntity): Long
    @Update suspend fun updateReduction(reduction: ReductionEntity)
    @Query("SELECT * FROM reduction_history WHERE type = :type AND status = 'ACTIVE' ORDER BY id") suspend fun reductions(type: String): List<ReductionEntity>
    @Insert suspend fun insertRestoration(event: RestorationEntity)
    @Query("UPDATE reduction_history SET status = 'CLOSED' WHERE type = :type AND axis = 'SOLVE' AND status = 'ACTIVE'") suspend fun closeSolveReductions(type: String)
    @Query("UPDATE learning_bundles SET status = 'CLOSED_BY_MODE_CHANGE' WHERE epochId = :epoch AND status = 'OPEN'") suspend fun closeBundles(epoch: String)
    @Query("UPDATE ai_decisions SET rewardStatus = 'CANCELLED_BY_MODE_CHANGE' WHERE epochId = :epoch AND rewardStatus = 'WAITING'") suspend fun cancelRewards(epoch: String)
    @Query("UPDATE learning_bundles SET status = 'CLOSED_BY_SCOPE_CHANGE' WHERE status = 'OPEN'") suspend fun closeLegacyBundles()
    @Query("UPDATE ai_decisions SET status = 'CANCELLED_BY_SCOPE_CHANGE' WHERE status = 'PENDING'") suspend fun cancelLegacyDecisions()
    @Query("UPDATE ai_decisions SET rewardStatus = 'CANCELLED_BY_SCOPE_CHANGE' WHERE rewardStatus IN ('WAITING', 'PENDING_APPLY')") suspend fun cancelLegacyRewards()
    @Query("UPDATE learning_bundles SET status = 'CLOSED_BY_CYCLE_STOPPED' WHERE cycleId = :cycleId AND status = 'OPEN'") suspend fun stopCycleBundle(cycleId: String)
    @Query("UPDATE problem_attempts SET learningStatus = 'LEGACY_RECORD_ONLY' WHERE status = 'COMPLETED' AND learningStatus = 'PENDING_ALGORITHM'") suspend fun markLegacyRecords()
    @Query("UPDATE problem_attempts SET learningStatus = :status WHERE id = :id") suspend fun markProblem(id: String, status: String)
    @Query("""SELECT p.*, s.type, s.memoryMs, s.waitMs, s.optionCount, s.solveMs
        FROM problem_attempts p JOIN cycle_slots s ON s.finalizedProblemId = p.id
        WHERE p.status = 'COMPLETED' AND p.learningStatus = 'PENDING_ALGORITHM'
        ORDER BY p.updatedAt, p.id""") suspend fun legacyObservations(): List<LegacyObservation>
    @Query("""SELECT d.*, COUNT(m.problemId) AS collected,
        EXISTS(SELECT 1 FROM ai_decisions a WHERE a.type = d.type AND a.status = 'PENDING') AS pending
        FROM difficulty_states d LEFT JOIN learning_bundles b ON b.type = d.type AND b.status = 'OPEN'
        LEFT JOIN bundle_members m ON m.bundleId = b.id WHERE d.type = 'COMBINED' GROUP BY d.type""")
    fun observeProfiles(): Flow<List<LearningProfile>>
    @Query("SELECT * FROM learning_bundles WHERE type = 'COMBINED' ORDER BY createdAt DESC, id DESC LIMIT 30") fun observeBundles(): Flow<List<BundleEntity>>
}
