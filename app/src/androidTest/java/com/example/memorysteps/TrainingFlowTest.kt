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

@RunWith(AndroidJUnit4::class)
class TrainingFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun allHomeStartButtonsOpenPlayableMemoryScreen() {
        listOf(R.string.mixed_title, R.string.color_title, R.string.picture_title, R.string.number_title).forEach { title ->
            click(title)
            waitFor(R.string.next_button)
            compose.onNodeWithTag("memory-target").performScrollTo().assertIsDisplayed()
            pressBack()
            click(R.string.end_training)
            compose.onNodeWithText(compose.activity.getString(R.string.end_confirm)).performClick()
        }
    }

    @Test fun mixedTrainingCompletesTenQuestionsAndShowsRealResult() {
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
        click(R.string.home_button)
        compose.onNodeWithText(compose.activity.getString(R.string.last_result, 10)).performScrollTo().assertIsDisplayed()
    }

    @Test fun wrongOptionDisablesAndRecreationKeepsCompletedProgress() {
        click(R.string.number_title)
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
        assertEquals(compose.activity.getString(R.string.question_progress, 2, compose.activity.getString(R.string.type_number)), progress)
        pressBack()
        click(R.string.continue_training)
        waitFor(R.string.interrupted_title)
        click(R.string.resume_question)
        waitFor(R.string.next_button)
    }

    private fun click(id: Int) = compose.onNodeWithText(compose.activity.getString(id)).performScrollTo().performClick()
    private fun waitFor(id: Int) {
        val text = compose.activity.getString(id)
        compose.waitUntil(timeoutMillis = 12_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
}
