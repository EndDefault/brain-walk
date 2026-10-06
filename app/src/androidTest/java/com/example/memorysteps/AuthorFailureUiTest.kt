package com.example.memorysteps

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.memorysteps.ai.author.*
import com.example.memorysteps.data.LearningDatabase
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthorFailureUiTest {
    private var slow = false
    @get:Rule(order = 0) val clean = object : ExternalResource() {
        override fun before() = runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            require(context.packageName.endsWith(".difficultyvalidation"))
            LearningDatabase.get(context).clearAllTables()
            QuestionAuthors.testFactory = { object : QuestionAuthor {
                override suspend fun compose(request: AuthorRequest): AuthoredPlan {
                    if (slow) awaitCancellation()
                    error("TEST_MODEL_FAILURE")
                }
            } }
        }
        override fun after() { QuestionAuthors.testFactory = null }
    }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    @Test fun failedModelCanStartClearlyIdentifiedFallbackWithoutLosingRecords() {
        click(R.string.learn_menu); click(R.string.mixed_title)
        waitFor(R.string.author_failed)
        compose.onNodeWithText(compose.activity.getString(R.string.author_fallback)).performClick()
        waitFor(R.string.next_button)
        compose.onNodeWithText(compose.activity.getString(R.string.author_rule)).assertIsDisplayed()
        runBlocking {
            val db = LearningDatabase.get(compose.activity)
            val cycle = checkNotNull(db.learningDao().progress()?.activeCycleId)
            assertEquals("RULE_FALLBACK", db.authoredPlanDao().forCycle(cycle)?.source)
            assertEquals("MODEL_FAILED", db.authoredPlanDao().forCycle(cycle)?.fallbackReason)
        }
    }
    @Test fun cancellingPreparationDoesNotCreateAFormalGame() {
        slow = true
        click(R.string.learn_menu); click(R.string.mixed_title)
        waitFor(R.string.author_preparing)
        compose.onNodeWithText(compose.activity.getString(R.string.cancel)).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.practice_heading)).performScrollTo().assertIsDisplayed()
        assertEquals(0, runBlocking { LearningDatabase.get(compose.activity).learningDao().cycleCount() })
    }
    private fun click(id: Int) { waitFor(id); compose.onNodeWithText(compose.activity.getString(id)).performScrollTo().performClick() }
    private fun waitFor(id: Int) = compose.waitUntil(timeoutMillis = 15_000) {
        compose.onAllNodesWithText(compose.activity.getString(id)).fetchSemanticsNodes().isNotEmpty()
    }
}
