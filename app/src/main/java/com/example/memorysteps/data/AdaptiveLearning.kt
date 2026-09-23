package com.example.memorysteps.data

import com.example.memorysteps.ai.ArmState
import com.example.memorysteps.ai.DiscountedUcb
import com.example.memorysteps.difficulty.*
import com.example.memorysteps.game.*
import java.util.UUID

/** All operations participate in the caller's Room transaction, including reward and cycle completion. */
internal class AdaptiveLearning(private val db: LearningDatabase) {
    val dao = db.adaptiveDao()
    private val records = db.learningDao()
    private fun id() = UUID.randomUUID().toString()

    suspend fun initialize(at: Long) {
        if (dao.config() != null) return
        val epoch = AlgorithmEpochEntity(id(), AlgorithmMode.BANDIT.name, at)
        dao.insertEpoch(epoch)
        dao.setConfig(AlgorithmConfigEntity(epochId = epoch.id))
        GameType.entries.forEach { type ->
            dao.setDifficulty(DifficultyEntity(type.name))
            DiscountedUcb.actions.forEach { action -> dao.setArm(BanditArmEntity(type.name, action.name,
                DiscountedUcb.VERSION, false, 0.0, 0.0, at)) }
        }
        records.progress()?.activeCycleId?.let { cycleId ->
            val cycle = checkNotNull(records.cycle(cycleId))
            records.updateCycle(cycle.copy(modeEpochId = epoch.id))
        }
        // Old records were collected before any policy was applied. Use one recent calibration
        // window per type, without inventing counterfactual bandit rewards for historical games.
        dao.legacyObservations().groupBy { it.type }.forEach { (type, all) ->
            all.forEach { dao.markProblem(it.problem.id, "LEGACY_RECORD_ONLY") }
            val eligible = all.filter { GameConditions(it.memoryMs, it.waitMs, it.optionCount, it.solveMs) == GameConditions() }
            val generator = eligible.lastOrNull()?.problem?.generatorVersion ?: return@forEach
            val window = eligible.filter { it.problem.generatorVersion == generator }.takeLast(10)
            if (window.isNotEmpty()) {
                val state = checkNotNull(dao.difficulty(type))
                val bundle = newBundle(state, epoch.id, generator, "LEGACY_CALIBRATION", at)
                window.forEach { include(bundle, it.problem, at) }
            }
        }
        if (records.progress()?.activeCycleId == null) applyPending(at)
    }

    private suspend fun newBundle(state: DifficultyEntity, epoch: String, generator: String, origin: String, at: Long): BundleEntity {
        val bundle = BundleEntity(id(), state.type, epoch, state.conditionVersion, AdaptiveCodec.conditions(state.conditions()),
            generator, state.appliedDecisionId, "OPEN", origin, at)
        dao.insertBundle(bundle)
        return bundle
    }

    suspend fun completed(problem: ProblemEntity, slot: SlotEntity, cycle: CycleEntity, at: Long) {
        check(problem.status == "COMPLETED")
        if (dao.member(problem.id) != null) return
        val config = checkNotNull(dao.config())
        check(cycle.modeEpochId == config.epochId)
        val state = checkNotNull(dao.difficulty(slot.type))
        check(state.conditionVersion == slot.conditionVersion)
        check(state.conditions() == GameConditions(slot.memoryMs, slot.waitMs, slot.optionCount, slot.solveMs))
        if (dao.pendingDecisions().any { it.type == slot.type }) {
            dao.markProblem(problem.id, "AFTER_BUNDLE_COMPLETE")
            return
        }
        val open = dao.openBundles(slot.type, config.epochId)
        check(open.size <= 1)
        val bundle = open.singleOrNull() ?: newBundle(state, config.epochId, problem.generatorVersion, "LIVE", at)
        check(bundle.conditionVersion == slot.conditionVersion && bundle.generatorVersion == problem.generatorVersion)
        include(bundle, problem, at)
    }

    private suspend fun include(bundle: BundleEntity, problem: ProblemEntity, at: Long) {
        val count = dao.memberCount(bundle.id)
        check(count < 10 && dao.bundle(bundle.id)?.status == "OPEN")
        dao.insertMember(BundleMemberEntity(problem.id, bundle.id, count))
        dao.markProblem(problem.id, "INCLUDED")
        if (count == 9) finish(bundle, at)
    }

