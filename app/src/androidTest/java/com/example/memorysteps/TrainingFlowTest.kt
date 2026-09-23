package com.example.memorysteps

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
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
import org.junit.Assert.assertTrue
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
            click(R.string.pause_back)
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
        compose.onNodeWithText("다른 보기를 골라보세요.").assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.attempts_left, 2)).performScrollTo().assertIsDisplayed()
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
        click(R.string.pause_back)
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

    @Test fun pauseDuringCountdownHidesGameAndRestartsOnlyCurrentQuestion() {
        click(R.string.learn_menu)
        click(R.string.mixed_title)
        waitFor(R.string.next_button)
        click(R.string.next_button)
        waitFor(R.string.wait_prompt)
        click(R.string.pause_button)
        waitFor(R.string.pause_title)
        compose.onNodeWithTag("memory-target").assertDoesNotExist()
        compose.onNodeWithTag("wait-countdown").assertDoesNotExist()
        Thread.sleep(3_500) // Longer than the wait: the paused screen must not show choices.
        compose.onNodeWithTag("option-option-1").assertDoesNotExist()
        compose.activityRule.scenario.recreate()
        waitFor(R.string.pause_title)
        click(R.string.resume_question)
        waitFor(R.string.memory_prompt)
        val progress = compose.onNodeWithTag("question-progress").fetchSemanticsNode()
            .config[SemanticsProperties.Text].single().text
        assertTrue(progress.startsWith("1 / 10"))
        assertEquals(2, runBlocking { LearningDatabase.get(compose.activity).learningDao().problemCount() })
        click(R.string.pause_button)
        click(R.string.home_button)
        compose.onNodeWithText(compose.activity.getString(R.string.brand_name)).assertIsDisplayed()
        click(R.string.learn_menu)
        compose.onNodeWithText(compose.activity.getString(R.string.saved_progress, 0)).performScrollTo().assertIsDisplayed()
    }

    @Test fun nextActionStaysBelowScrollingTargetAndCorrectFeedbackIsCentered() {
        click(R.string.learn_menu)
        click(R.string.type_picture)
        waitFor(R.string.next_button)
        val buttonBefore = compose.onNodeWithTag("stage-action").fetchSemanticsNode().boundsInRoot
        val target = compose.onNodeWithTag("memory-target").performScrollTo().fetchSemanticsNode()
        val answer = target.config[SemanticsProperties.ContentDescription].single()
        val buttonAfter = compose.onNodeWithTag("stage-action").fetchSemanticsNode().boundsInRoot
        assertEquals(buttonBefore, buttonAfter)
        assertTrue(target.boundsInRoot.bottom <= buttonAfter.top)
        compose.onNodeWithText("학습 선택으로").assertDoesNotExist()
        click(R.string.next_button)
        waitFor(R.string.wait_prompt)
        compose.onNodeWithTag("wait-countdown").assertIsDisplayed()
        waitFor(R.string.solve_prompt)
        compose.onNodeWithContentDescription(answer).performScrollTo().performClick()
        waitFor(R.string.correct_result)
        val stage = compose.onNodeWithTag("training-stage").fetchSemanticsNode().boundsInRoot
        val feedback = compose.onNodeWithTag("correct-feedback").fetchSemanticsNode().boundsInRoot
        assertEquals(stage.center.x, feedback.center.x, 2f)
        assertEquals(stage.center.y, feedback.center.y, 2f)
        compose.onNodeWithText(compose.activity.getString(R.string.answer_label)).assertDoesNotExist()
        click(R.string.pause_button)
        click(R.string.pause_continue)
        waitFor(R.string.correct_result)
        assertEquals(0, runBlocking { LearningDatabase.get(compose.activity).learningDao().problemCount() })
    }

    private fun click(id: Int) {
        val node = compose.onNodeWithText(compose.activity.getString(id))
        if (generateSequence(node.fetchSemanticsNode().parent) { it.parent }.any { SemanticsActions.ScrollBy in it.config }) {
            node.performScrollTo()
        }
        node.performClick()
    }
    private fun waitFor(id: Int) {
        val text = compose.activity.getString(id)
        compose.waitUntil(timeoutMillis = 12_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
}
