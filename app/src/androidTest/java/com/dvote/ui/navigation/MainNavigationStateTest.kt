package com.dvote.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class MainNavigationStateTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun typedBackStack_restoresStableDestinationAndSupportsBack() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            MaterialTheme {
                MainBackStackHarness()
            }
        }

        composeRule.onNodeWithText(HOME_STATE).assertIsDisplayed()
        composeRule.onNodeWithText(OPEN_SURVEY).performClick()
        composeRule.onNodeWithText(SURVEY_STATE).assertIsDisplayed()

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText(SURVEY_STATE).assertIsDisplayed()
        composeRule.onNodeWithText(BACK).performClick()
        composeRule.onNodeWithText(HOME_STATE).assertIsDisplayed()
    }

    @Composable
    private fun MainBackStackHarness() {
        val backStack = rememberMainNavBackStack(MainNavKey.Home)
        val current = backStack.last()

        Column {
            Text(
                when (current) {
                    MainNavKey.Home -> HOME_STATE
                    MainNavKey.CreateSurvey -> "Create"
                    MainNavKey.Profile -> "Profile"
                    is MainNavKey.Survey -> "Survey ${current.surveyId}"
                }
            )
            Button(
                onClick = {
                    backStack.add(MainNavKey.Survey(SURVEY_ID))
                }
            ) {
                Text(OPEN_SURVEY)
            }
            Button(onClick = { backStack.removeLastOrNull() }) {
                Text(BACK)
            }
        }
    }

    private companion object {
        const val SURVEY_ID = "survey-42"
        const val HOME_STATE = "Home"
        const val SURVEY_STATE = "Survey $SURVEY_ID"
        const val OPEN_SURVEY = "Open survey"
        const val BACK = "Back"
    }
}
