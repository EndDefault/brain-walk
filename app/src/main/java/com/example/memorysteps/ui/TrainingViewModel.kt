package com.example.memorysteps.ui

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import com.example.memorysteps.game.MonotonicClock
import com.example.memorysteps.game.TrainingMode
import com.example.memorysteps.game.TrainingSession
import com.example.memorysteps.game.TrainingSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Activity scoped: survives rotation and home navigation, not process termination. */
class TrainingViewModel : ViewModel() {
    private var session: TrainingSession? = null
    private var completedMixedCycles = 0
    private var countedCompletion = false
    private val mutableState = MutableStateFlow<TrainingSnapshot?>(null)
    val state = mutableState.asStateFlow()

    fun start(mode: TrainingMode) {
        if (session != null && mutableState.value?.complete != true) return
        countedCompletion = false
        session = TrainingSession(mode, completedMixedCycles, MonotonicClock { SystemClock.elapsedRealtime() })
        publish(session!!.state)
    }

    fun memoryShown(id: String) { session?.let { publish(it.memoryShown(id)) } }
    fun optionsShown(id: String) { session?.let { publish(it.optionsShown(id)) } }
    fun next(id: String) { session?.let { publish(it.next(id)) } }
    fun answer(id: String, optionId: String) { session?.let { publish(it.answer(id, optionId)) } }
    fun advance(id: String) { session?.let { publish(it.advance(id)) } }
    fun resume(id: String) { session?.let { publish(it.resume(id)) } }
    fun tick() { session?.let { publish(it.tick()) } }
    fun pause(sessionId: String) {
        if (mutableState.value?.sessionId == sessionId) session?.let { publish(it.interrupt()) }
    }
    fun discard() { session = null; mutableState.value = null }

    private fun publish(snapshot: TrainingSnapshot) {
        if (snapshot.complete && !countedCompletion) {
            countedCompletion = true
            if (snapshot.mode == TrainingMode.MIXED) completedMixedCycles++
        }
        mutableState.value = snapshot
    }
}
