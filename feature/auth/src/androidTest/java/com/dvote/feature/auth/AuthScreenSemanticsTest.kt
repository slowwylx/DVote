package com.dvote.feature.auth

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class AuthScreenSemanticsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loadingSignIn_exposesOneDisabledActionWithProgressState() {
        composeRule.setContent {
            MaterialTheme {
                AuthScreen(
                    uiState = AuthUiState(isLoading = true),
                    isRepairRequired = false,
                    onGoogleSignIn = {},
                    onRepairProfile = {},
                    onSignOut = {},
                )
            }
        }

        composeRule
            .onNodeWithContentDescription(string(R.string.auth_google_sign_in))
            .assertIsDisplayed()
            .assertIsNotEnabled()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    string(R.string.auth_signing_in),
                )
            )
    }

    @Test
    fun persistentError_isAnnouncedPolitely() {
        composeRule.setContent {
            MaterialTheme {
                AuthScreen(
                    uiState = AuthUiState(error = AuthUiError.SIGN_IN_FAILED),
                    isRepairRequired = false,
                    onGoogleSignIn = {},
                    onRepairProfile = {},
                    onSignOut = {},
                )
            }
        }

        composeRule
            .onNodeWithText(string(R.string.auth_error_sign_in))
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite,
                )
            )
    }

    private fun string(resource: Int): String {
        return InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getString(resource)
    }
}
