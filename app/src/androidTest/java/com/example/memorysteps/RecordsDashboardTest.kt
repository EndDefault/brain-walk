package com.example.memorysteps

import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.memorysteps.data.LearningDatabase
import com.example.memorysteps.data.LearningRepository
import com.example.memorysteps.game.MonotonicClock
import com.example.memorysteps.game.TrainingMode
import java.io.File
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic records in the isolated test package only; never seed the user's database. */
@RunWith(AndroidJUnit4::class)
class RecordsDashboardTest {
    @get:Rule(order = 0) val records = FreshLearningRecords()
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun chartsReflectSavedGamesAndKeepAllMenuDestinationsReachable() {
        screenshot("submission-home")
        click(R.string.learn_menu)
        compose.waitUntil(5_000) {
            try { compose.onNodeWithText(text(R.string.mixed_title)).assertIsEnabled(); true }
            catch (_: AssertionError) { false }
        }
        screenshot("submission-learn")
        runBlocking(Dispatchers.IO) {
            val repo = LearningRepository(LearningDatabase.get(compose.activity))
            var now = Instant.parse("2026-10-01T00:00:00Z").toEpochMilli()
            listOf(3, 8, 6, 10, 7).forEach { score ->
                val game = repo.startFormal(TrainingMode.MIXED, MonotonicClock { now }, "chart-fixture", now)
                suspend fun save() = repo.save(game.state, "chart-fixture", now)
                repeat(10) { index ->
                    val p = game.state.problem
                    game.memoryShown(p.id); save()
                    now += 1_000; game.next(p.id); save()
                    now += p.conditions.waitMs; game.tick(); save()
                    game.optionsShown(p.id); save()
                    now += 500
                    if (index >= score) {
                        game.answer(p.id, p.options.first { it.item != p.target }.id); save()
                        now += 300
                    }
                    game.answer(p.id, p.options.single { it.item == p.target }.id); save()
                    if (index < 9) { game.advance(p.id); save() }
                }
                now += 86_400_000
            }
        }
        click(R.string.home_button)
        click(R.string.home_title)
        compose.onNodeWithTag("metric-first").performScrollTo().assert(hasAnyDescendant(hasText("68%")))
            .assert(hasAnyDescendant(hasText(compose.activity.getString(R.string.metric_fraction, 34, 50))))
        screenshot("submission-records")
        compose.onNodeWithTag("records-trend").performScrollTo().assertIsDisplayed()
        val descriptions = compose.onNodeWithTag("records-trend").fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription].joinToString()
        assertTrue(descriptions.contains("30%") && descriptions.contains("100%") && descriptions.contains("70%"))
        screenshot("submission-trend")
        compose.onNodeWithTag("rate-COLOR").performScrollTo().assertIsDisplayed()
        screenshot("submission-types")
        compose.onNodeWithText("개발용 AI 확인").assertDoesNotExist()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("metric-first").performScrollTo().assert(hasAnyDescendant(hasText("68%")))
        click(R.string.home_button)
        click(R.string.guide_button)
        compose.onNodeWithText("글꼴 저작권·라이선스").assertDoesNotExist()
        screenshot("submission-guide")
        compose.onNodeWithText(text(R.string.solve_description)).performScrollTo().assertIsDisplayed()
        click(R.string.home_button)
        compose.onNodeWithText(text(R.string.learn_menu)).performScrollTo().assertIsDisplayed()
    }

    private fun text(id: Int) = compose.activity.getString(id)
    private fun click(id: Int) = compose.onNodeWithText(text(id)).performScrollTo().performClick()
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(compose.activity.getExternalFilesDir("verification"), "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
