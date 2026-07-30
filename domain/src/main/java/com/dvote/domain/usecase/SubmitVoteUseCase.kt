package com.dvote.domain.usecase

import com.dvote.core.model.Survey
import com.dvote.core.model.VoteReceipt
import com.dvote.core.model.VoteSubmission
import com.dvote.core.model.isClosedAt
import com.dvote.domain.coroutines.runSuspendCatching
import com.dvote.domain.repository.AuthRepository
import com.dvote.domain.repository.VotingRepository
import com.dvote.domain.security.VoteReceiptVerifier
import com.dvote.domain.security.VoteSigner
import java.time.Clock
import javax.inject.Inject
import kotlinx.collections.immutable.toImmutableList

class SubmitVoteUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val voteSigner: VoteSigner,
    private val votingRepository: VotingRepository,
    private val receiptVerifier: VoteReceiptVerifier,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        survey: Survey,
        selectedOptionIds: Set<String>,
        operationId: String,
    ): Result<VoteReceipt> {
        val submission = prepareSubmission(
            survey = survey,
            selectedOptionIds = selectedOptionIds,
            operationId = operationId,
        ).getOrElse { return Result.failure(it) }
        return submit(submission)
    }

    suspend fun prepareSubmission(
        survey: Survey,
        selectedOptionIds: Set<String>,
        operationId: String,
    ): Result<VoteSubmission> = runSuspendCatching {
        val userId = authRepository.currentUserId()
            ?: error("User is not authenticated.")

        val options = selectedOptionIds.toList().sorted()
        validate(survey, options).getOrThrow()

        val signedAt = clock.millis()
        val votingKey = voteSigner.votingKey(userId)
        val unsignedPayload = buildPayload(
            surveyId = survey.id,
            userId = userId,
            keyId = votingKey.keyId,
            optionIds = options,
            signedAt = signedAt,
        )
        val signature = voteSigner.signBase64(userId, unsignedPayload)
        val commitmentHash = voteSigner.sha256Base64(
            "$unsignedPayload|$signature|${votingKey.publicKey}"
        )

        VoteSubmission(
            operationId = operationId,
            surveyId = survey.id,
            keyId = votingKey.keyId,
            optionIds = options.toImmutableList(),
            signedAt = signedAt,
            canonicalPayload = unsignedPayload,
            publicKey = votingKey.publicKey,
            signature = signature,
            commitmentHash = commitmentHash,
        )
    }

    suspend fun submit(submission: VoteSubmission): Result<VoteReceipt> {
        return votingRepository.submitVote(submission).fold(
            onSuccess = { receipt ->
                if (receiptVerifier.verify(receipt)) {
                    Result.success(receipt)
                } else {
                    Result.failure(
                        IllegalStateException("Vote verification bundle is invalid.")
                    )
                }
            },
            onFailure = { Result.failure(it) },
        )
    }

    fun buildPayload(
        surveyId: String,
        userId: String,
        keyId: String,
        optionIds: List<String>,
        signedAt: Long,
    ): String {
        return listOf(
            "survey=$surveyId",
            "user=$userId",
            "key=$keyId",
            "options=${optionIds.sorted().joinToString(",")}",
            "signedAt=$signedAt",
        ).joinToString("|")
    }

    private fun validate(survey: Survey, selectedOptionIds: List<String>): Result<Unit> {
        val allowedOptionIds = survey.options.map { it.id }.toSet()
        return when {
            survey.isClosedAt(clock.millis()) ->
                Result.failure(IllegalStateException("Survey is closed."))
            selectedOptionIds.isEmpty() ->
                Result.failure(IllegalArgumentException("Select at least one option."))
            !survey.allowMultipleChoices && selectedOptionIds.size > 1 ->
                Result.failure(IllegalArgumentException("Select only one option."))
            selectedOptionIds.any { it !in allowedOptionIds } ->
                Result.failure(IllegalArgumentException("Selected option does not belong to this survey."))
            else -> Result.success(Unit)
        }
    }
}
