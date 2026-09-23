package com.example.memorysteps.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.memorysteps.data.*
import com.example.memorysteps.game.*
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class TrainingViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LearningRepository(LearningDatabase.get(application))
    private val runToken = UUID.randomUUID().toString()
    private var session: TrainingSession? = null
    private var startPending = false
    private var eventTime = SystemClock.elapsedRealtime()
    private val clock = MonotonicClock { eventTime }
    private val tasks = Channel<Task>(Channel.UNLIMITED)
    private val navigation = Channel<Unit>(Channel.BUFFERED)
    val openTraining = navigation.receiveAsFlow()
    private val mutableState = MutableStateFlow<TrainingSnapshot?>(null)
    val state = mutableState.asStateFlow()
    private val mutableBusy = MutableStateFlow(true)
    val busy = mutableBusy.asStateFlow()
    private val mutableReady = MutableStateFlow(false)
    val ready = mutableReady.asStateFlow()
    private val mutableError = MutableStateFlow(false)
    val storageError = mutableError.asStateFlow()
    val overview = repository.dao.observeOverview().displayState(LearningOverview(0, 0, 0, 0))
    val typeStatistics = repository.dao.observeTypes().displayState(emptyList())
    val history = repository.dao.observeHistory().displayState(emptyList())
    val active = repository.dao.observeActive().displayState(null)

    private fun <T> Flow<T>.displayState(initial: T): StateFlow<T> = retryWhen { cause, _ ->
        if (cause is CancellationException) throw cause
        mutableError.value = true
        mutableError.filter { !it }.first()
        true
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    private class Task(val monotonicAt: Long, val wallAt: Long, val recover: Boolean, val action: suspend (Long) -> Unit)

    init {
        viewModelScope.launch {
            for (task in tasks) {
                if (mutableError.value && !task.recover) continue
                eventTime = task.monotonicAt
                try { task.action(task.wallAt) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { mutableError.value = true }
                finally { mutableBusy.value = false }
            }
        }
        reload()
    }

    /** Capturing time before queueing keeps disk latency out of answer measurements. */
    private fun enqueue(recover: Boolean = false, action: suspend (Long) -> Unit) {
        if (!mutableError.value || recover) tasks.trySend(Task(SystemClock.elapsedRealtime(), System.currentTimeMillis(), recover, action))
    }

    fun reload() = enqueue(recover = true) { at ->
        startPending = false
        mutableBusy.value = true
        val saved = repository.recoverActive(at)
        session = saved?.let { TrainingSession.restore(it, clock) }
        mutableState.value = session?.state
        mutableError.value = false
        mutableReady.value = true
    }

    fun start(mode: TrainingMode, practice: Boolean = mode != TrainingMode.MIXED) {
        if (startPending || !mutableReady.value || mutableError.value) return
        startPending = true
        enqueue { at ->
            try {
                mutableBusy.value = true
                if (practice) {
                    // A formal cycle remains in Room while the user practices.
                    session = TrainingSession(mode, 0, clock, practice = true)
                } else {
                    val saved = repository.recoverActive(at)
                    if (saved != null) session = TrainingSession.restore(saved, clock)
                    else {
                        val newSession = TrainingSession(mode, repository.dao.completedMixedCycles(), clock)
                        repository.create(newSession, runToken, at)
                        session = newSession
                    }
                }
                mutableState.value = session!!.state
                navigation.send(Unit)
            } finally {
                startPending = false
            }
        }
    }

    private fun change(id: String? = null, event: TrainingSession.() -> TrainingSnapshot) = enqueue { at ->
        val current = session ?: return@enqueue
        if (id != null && current.state.problem.id != id) return@enqueue
        val before = current.state
        val after = current.event()
        val needsWrite = before.problem.id != after.problem.id || before.round.phase != after.round.phase ||
            before.round.choices.size != after.round.choices.size || before.showSummary != after.showSummary
        if (!after.practice && needsWrite) {
            mutableBusy.value = true
            repository.save(after, runToken, at)
        }
        // A correct answer/result becomes visible only after its transaction commits.
        mutableState.value = after
    }

    fun memoryShown(id: String) = change(id) { memoryShown(id) }
    fun optionsShown(id: String) = change(id) { optionsShown(id) }
    fun next(id: String) = change(id) { next(id) }
    fun answer(id: String, optionId: String) = change(id) { answer(id, optionId) }
    fun advance(id: String) = change(id) { advance(id) }
    fun resume(id: String) = change(id) { resume(id) }
    fun tick() {
        if (!mutableBusy.value && mutableState.value?.round?.phase != RoundPhase.FINISHED) change { tick() }
    }
    fun pause(sessionId: String) = change {
        if (state.sessionId == sessionId) interrupt() else state
    }
    fun stopTraining() = enqueue { at ->
        mutableBusy.value = true
        repository.dao.progress()?.activeCycleId?.let { repository.stop(it, at) }
        if (session?.state?.practice != true) { session = null; mutableState.value = null }
    }
}
