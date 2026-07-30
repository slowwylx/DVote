package com.dvote.feature.voting

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.platform.app.InstrumentationRegistry
import com.dvote.core.designsystem.DVoteTheme
import com.dvote.core.model.Survey
import com.dvote.core.model.SurveyOption
import com.dvote.core.model.UserProfile
import com.dvote.core.model.VoteReceipt
import com.dvote.feature.voting.create.CreateSurveyScreen
import com.dvote.feature.voting.create.CreateSurveyUiState
import com.dvote.feature.voting.home.HomeScreen
import com.dvote.feature.voting.home.HomeUiState
import com.dvote.feature.voting.profile.ProfileScreen
import com.dvote.feature.voting.profile.ProfileUiState
import com.dvote.feature.voting.vote.VoteSurveyScreen
import com.dvote.feature.voting.vote.VoteSurveyUiState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import com.dvote.core.designsystem.R as DesignSystemR

class VotingScreensSemanticsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun home_exposesLoadingErrorAndEmptyStates() {
        var uiState by mutableStateOf<HomeUiState>(HomeUiState.Loading)
        composeRule.setContent {
            DVoteTheme {
                HomeScreen(
                    uiState = uiState,
                    formatClosingDate = { "" },
                    onSurveyClick = {},
                )
            }
        }
        composeRule
            .onNodeWithContentDescription(string(DesignSystemR.string.designsystem_loading))
            .assertIsDisplayed()

        composeRule.runOnIdle {
            uiState = HomeUiState.Error(VotingUiError.LOAD_SURVEYS_FAILED)
        }
        composeRule
            .onNodeWithText(string(R.string.error_load_surveys))
            .assertIsDisplayed()
            .assert(hasPoliteLiveRegion())

