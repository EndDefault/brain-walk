package com.example.memorysteps

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.example.memorysteps.data.LearningDatabase
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class TrainingFlowTest {
    @get:Rule(order = 0) val records = FreshLearningRecords()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun allHomeStartButtonsOpenPlayableMemoryScreen() {
        click(R.string.learn_menu)
        listOf(R.string.mixed_title, R.string.type_color, R.string.type_picture, R.string.type_number).forEach { title ->
            click(title)
            waitFor(R.string.next_button)
            compose.onNodeWithTag("memory-target").performScrollTo().assertIsDisplayed()
            pressBack()
            if (title == R.string.mixed_title) {
                click(R.string.end_training)
                compose.onNodeWithText(compose.activity.getString(R.string.end_confirm)).performClick()
            }
        }
    }

    @Test fun mixedTrainingCompletesTenQuestionsAndShowsRealResult() {
        click(R.string.learn_menu)
        click(R.string.mixed_title)
        repeat(10) { index ->
            waitFor(R.string.next_button)
            val answer = compose.onNodeWithTag("memory-target").fetchSemanticsNode()
                .config[SemanticsProperties.ContentDescription].single()
            click(R.string.next_button)
            waitFor(R.string.solve_prompt)
            compose.onNodeWithContentDescription(answer).performScrollTo().performClick()
            waitFor(R.string.correct_result)
            click(if (index == 9) R.string.show_results else R.string.next_question)
        }
        waitFor(R.string.summary_title)
        compose.onNodeWithText(compose.activity.getString(R.string.summary_first, 10)).assertIsDisplayed()
        click(R.string.learn_return)
        click(R.string.home_button)
        click(R.string.home_title)
        compose.onNodeWithText(compose.activity.getString(R.string.record_first, 10, 10)).performScrollTo().assertIsDisplayed()
    }

    @Test fun wrongOptionDisablesAndRecreationKeepsCompletedProgress() {
        click(R.string.learn_menu)
        click(R.string.mixed_title)
        waitFor(R.string.next_button)
        val answer = compose.onNodeWithTag("memory-target").fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription].single()
        click(R.string.next_button)
        waitFor(R.string.solve_prompt)
        val wrong = (1..4).map { "option-option-$it" }.first { tag ->
            !hasContentDescription(answer).matches(compose.onNodeWithTag(tag).fetchSemanticsNode())
        }
        compose.onNodeWithTag(wrong).performScrollTo().performClick()
        compose.onNodeWithTag(wrong).assertIsNotEnabled()
        compose.onNodeWithContentDescription(answer).performScrollTo().performClick()
        waitFor(R.string.correct_result)
        click(R.string.next_question)
        waitFor(R.string.next_button)
        compose.activityRule.scenario.recreate()
        waitFor(R.string.interrupted_title)
        click(R.string.resume_question)
        waitFor(R.string.next_button)
        val progress = compose.onNodeWithTag("question-progress").fetchSemanticsNode()
            .config[SemanticsProperties.Text].single().text
        org.junit.Assert.assertTrue(progress.startsWith("2 / 10"))
        pressBack()
        click(R.string.continue_training)
        waitFor(R.string.interrupted_title)
        click(R.string.resume_question)
        waitFor(R.string.next_button)
    }

    @Test fun practiceDoesNotCreateLearningRecords() {
        click(R.string.learn_menu)
        click(R.string.type_number)
        waitFor(R.string.next_button)
        val answer = compose.onNodeWithTag("memory-target").fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription].single()
        click(R.string.next_button)
        waitFor(R.string.solve_prompt)
        compose.onNodeWithContentDescription(answer).performScrollTo().performClick()
        waitFor(R.string.correct_result)
        assertEquals(0, runBlocking { LearningDatabase.get(compose.activity).learningDao().problemCount() })
        assertEquals(0, runBlocking { LearningDatabase.get(compose.activity).learningDao().cycleCount() })
    }

    private fun click(id: Int) = compose.onNodeWithText(compose.activity.getString(id)).performScrollTo().performClick()
    private fun waitFor(id: Int) {
        val text = compose.activity.getString(id)
        compose.waitUntil(timeoutMillis = 12_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
}
