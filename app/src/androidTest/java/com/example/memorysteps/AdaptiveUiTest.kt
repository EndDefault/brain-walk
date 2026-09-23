package com.example.memorysteps

import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.memorysteps.data.*
import com.example.memorysteps.game.*
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdaptiveUiTest {
    @get:Rule(order = 0) val records = FreshLearningRecords()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    private val db get() = LearningDatabase.get(compose.activity)

    @Test fun recordsReflectLearnedConditionsAfterRecreationAndModeSwitchRequiresFinishedGame() {
        click(R.string.learn_menu)
        compose.waitUntil(5_000) { compose.onNodeWithText(text(R.string.mixed_title)).isEnabled() }
        runBlocking(Dispatchers.IO) {
            val repo = LearningRepository(db)
            var now = 100L
            val game = repo.startFormal(TrainingMode.MIXED, MonotonicClock { now }, "ui-seed", now)
            suspend fun save() = repo.save(game.state, "ui-seed", now)
            repeat(10) { index ->
                val p = game.state.problem
                game.memoryShown(p.id); save()
                now += 6_000; game.next(p.id); save()
                now += 3_000; game.tick(); save()
                game.optionsShown(p.id); save()
                now += 500; game.answer(p.id, p.options.single { it.item == p.target }.id); save()
                if (index < 9) { game.advance(p.id); save() }
            }
        }
        click(R.string.home_button)
        click(R.string.home_title)
        val learned = compose.activity.getString(R.string.adaptive_conditions, "5", "3", 4, text(R.string.adaptive_unlimited))
        compose.onNodeWithText(learned).performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText(learned).assertCountEquals(1)
        compose.onNodeWithText(text(R.string.adaptive_heading)).performScrollTo().assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText(learned).performScrollTo().assertIsDisplayed()
        screenshot("ai-records")
        click(R.string.ai_diagnostics)
        click(R.string.ai_mode_comparison)
        compose.waitUntil(5_000) { compose.onAllNodesWithText("현재 모드: COMPARISON").fetchSemanticsNodes().isNotEmpty() }
        click(R.string.ai_back)
        click(R.string.home_button)
        click(R.string.learn_menu)
        click(R.string.mixed_title)
        waitFor(R.string.memory_prompt)
        click(R.string.pause_button)
        click(R.string.home_button)
        click(R.string.home_title)
        click(R.string.ai_diagnostics)
        compose.onNodeWithText(text(R.string.ai_mode_bandit)).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.ai_mode_comparison)).assertIsNotEnabled()
    }

    @Test fun sixteenOptionsAndTimedSolveRemainReachableWithLargeText() {
        click(R.string.learn_menu)
        compose.waitUntil(5_000) { compose.onNodeWithText(text(R.string.mixed_title)).isEnabled() }
        runBlocking(Dispatchers.IO) {
            db.adaptiveDao().difficulties().forEach { old ->
                db.adaptiveDao().setDifficulty(old.copy(memoryMs = 5_000, optionCount = 16, solveMs = 15_000, conditionVersion = "ui-grid-fixture"))
            }
        }
        click(R.string.mixed_title)
        waitFor(R.string.next_button)
        click(R.string.next_button)
        waitFor(R.string.solve_prompt)
        val stage = compose.onNodeWithTag("training-stage").fetchSemanticsNode().boundsInRoot
        val largeTablet = compose.activity.resources.configuration.screenWidthDp >= 1000 &&
            compose.activity.resources.configuration.fontScale <= 1.3f
        if (largeTablet) {
            (1..16).forEach { index ->
                val node = compose.onNodeWithTag("option-option-$index").assertIsDisplayed().fetchSemanticsNode()
                assertTrue("Option $index must fit the tablet viewport", node.boundsInRoot.bottom <= stage.bottom)
                assertTrue(node.boundsInRoot.top >= stage.top)
            }
            compose.onNodeWithTag("stage-countdown").assertIsDisplayed()
        } else {
            // The grid and the stage scroll on different axes; exercise both containers.
            scrollToEnd("stage-scroll", SemanticsProperties.VerticalScrollAxisRange)
            scrollToEnd("options-scroll", SemanticsProperties.HorizontalScrollAxisRange)
            compose.onNodeWithTag("option-option-16").performScrollTo().assertIsDisplayed()
            screenshot("ai-16-options-last")
            compose.onNodeWithTag("option-option-1").performScrollTo().assertIsDisplayed()
        }
        screenshot("ai-16-options")
        click(R.string.pause_button)
        compose.onNodeWithTag("option-option-16").assertDoesNotExist()
    }

    private fun text(id: Int) = compose.activity.getString(id)
    private fun click(id: Int) {
        val node = compose.onNodeWithText(text(id))
        if (generateSequence(node.fetchSemanticsNode().parent) { it.parent }.any { SemanticsActions.ScrollBy in it.config }) node.performScrollTo()
        node.performClick()
    }
    private fun waitFor(id: Int) = compose.waitUntil(12_000) { compose.onAllNodesWithText(text(id)).fetchSemanticsNodes().isNotEmpty() }
    private fun SemanticsNodeInteraction.isEnabled(): Boolean = try { assertIsEnabled(); true } catch (_: AssertionError) { false }
    private fun scrollToEnd(tag: String, axis: androidx.compose.ui.semantics.SemanticsPropertyKey<androidx.compose.ui.semantics.ScrollAxisRange>) {
        val node = compose.onNodeWithTag(tag)
        val range = node.fetchSemanticsNode().config[axis]
        node.performSemanticsAction(SemanticsActions.ScrollBy) {
            if (axis == SemanticsProperties.VerticalScrollAxisRange) it(0f, range.maxValue()) else it(range.maxValue(), 0f)
        }
    }
    private fun screenshot(name: String) {
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(compose.activity.getExternalFilesDir("verification"), "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