        composeRule.runOnIdle {
            uiState = HomeUiState.Content(persistentListOf())
        }
        composeRule
            .onNodeWithText(string(R.string.home_empty_title))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(string(R.string.home_empty_body))
            .assertIsDisplayed()
    }

    @Test
    fun homeContent_exposesOneClickableSurveyWithStableIdCallback() {
        var selectedSurveyId: String? = null
        composeRule.setContent {
            DVoteTheme {
                HomeScreen(
                    uiState = HomeUiState.Content(persistentListOf(survey())),
                    formatClosingDate = { "31 Dec 2026" },
                    onSurveyClick = { selectedSurveyId = it },
                )
            }
        }

        composeRule
            .onNode(
                hasClickAction() and hasAnyDescendant(hasText(SURVEY_TITLE)),
                useUnmergedTree = true,
            )
            .assertIsDisplayed()
            .performClick()

        assertEquals(SURVEY_ID, selectedSurveyId)
    }

    @Test
    fun submittingCreateSurvey_disablesEditableAndSubmissionActions() {
        composeRule.setContent {
            DVoteTheme {
                CreateSurveyScreen(
                    uiState = CreateSurveyUiState(isSubmitting = true),
                    onTitleChanged = {},
                    onDescriptionChanged = {},
                    onAllowMultipleChoicesChanged = {},
                    onOptionChanged = { _, _ -> },
                    onRemoveOption = {},
                    onAddOption = {},
                    onSubmit = {},
                )
            }
        }

        composeRule
            .onNodeWithText(string(R.string.create_survey_submitting))
            .assertIsNotEnabled()
        composeRule
            .onNode(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.Role,
                    Role.Switch,
                )
            )
            .assertIsNotEnabled()
    }

    @Test
    fun optionLimit_disablesAddActionAndAnnouncesWhy() {
        composeRule.setContent {
            DVoteTheme {
                CreateSurveyScreen(
                    uiState = CreateSurveyUiState(
                        options = persistentListOf(
                            "1", "2", "3", "4", "5",
                            "6", "7", "8", "9", "10",
                        ),
                    ),
                    onTitleChanged = {},
                    onDescriptionChanged = {},
                    onAllowMultipleChoicesChanged = {},
                    onOptionChanged = { _, _ -> },
                    onRemoveOption = {},
                    onAddOption = {},
                    onSubmit = {},
                )
            }
        }

        composeRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText(string(R.string.create_survey_add_option)))
        composeRule
            .onNodeWithText(string(R.string.create_survey_add_option))
            .assertIsNotEnabled()
        composeRule
            .onNodeWithText(string(R.string.create_survey_option_limit_reached))
            .assert(hasPoliteLiveRegion())
    }

    @Test
    fun voteSelection_exposesOneCardLevelRadioAction() {
        var selectedOptionId: String? = null
        composeRule.setContent {
            DVoteTheme {
                VoteSurveyScreen(
                    uiState = readyVoteState(),
                    onOptionClick = { selectedOptionId = it },
                    onSubmit = {},
                    onExportReceipt = {},
                    onDismissReceipt = {},
                )
            }
        }

        composeRule
            .onNode(
                hasAnyDescendant(hasText(OPTION_TITLE)) and
                    SemanticsMatcher.expectValue(
                        SemanticsProperties.Role,
                        Role.RadioButton,
                    ),
                useUnmergedTree = true,
            )
            .assertIsNotSelected()
            .performClick()

        assertEquals(OPTION_ID, selectedOptionId)
    }

    @Test
    fun closedVote_disablesSelectionAndSubmissionAndExplainsState() {
        composeRule.setContent {
            DVoteTheme {
                VoteSurveyScreen(
                    uiState = readyVoteState().copy(isSurveyClosed = true),
                    onOptionClick = {},
                    onSubmit = {},
                    onExportReceipt = {},
                    onDismissReceipt = {},
                )
            }
        }

        composeRule
            .onNode(
                hasAnyDescendant(hasText(OPTION_TITLE)) and
                    SemanticsMatcher.expectValue(
                        SemanticsProperties.Role,
                        Role.RadioButton,
                    ),
                useUnmergedTree = true,
            )
            .assertIsNotEnabled()
        composeRule
            .onNodeWithText(string(R.string.vote_survey_closed))
            .assertIsNotEnabled()
        composeRule
            .onNodeWithText(string(R.string.vote_closed_explanation))
            .assert(hasPoliteLiveRegion())
    }

    @Test
    fun acceptedVote_exposesReceiptActions() {
        var exported = false
        var dismissed = false
        composeRule.setContent {
            DVoteTheme {
                VoteSurveyScreen(
                    uiState = readyVoteState().copy(receipt = receipt()),
                    onOptionClick = {},
                    onSubmit = {},
                    onExportReceipt = { exported = true },
                    onDismissReceipt = { dismissed = true },
                )
            }
        }

        composeRule
            .onNodeWithText(string(R.string.vote_receipt_title))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(string(R.string.vote_export_bundle))
            .performClick()
        assertTrue(exported)

        composeRule
            .onNodeWithText(string(R.string.vote_receipt_dismiss))
            .performClick()
        assertTrue(dismissed)
    }

    @Test
    fun profileContent_exposesCountsAndSignOutAction() {
        var signedOut = false
        composeRule.setContent {
            DVoteTheme {
                ProfileScreen(
                    uiState = ProfileUiState(
                        profile = UserProfile(
                            id = "user-1",
                            displayName = "Alex",
                            createdSurveyIds = persistentListOf("created"),
                            votedSurveyIds = persistentListOf("one", "two"),
                        ),
                        isLoading = false,
                    ),
                    onSignOut = { signedOut = true },
                )
            }
        }

        composeRule.onNodeWithText("Alex").assertIsDisplayed()
        composeRule
            .onNodeWithText(string(R.string.profile_sign_out))
            .performClick()

        assertTrue(signedOut)
    }

    private fun hasPoliteLiveRegion(): SemanticsMatcher {
        return SemanticsMatcher.expectValue(
            SemanticsProperties.LiveRegion,
            LiveRegionMode.Polite,
        )
    }

    private fun string(resource: Int): String {
        return InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getString(resource)
    }

    private fun survey(): Survey {
        return Survey(
            id = SURVEY_ID,
            title = SURVEY_TITLE,
            description = "A deterministic semantics test survey.",
            creatorId = "creator-1",
            creatorName = "Creator",
            createdAt = 1_000L,
            expiresAt = 2_000L,
            isActive = true,
            allowMultipleChoices = false,
            options = persistentListOf(
                SurveyOption(id = OPTION_ID, title = OPTION_TITLE),
                SurveyOption(id = "option-2", title = "Option B"),
            ),
        )
    }

    private fun readyVoteState(): VoteSurveyUiState {
        return VoteSurveyUiState(
            survey = survey(),
            selectedOptionIds = persistentSetOf(),
            isLoading = false,
            isVoteStatusLoading = false,
        )
    }

    private fun receipt(): VoteReceipt {
        return VoteReceipt(
            surveyId = SURVEY_ID,
            receiptId = "receipt-1",
            keyId = "key-1",
            commitmentHash = "commitment",
            acceptedAt = 3_000L,
            canonicalPayload = "payload",
            publicKey = "public-key",
            signature = "signature",
        )
    }

    private companion object {
        const val SURVEY_ID = "survey-1"
        const val SURVEY_TITLE = "Portfolio survey"
        const val OPTION_ID = "option-1"
        const val OPTION_TITLE = "Option A"
    }
}
