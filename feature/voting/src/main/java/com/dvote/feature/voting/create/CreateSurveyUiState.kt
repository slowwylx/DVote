package com.dvote.feature.voting.create

import com.dvote.feature.voting.VotingUiError
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

internal const val MIN_SURVEY_OPTION_COUNT = 2
internal const val MAX_SURVEY_OPTION_COUNT = 10

data class CreateSurveyUiState(
    val title: String = "",
    val description: String = "",
    val options: ImmutableList<String> = persistentListOf("", ""),
    val allowMultipleChoices: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: VotingUiError? = null,
)

sealed interface CreateSurveyEffect {
    data class OpenSurvey(val surveyId: String) : CreateSurveyEffect
}
