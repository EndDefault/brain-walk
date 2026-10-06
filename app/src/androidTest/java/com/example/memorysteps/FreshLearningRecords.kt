package com.example.memorysteps

import androidx.test.core.app.ApplicationProvider
import com.example.memorysteps.data.LearningDatabase
import com.example.memorysteps.ai.author.*
import com.example.memorysteps.game.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource

/** UI tests run on the development emulator; reset before Activity creation. */
class FreshLearningRecords : ExternalResource() {
    override fun before() = runBlocking(Dispatchers.IO) {
        LearningDatabase.get(ApplicationProvider.getApplicationContext()).clearAllTables()
        QuestionAuthors.testFactory = { object : QuestionAuthor {
            override suspend fun compose(request: AuthorRequest): AuthoredPlan {
                val used = request.recent.toMutableSet()
                return AuthoredPlan("INSUFFICIENT_DATA", request.slots.map { type ->
                    val ids = when (type) {
                        GameType.COLOR -> (0..15).map { "C%02d".format(it) }
                        GameType.PICTURE -> (0..15).map { "P%02d".format(it) }
                        GameType.NUMBER -> (10..99).map { "N$it" }
                    }
                    val target = ids.first { it !in used }.also { used.add(it) }
                    listOf(target) + ids.filter { it != target }.take(request.conditions.optionCount - 1)
                }).validate(request)
            }
        } }
    }
    override fun after() { QuestionAuthors.testFactory = null }
}
