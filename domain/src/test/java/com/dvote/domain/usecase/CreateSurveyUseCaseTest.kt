package com.dvote.domain.usecase

import com.dvote.core.model.CreateSurveyRequest
import com.dvote.domain.repository.VotingRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateSurveyUseCaseTest {
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC)
    private val repository = RecordingVotingRepository()
    private val useCase = CreateSurveyUseCase(repository, clock)

    @Test
    fun normalizesTextAndOptionsBeforeDelegating() = runTest {
        val result = useCase(
            validRequest().copy(
                title = "  Secure vote  ",
                description = "  A valid survey description  ",
                options = persistentListOf("  Alpha  ", "", "Alpha", "  Beta"),
            )
        )

        assertEquals("survey-id", result.getOrNull())
        assertEquals(1, repository.requests.size)
        assertEquals(
            validRequest().copy(
                options = persistentListOf("Alpha", "Beta"),
            ),
            repository.requests.single(),
        )
    }

    @Test
    fun rejectsEveryInvalidBoundaryBeforeCallingRepository() = runTest {
        val cases = listOf(
            validRequest().copy(title = "ab") to
                CreateSurveyValidationFailure.Reason.TITLE_LENGTH,
            validRequest().copy(description = "too short") to
                CreateSurveyValidationFailure.Reason.DESCRIPTION_LENGTH,
            validRequest().copy(options = persistentListOf("Only one")) to
                CreateSurveyValidationFailure.Reason.OPTION_COUNT,
            validRequest().copy(options = persistentListOf("Same", " Same ")) to
                CreateSurveyValidationFailure.Reason.OPTION_COUNT,
            validRequest().copy(
                options = persistentListOf("Valid", "A".repeat(81)),
            ) to CreateSurveyValidationFailure.Reason.OPTION_LENGTH,
            validRequest().copy(expiresAt = clock.millis()) to
                CreateSurveyValidationFailure.Reason.EXPIRATION,
        )

        cases.forEach { (request, expectedReason) ->
            val failure = useCase(request).exceptionOrNull()

            assertTrue(failure is CreateSurveyValidationFailure)
            assertEquals(
                expectedReason,
                (failure as CreateSurveyValidationFailure).reason,
            )
        }
        assertTrue(repository.requests.isEmpty())
    }

    @Test
    fun acceptsInclusiveValidationLimits() = runTest {
        val result = useCase(
            validRequest().copy(
                title = "abc",
                description = "1234567890",
                options = persistentListOf(
                    "A".repeat(80),
                    "B",
                ),
                expiresAt = clock.millis() + 1,
            )
        )

        assertTrue(result.isSuccess)
        assertEquals(1, repository.requests.size)
    }

    private fun validRequest() = CreateSurveyRequest(
        operationId = "01234567-89ab-4cde-8fab-0123456789ab",
        title = "Secure vote",
        description = "A valid survey description",
        allowMultipleChoices = false,
        options = persistentListOf("Alpha", "Beta"),
        expiresAt = clock.millis() + 60_000,
    )

    private class RecordingVotingRepository : VotingRepository {
        val requests = mutableListOf<CreateSurveyRequest>()

        override fun observeHasVoted(surveyId: String) = flowOf(false)

        override suspend fun createSurvey(request: CreateSurveyRequest): Result<String> {
            requests += request
            return Result.success("survey-id")
        }

        override suspend fun submitVote(submission: com.dvote.core.model.VoteSubmission) =
            Result.failure<com.dvote.core.model.VoteReceipt>(UnsupportedOperationException())
    }
}
