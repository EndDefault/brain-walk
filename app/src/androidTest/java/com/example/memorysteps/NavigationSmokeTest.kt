package com.example.memorysteps

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationSmokeTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun guideOpensAndReturnButtonRestoresHome() {
        compose.onNodeWithText(text(R.string.empty_record)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.guide_button)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.guide_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.solve_description)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.home_button)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.home_title)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun guideSurvivesActivityRecreationAndSystemBackReturnsHome() {
        compose.onNodeWithText(text(R.string.guide_button)).performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText(text(R.string.guide_title)).assertIsDisplayed()
        pressBack()
        compose.onNodeWithText(text(R.string.guide_button)).performScrollTo().assertIsDisplayed()
    }

    private fun text(id: Int): String = compose.activity.getString(id)
}
