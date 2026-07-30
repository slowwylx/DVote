package com.dvote.domain.usecase

import com.dvote.core.model.CreateSurveyRequest
import com.dvote.domain.repository.VotingRepository
import java.time.Clock
import javax.inject.Inject
import kotlinx.collections.immutable.toImmutableList

class CreateSurveyValidationFailure(
    val reason: Reason,
) : IllegalArgumentException("Invalid create-survey request: $reason") {
    enum class Reason {
        TITLE_LENGTH,
        DESCRIPTION_LENGTH,
        OPTION_COUNT,
        OPTION_LENGTH,
        EXPIRATION,
    }
}

class CreateSurveyUseCase @Inject constructor(
    private val votingRepository: VotingRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(request: CreateSurveyRequest): Result<String> {
        val normalizedOptions = request.options
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        val normalizedRequest = request.copy(
            title = request.title.trim(),
            description = request.description.trim(),
            options = normalizedOptions.toImmutableList(),
        )

        return validate(normalizedRequest)
            .fold(
                onSuccess = { votingRepository.createSurvey(normalizedRequest) },
                onFailure = { Result.failure(it) },
            )
    }

    private fun validate(request: CreateSurveyRequest): Result<Unit> {
        return when {
            request.title.length !in TITLE_RANGE ->
                invalid(CreateSurveyValidationFailure.Reason.TITLE_LENGTH)
            request.description.length !in DESCRIPTION_RANGE ->
                invalid(CreateSurveyValidationFailure.Reason.DESCRIPTION_LENGTH)
            request.options.size !in OPTION_COUNT_RANGE ->
                invalid(CreateSurveyValidationFailure.Reason.OPTION_COUNT)
            request.options.any { it.length !in OPTION_RANGE } ->
                invalid(CreateSurveyValidationFailure.Reason.OPTION_LENGTH)
            request.expiresAt <= clock.millis() ->
                invalid(CreateSurveyValidationFailure.Reason.EXPIRATION)
            else -> Result.success(Unit)
        }
    }

    private fun invalid(
        reason: CreateSurveyValidationFailure.Reason,
    ): Result<Unit> = Result.failure(CreateSurveyValidationFailure(reason))

    private companion object {
        val TITLE_RANGE = 3..80
        val DESCRIPTION_RANGE = 10..500
        val OPTION_COUNT_RANGE = 2..10
        val OPTION_RANGE = 1..80
    }
}
