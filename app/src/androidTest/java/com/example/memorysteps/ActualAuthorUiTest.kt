package com.example.memorysteps

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.memorysteps.ai.author.QuestionAuthors
import com.example.memorysteps.data.LearningDatabase
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ActualAuthorUiTest {
    @get:Rule(order = 0) val clean = object : ExternalResource() {
        override fun before() = runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            require(context.packageName.endsWith(".difficultyvalidation"))
            QuestionAuthors.testFactory = null
            LearningDatabase.get(context).clearAllTables()
        }
    }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    @Test fun gameStartUsesThePackagedModelAndShowsItsActualProblem() {
        click(R.string.learn_menu); click(R.string.mixed_title)
        waitFor(R.string.author_preparing, 10_000)
        waitFor(R.string.next_button, 200_000)
        compose.onNodeWithText(compose.activity.getString(R.string.author_model)).assertIsDisplayed()
        val answer = compose.onNodeWithTag("memory-target").fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
        val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val folder = File(compose.activity.getExternalFilesDir(null), "verification").apply { mkdirs() }
        File(folder, "actual-ai-game.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
        click(R.string.next_button); waitFor(R.string.solve_prompt, 10_000)
        compose.onNodeWithContentDescription(answer).performScrollTo().performClick()
        waitFor(R.string.correct_result, 10_000)
        runBlocking {
            val db = LearningDatabase.get(compose.activity)
            val cycle = checkNotNull(db.learningDao().progress()?.activeCycleId)
            val plan = checkNotNull(db.authoredPlanDao().forCycle(cycle))
            assertEquals("MODEL", plan.source)
            assertEquals(1, db.learningDao().problemCount())
            assertTrue(db.learningDao().problems(cycle).single().firstCorrect)
            println("ACTUAL_UI_MODEL elapsedMs=${plan.elapsedMs} source=${plan.source}")
        }
    }
    private fun click(id: Int) {
        waitFor(id, 10_000)
        val node = compose.onNodeWithText(compose.activity.getString(id))
        if (generateSequence(node.fetchSemanticsNode().parent) { it.parent }.any { SemanticsActions.ScrollBy in it.config }) node.performScrollTo()
        node.performClick()
    }
    private fun waitFor(id: Int, timeout: Long) = compose.waitUntil(timeoutMillis = timeout) {
        compose.onAllNodesWithText(compose.activity.getString(id)).fetchSemanticsNodes().isNotEmpty()
    }
}
