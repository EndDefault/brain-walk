package com.example.memorysteps.data

import androidx.room.withTransaction
import com.example.memorysteps.BuildConfig
import com.example.memorysteps.game.*

class LearningRepository(private val db: LearningDatabase) {
    val dao = db.learningDao()

    suspend fun create(session: TrainingSession, runToken: String, at: Long) = db.withTransaction {
        val state = session.state
        require(!state.practice) { "Practice must not enter learning records" }
        check(dao.progress()?.activeCycleId == null) { "Training already in progress" }
        val rotation = dao.completedMixedCycles()
        dao.insertCycle(CycleEntity(state.sessionId, state.mode.name, "IN_PROGRESS", at, null,
            rotation % 3, 0, false, BuildConfig.VERSION_NAME))
        val c = state.problem.conditions
        dao.insertSlots(session.plan.mapIndexed { index, type ->
            SlotEntity(state.sessionId, index, type.name, c.memoryLimitMs, c.waitMs, c.optionCount, c.solveLimitMs)
        })
        dao.setProgress(ProgressEntity(activeCycleId = state.sessionId))
        saveInsideTransaction(state, runToken, at)
    }

    suspend fun save(state: TrainingSnapshot, runToken: String, at: Long) = db.withTransaction {
        require(!state.practice)
        saveInsideTransaction(state, runToken, at)
    }

    private suspend fun saveInsideTransaction(state: TrainingSnapshot, runToken: String, at: Long) {
        val cycle = checkNotNull(dao.cycle(state.sessionId))
        check(cycle.status != "STOPPED") { "Cycle is closed" }
        val slotIndex = state.questionNumber - 1
        val slots = dao.slots(cycle.id)
        val slot = slots[slotIndex]
        val result = state.round.result
        val status = when { result == null -> "ACTIVE"; result.valid -> "COMPLETED"; else -> "INVALID" }
        val old = dao.problem(state.problem.id)
        if (old == null) {
            check(slot.finalizedProblemId == null)
            val revision = dao.problems(cycle.id).count { it.slotIndex == slotIndex }
            dao.insertProblem(entity(state, runToken, revision, at, at, status))
        } else if (old.status == "ACTIVE") {
            check(old.runToken == runToken) { "Stale process write" }
            dao.updateProblem(entity(state, runToken, old.revision, old.generatedAt, at, status))
        } else {
            // A terminal record is immutable, even if a delayed callback arrives.
            check(old.outcome == result?.outcome?.name && old.attempts == state.round.choices.size) { "Terminal result mismatch" }
        }
        val savedChoices = dao.choices(state.problem.id)
        check(savedChoices.size <= state.round.choices.size)
        state.round.choices.drop(savedChoices.size).forEachIndexed { offset, choice ->
            dao.insertChoice(ChoiceEntity(state.problem.id, savedChoices.size + offset + 1, choice.optionId,
                choice.correct, choice.elapsedMs, choice.monotonicTimestampMs, at))
        }
        if (result?.valid == true) {
            check(slot.finalizedProblemId == null || slot.finalizedProblemId == state.problem.id)
            if (slot.finalizedProblemId == null) check(dao.finalizeSlot(cycle.id, slotIndex, state.problem.id) == 1)
        }
        val completed = dao.slots(cycle.id).count { it.finalizedProblemId != null } == 10
        dao.updateCycle(cycle.copy(currentSlot = slotIndex, showSummary = state.showSummary,
            status = if (completed) "COMPLETED" else cycle.status,
            completedAt = if (completed) cycle.completedAt ?: at else null))
        if (completed && dao.progress()?.activeCycleId == cycle.id) dao.setProgress(ProgressEntity(activeCycleId = null))
    }

    private fun entity(state: TrainingSnapshot, token: String, revision: Int, createdAt: Long, at: Long, status: String): ProblemEntity {
        val result = state.round.result
        val choices = state.round.choices
        return ProblemEntity(state.problem.id, state.sessionId, state.questionNumber - 1, revision, token,
            state.problem.generatorVersion, ProblemContentCodec.encode(state.problem), createdAt, at,
            state.round.phase.name, status, result?.outcome?.name,
            if (status == "INVALID") "SCREEN_LEFT" else null, state.round.actualMemoryMs,
            state.round.usedNextButton, result?.solveElapsedMs, choices.firstOrNull()?.elapsedMs,
            choices.firstOrNull()?.correct == true, result?.finalCorrect == true, choices.size,
            if (status == "INVALID") "EXCLUDED_INVALID" else "PENDING_ALGORITHM")
    }

    /** Recover from committed data only. Old process timer origins are never used. */
    suspend fun recoverActive(at: Long): SessionCheckpoint? = db.withTransaction {
        val id = dao.progress()?.activeCycleId ?: return@withTransaction null
        val cycle = checkNotNull(dao.cycle(id))
        check(cycle.status == "IN_PROGRESS")
        dao.problems(id).filter { it.status == "ACTIVE" }.forEach {
            dao.updateProblem(it.copy(status = "INVALID", outcome = RoundOutcome.INTERRUPTED.name,
                phase = RoundPhase.FINISHED.name, invalidReason = "PROCESS_OR_SCREEN_RESTART", updatedAt = at,
                learningStatus = "EXCLUDED_INVALID"))
        }
        val slots = dao.slots(id)
        val problems = dao.problems(id)
        val completed = slots.mapNotNull { slot ->
            slot.finalizedProblemId?.let { problemId ->
                val problem = problems.single { it.id == problemId }
                CompletedProblem(GameType.valueOf(slot.type), result(problem, slot))
            }
        }
        val current = problems.filter { it.slotIndex == cycle.currentSlot }.maxBy { it.revision }
        val slot = slots[cycle.currentSlot]
        SessionCheckpoint(id, TrainingMode.valueOf(cycle.mode), slots.map { GameType.valueOf(it.type) },
            cycle.currentSlot, ProblemContentCodec.decode(current, slot), result(current, slot), completed, cycle.showSummary)
    }

    private suspend fun result(problem: ProblemEntity, slot: SlotEntity): RoundResult {
        val content = ProblemContentCodec.decode(problem, slot)
        val choices = dao.choices(problem.id).map { choice ->
            ChoiceRecord(choice.optionId, content.options.single { it.id == choice.optionId }.item,
                choice.correct, choice.elapsedMs, choice.monotonicAt)
        }
        return RoundResult(problem.id, RoundOutcome.valueOf(checkNotNull(problem.outcome)), problem.actualMemoryMs,
            problem.usedNextButton, problem.solveElapsedMs, frozenCopy(choices))
    }

    suspend fun stop(id: String, at: Long) = db.withTransaction {
        val cycle = checkNotNull(dao.cycle(id))
        if (cycle.status == "IN_PROGRESS") {
            dao.problems(id).filter { it.status == "ACTIVE" }.forEach {
                dao.updateProblem(it.copy(status = "INVALID", outcome = RoundOutcome.INTERRUPTED.name,
                    invalidReason = "TRAINING_STOPPED", phase = RoundPhase.FINISHED.name, updatedAt = at,
                    learningStatus = "EXCLUDED_INVALID"))
            }
            dao.updateCycle(cycle.copy(status = "STOPPED"))
        }
        if (dao.progress()?.activeCycleId == id) dao.setProgress(ProgressEntity(activeCycleId = null))
    }
}
