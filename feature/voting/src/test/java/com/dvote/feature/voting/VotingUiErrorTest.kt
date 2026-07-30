package com.dvote.feature.voting

import com.dvote.domain.repository.RepositoryFailure
import com.dvote.domain.usecase.CreateSurveyValidationFailure
import org.junit.Assert.assertEquals
import org.junit.Test

class VotingUiErrorTest {

    @Test
    fun `maps every repository failure kind without exposing its message`() {
        assertEquals(
            VotingUiError.SERVICE_UNAVAILABLE,
            repositoryFailure(RepositoryFailure.Kind.RETRYABLE)
                .toVotingUiError(VotingUiError.LOAD_SURVEYS_FAILED),
        )
        assertEquals(
            VotingUiError.INVALID_SERVICE_DATA,
            repositoryFailure(RepositoryFailure.Kind.INVALID_DATA)
                .toVotingUiError(VotingUiError.LOAD_SURVEYS_FAILED),
        )
        assertEquals(
            VotingUiError.REQUEST_REJECTED,
            repositoryFailure(RepositoryFailure.Kind.REJECTED)
                .toVotingUiError(VotingUiError.LOAD_SURVEYS_FAILED),
        )
    }

    @Test
    fun `maps create validation to actionable field guidance`() {
        val error = CreateSurveyValidationFailure(
            CreateSurveyValidationFailure.Reason.DESCRIPTION_LENGTH
        )

        assertEquals(
            VotingUiError.CREATE_DESCRIPTION_LENGTH,
            error.toVotingUiError(VotingUiError.CREATE_SURVEY_FAILED),
        )
    }

    @Test
    fun `uses operation fallback for an unknown local failure`() {
        assertEquals(
            VotingUiError.SUBMIT_VOTE_FAILED,
            IllegalStateException("implementation details")
                .toVotingUiError(VotingUiError.SUBMIT_VOTE_FAILED),
        )
    }

    private fun repositoryFailure(
        kind: RepositoryFailure.Kind,
    ) = RepositoryFailure(
        kind = kind,
        message = "sensitive backend details",
    )
}
