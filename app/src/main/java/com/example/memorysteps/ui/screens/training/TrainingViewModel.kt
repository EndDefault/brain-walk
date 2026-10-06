package com.example.memorysteps.ui.screens.training

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.memorysteps.data.*
import com.example.memorysteps.game.*
import com.example.memorysteps.difficulty.AlgorithmMode
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import com.example.memorysteps.ai.author.*

class TrainingViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = LearningRepository(LearningDatabase.get(application))
    private val plans = QuestionPlanRepository(LearningDatabase.get(application))
    private val preparation = QuestionPreparation(viewModelScope, QuestionAuthors.create(application), plans)
    private var startJob: Job? = null
    private var requested: AuthorRequest? = null
    private var startSerial = 0L
    private val mutablePreparing = MutableStateFlow(false)
    val preparing = mutablePreparing.asStateFlow()
    private val mutablePreparationError = MutableStateFlow(false)
    val preparationError = mutablePreparationError.asStateFlow()
    private val mutableNextReady = MutableStateFlow(false)
    val nextReady = mutableNextReady.asStateFlow()
    private val mutableNextStatus = MutableStateFlow("")
    val nextStatus = mutableNextStatus.asStateFlow()
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
    val profiles = repository.adaptiveDao.observeProfiles().displayState(emptyList())
    val decisions = repository.adaptiveDao.observeDecisions().displayState(emptyList())
    val bundles = repository.adaptiveDao.observeBundles().displayState(emptyList())
    val arms = repository.adaptiveDao.observeArms().displayState(emptyList())
    val algorithmEpoch = repository.adaptiveDao.observeEpoch().displayState(null)

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
                        beginAuthoredStart()
                        return@enqueue
                    }
                }
                mutableState.value = session!!.state
                navigation.send(Unit)
            } finally {
                startPending = false
            }
        }
    }

    private fun beginAuthoredStart() {
        if (startJob?.isActive == true) return
        val serial = ++startSerial
        mutablePreparing.value = true
        mutablePreparationError.value = false
        startJob = viewModelScope.launch {
            try {
                val request = plans.request(System.currentTimeMillis()) ?: return@launch
                requested = request
                val plan = preparation.prepare(request).await()
                enqueue { at ->
                    if (!mutablePreparing.value || requested?.key != request.key || serial != startSerial) return@enqueue
                    session = repository.startAuthored(request, plan.id, clock, runToken, at)
                    mutableState.value = session!!.state
                    mutablePreparing.value = false
                    mutableNextReady.value = false
                    navigation.send(Unit)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutablePreparationError.value = true }
            catch (_: UnsatisfiedLinkError) { mutablePreparationError.value = true }
        }
    }

    fun cancelPreparation() {
        ++startSerial
        mutablePreparing.value = false
        mutablePreparationError.value = false
        requested = null
        startJob?.cancel(); startJob = null
        preparation.cancel()
    }

    fun retryPreparation() { startJob = null; beginAuthoredStart() }

    fun startFallback() {
        val request = requested ?: return
        val serial = ++startSerial
        preparation.cancel(); startJob?.cancel()
        enqueue { at ->
            if (serial != startSerial || !mutablePreparing.value) return@enqueue
            if (plans.request(at)?.key != request.key) { cancelPreparation(); return@enqueue }
            val plan = plans.fallback(request, if (mutablePreparationError.value) "MODEL_FAILED" else "USER_SKIPPED_WAIT")
            session = repository.startAuthored(request, plan.id, clock, runToken, at)
            mutableState.value = session!!.state
            mutablePreparing.value = false
            mutablePreparationError.value = false
            mutableNextReady.value = false
            navigation.send(Unit)
        }
    }

    private fun prepareNext() {
        viewModelScope.launch {
            try {
                mutableNextStatus.value = "PREPARING"
                val request = plans.request(System.currentTimeMillis()) ?: return@launch
                preparation.prepare(request).await()
                mutableNextReady.value = true
                mutableNextStatus.value = "READY"
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableNextReady.value = false; mutableNextStatus.value = "FAILED" }
            catch (_: UnsatisfiedLinkError) { mutableNextReady.value = false; mutableNextStatus.value = "FAILED" }
        }
    }

    override fun onCleared() { preparation.cancel(); super.onCleared() }

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
        if (!after.practice && after.complete && !before.complete) prepareNext()
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

    fun changeAlgorithm(mode: AlgorithmMode) = enqueue { at ->
        if (com.example.memorysteps.BuildConfig.DEBUG) {
            mutableBusy.value = true
            repository.changeAlgorithm(mode, at)
        }
    }
}
