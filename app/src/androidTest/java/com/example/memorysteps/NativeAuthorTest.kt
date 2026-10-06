package com.example.memorysteps

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import android.os.SystemClock
import com.example.memorysteps.ai.author.*
import com.example.memorysteps.data.*
import com.example.memorysteps.game.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Actual packaged weights + JNI + persisted game flow. No fake author. */
@RunWith(AndroidJUnit4::class)
class NativeAuthorTest {
    @Test fun packagedTrainedModelCreatesTenPlayablePersistedQuestionsOffline() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        require(context.packageName.endsWith(".difficultyvalidation"))
        val name = "native-author-validation.db"
        context.deleteDatabase(name)
        val db = LearningDatabase.open(context, name)
        try {
            val plans = QuestionPlanRepository(db)
            val request = checkNotNull(plans.request(1000))
            val start = SystemClock.elapsedRealtime()
            val result = NativeQuestionAuthor(context).compose(request).validate(request)
            val elapsed = SystemClock.elapsedRealtime() - start
            val problems = result.problems(request)
            assertEquals(10, problems.size)
            assertTrue(problems.all { it.generationSource == "MODEL" && it.modelVersion == NativeQuestionAuthor.MODEL_VERSION })
            val saved = plans.save(request, problems, "MODEL", elapsed)
            assertEquals(saved.id, plans.cached(request)?.id)
            var now = 0L
            val clock = MonotonicClock { now }
            val repository = LearningRepository(db)
            var session = repository.startAuthored(request, saved.id, clock, "native-test", 2000)
            val cycle = session.state.sessionId
            fun solve() {
                val p = session.state.problem
                session.memoryShown(p.id); session.next(p.id)
                now += p.conditions.waitMs; session.tick(); session.optionsShown(p.id)
                session.answer(p.id, p.options.single { it.item == p.target }.id)
                assertEquals(RoundOutcome.CORRECT, session.state.round.result?.outcome)
            }
            solve(); repository.save(session.state, "native-test", 3000)
            val checkpoint = checkNotNull(repository.recoverActive(4000))
            session = TrainingSession.restore(checkpoint, clock)
            for (index in 1..9) {
                session.advance(session.state.problem.id)
                assertEquals(problems[index].id, session.state.problem.id)
                assertEquals("MODEL", session.state.problem.generationSource)
                repository.save(session.state, "native-test", 4000L + index)
                solve(); repository.save(session.state, "native-test", 5000L + index)
            }
            assertTrue(session.state.complete)
            assertEquals(10, db.learningDao().problemCount())
            assertEquals(cycle, db.authoredPlanDao().get(saved.id)?.cycleId)
            assertNull(plans.cached(request))
            assertNotEquals(request.key, plans.request(7000)?.key)
            val report = JSONObject().put("source", "MODEL").put("model", NativeQuestionAuthor.MODEL_VERSION)
                .put("elapsedMs", elapsed).put("focus", result.focus).put("questions", JSONArray(result.questions))
                .put("completedQuestions", 10).put("restoredPlan", true)
            File(context.getExternalFilesDir(null), "verification").apply { mkdirs() }
                .resolve("native-author.json").writeText(report.toString(2))
            println("NATIVE_AUTHOR_VERIFIED elapsedMs=$elapsed questions=10 restored=true")
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
