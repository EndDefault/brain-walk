package com.example.memorysteps.ai.author

import android.os.SystemClock
import com.example.memorysteps.data.AuthoredPlanEntity
import com.example.memorysteps.data.QuestionPlanRepository
import kotlinx.coroutines.*

/** One background request at a time; a prepared plan is consumed by one game. */
class QuestionPreparation(private val scope: CoroutineScope, private val author: QuestionAuthor,
                          private val plans: QuestionPlanRepository) {
    private var key: String? = null
    private var task: Deferred<AuthoredPlanEntity>? = null
    fun prepare(request: AuthorRequest): Deferred<AuthoredPlanEntity> {
        if (key == request.key && task?.isCancelled == false && task?.isCompleted == false) return task!!
        key = request.key
        task = scope.async(Dispatchers.IO) {
            plans.cached(request)?.let { return@async it }
            val started = SystemClock.elapsedRealtime()
            val result = author.compose(request).validate(request)
            ensureActive()
            plans.save(request, result.problems(request), "MODEL", SystemClock.elapsedRealtime() - started)
        }
        return task!!
    }
    fun cancel() { author.cancel(); task?.cancel(); task = null; key = null }
}