    private suspend fun finish(bundle: BundleEntity, at: Long) {
        val observations = dao.observations(bundle.id)
        val stats = BundleStatistics.from(observations.map {
            Performance(it.firstCorrect, it.finalCorrect, it.actualMemoryMs, it.solveElapsedMs, it.attempts)
        })
        dao.updateBundle(bundle.copy(status = "COMPLETED", completedAt = at, firstCorrect = stats.firstCorrect,
            memorySumMs = stats.memorySumMs, memoryCount = stats.memoryCount, solveSumMs = stats.solveSumMs,
            weightedSumMs = stats.weightedSumMs, solvedCount = stats.solvedCount))
        val previous = bundle.sourceDecisionId?.let { dao.decision(it) }
        if (previous?.source == "BANDIT" && previous.rewardStatus == "WAITING") {
            check(previous.status == "APPLIED" && previous.epochId == bundle.epochId && previous.inputBundleId != bundle.id)
            if (dao.reward(previous.id) == null) {
                val reward = DiscountedUcb.reward(stats.firstCorrect)
                val updated = DiscountedUcb.update(armState(bundle.type), LearningAction.valueOf(previous.action), reward)
                updated.forEach { (action, arm) -> saveArm(bundle.type, action, arm, at) }
                dao.insertReward(RewardEntity(previous.id, bundle.id, stats.firstCorrect, reward, at))
                dao.updateDecision(previous.copy(rewardStatus = "REWARDED"))
            }
        }
        val state = checkNotNull(dao.difficulty(bundle.type))
        check(state.conditionVersion == bundle.conditionVersion)
        val reductions = dao.reductions(bundle.type).map { Reduction(it.id, ReductionAxis.valueOf(it.axis), it.remainingMs) }
        val plan = DifficultyRules.evaluate(state.conditions(), stats, reductions)
        val mode = AlgorithmMode.valueOf(checkNotNull(dao.epoch(bundle.epochId)).mode)
        val bandit = if (!plan.fixed && mode == AlgorithmMode.BANDIT)
            DiscountedUcb.choose(plan.candidates.map { it.action }.toSet(), armState(bundle.type)) else null
        val chosen = when {
            plan.fixed -> plan.candidates.single()
            bandit != null -> plan.candidates.single { it.action == bandit.action }
            else -> DifficultyRules.comparison(plan, stats)
        }
        val source = when { plan.fixed -> "FIXED"; bandit != null -> "BANDIT"; else -> "COMPARISON" }
        check(dao.pendingDecisions().none { it.type == bundle.type })
        dao.insertDecision(DecisionEntity(id(), bundle.type, bundle.epochId, bundle.id, DiscountedUcb.VERSION,
            source, chosen.action.name, bandit?.reason ?: source, state.conditionVersion,
            AdaptiveCodec.conditions(state.conditions()), AdaptiveCodec.conditions(chosen.conditions),
            AdaptiveCodec.candidates(plan.candidates, bandit?.scores ?: emptyMap()), AdaptiveCodec.restorations(plan.restorations),
            plan.closeSolveHistory, "PENDING", if (source == "BANDIT") "PENDING_APPLY" else "NOT_ELIGIBLE", at))
    }

    suspend fun applyPending(at: Long) {
        dao.pendingDecisions().forEach { decision ->
            val state = checkNotNull(dao.difficulty(decision.type))
            val before = AdaptiveCodec.conditions(decision.beforeConditions)
            val after = AdaptiveCodec.conditions(decision.afterConditions)
            check(state.conditionVersion == decision.beforeVersion && state.conditions() == before)
            check(decision.epochId == dao.config()?.epochId)
            val reductions = dao.reductions(decision.type).associateBy { it.id }
            AdaptiveCodec.restorations(decision.restorations).forEach { event ->
                val old = reductions.getValue(event.reductionId)
                check(event.amountMs in 1..old.remainingMs)
                val remaining = old.remainingMs - event.amountMs
                dao.updateReduction(old.copy(remainingMs = remaining, status = if (remaining == 0L) "RESTORED" else "ACTIVE"))
                dao.insertRestoration(RestorationEntity(decision.id, old.id, event.amountMs))
            }
            if (decision.closeSolveHistory || (before.solveLimitMs != null && after.solveLimitMs == null)) dao.closeSolveReductions(decision.type)
            if (after.memoryLimitMs < before.memoryLimitMs) dao.insertReduction(ReductionEntity(type = decision.type,
                axis = ReductionAxis.MEMORY.name, decisionId = decision.id, beforeMs = before.memoryLimitMs,
                afterMs = after.memoryLimitMs, remainingMs = before.memoryLimitMs - after.memoryLimitMs))
            if (before.solveLimitMs != null && after.solveLimitMs != null && after.solveLimitMs < before.solveLimitMs)
                dao.insertReduction(ReductionEntity(type = decision.type, axis = ReductionAxis.SOLVE.name, decisionId = decision.id,
                    beforeMs = before.solveLimitMs, afterMs = after.solveLimitMs, remainingMs = before.solveLimitMs - after.solveLimitMs))
            dao.setDifficulty(DifficultyEntity(decision.type, after.memoryLimitMs, after.waitMs, after.optionCount, after.solveLimitMs,
                decision.id, decision.id))
            if (decision.source == "BANDIT") {
                val action = LearningAction.valueOf(decision.action)
                val arm = armState(decision.type).getValue(action)
                saveArm(decision.type, action, arm.copy(hasEverApplied = true), at)
            }
            dao.updateDecision(decision.copy(status = "APPLIED", appliedAt = at,
                rewardStatus = if (decision.source == "BANDIT") "WAITING" else "NOT_ELIGIBLE"))
        }
    }

    suspend fun changeMode(mode: AlgorithmMode, at: Long): Boolean {
        initialize(at)
        if (records.progress()?.activeCycleId != null) return false
        val old = checkNotNull(dao.epoch(checkNotNull(dao.config()).epochId))
        if (old.mode == mode.name) return true
        applyPending(at)
        dao.closeBundles(old.id)
        dao.cancelRewards(old.id)
        dao.updateEpoch(old.copy(closedAt = at))
        val next = AlgorithmEpochEntity(id(), mode.name, at)
        dao.insertEpoch(next)
        dao.setConfig(AlgorithmConfigEntity(epochId = next.id))
        dao.difficulties().forEach { dao.setDifficulty(it.copy(appliedDecisionId = null)) }
        return true
    }

    private suspend fun armState(type: String): Map<LearningAction, ArmState> = dao.arms(type, DiscountedUcb.VERSION)
        .associate { LearningAction.valueOf(it.action) to ArmState(it.hasEverApplied, it.effectiveCount, it.rewardSum) }
    private suspend fun saveArm(type: String, action: LearningAction, arm: ArmState, at: Long) =
        dao.setArm(BanditArmEntity(type, action.name, DiscountedUcb.VERSION, arm.hasEverApplied, arm.effectiveCount, arm.rewardSum, at))
}
