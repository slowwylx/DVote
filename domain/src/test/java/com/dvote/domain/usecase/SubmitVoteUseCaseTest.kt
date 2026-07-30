package com.dvote.domain.usecase

import com.dvote.core.model.AuthState
import com.dvote.core.model.Survey
import com.dvote.core.model.SurveyOption
import com.dvote.core.model.VoteReceipt
import com.dvote.core.model.VoteSubmission
import com.dvote.core.model.VotingKey
import com.dvote.domain.repository.AuthRepository
import com.dvote.domain.repository.VotingRepository
import com.dvote.domain.security.VoteReceiptVerifier
import com.dvote.domain.security.VoteSigner
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SubmitVoteUseCaseTest {
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC)
    private val signer = RecordingVoteSigner()
    private val repository = RecordingVotingRepository()

    private val authRepository = object : AuthRepository {
        override val authState = flowOf(AuthState.SignedIn("user-1"))
        override fun currentUserId() = "user-1"
        override suspend fun signInWithGoogleIdToken(idToken: String) = Result.success(Unit)
        override suspend fun repairProfile(reason: com.dvote.core.model.AuthRepairReason) =
            Result.success(Unit)
        override suspend fun signOut() = Result.success(Unit)
    }

    @Test
    fun matchesSharedCanonicalPayloadVectors() {
        val useCase = useCase()
        val vectorsFile = File(requireNotNull(System.getProperty("dvote.canonicalPayloadVectors")))
        val vectors = Json.parseToJsonElement(vectorsFile.readText()).jsonArray

        vectors.forEach { element ->
            val vector = element.jsonObject
            assertEquals(
                vector.getValue("name").jsonPrimitive.content,
                vector.getValue("canonicalPayload").jsonPrimitive.content,
                useCase.buildPayload(
                    surveyId = vector.getValue("surveyId").jsonPrimitive.content,
                    userId = vector.getValue("userId").jsonPrimitive.content,
                    keyId = vector.getValue("keyId").jsonPrimitive.content,
                    optionIds = vector.getValue("optionIds").jsonArray.map {
                        it.jsonPrimitive.content
                    },
                    signedAt = vector.getValue("signedAt").jsonPrimitive.content.toLong(),
                ),
            )
        }
    }

    @Test
    fun preparesCanonicalSubmissionFromSortedSelection() = runTest {
        val submission = useCase().prepareSubmission(
            survey = survey(allowMultipleChoices = true),
            selectedOptionIds = setOf("b", "a"),
            operationId = OPERATION_ID,
        ).getOrThrow()

        assertEquals(OPERATION_ID, submission.operationId)
        assertEquals(listOf("a", "b"), submission.optionIds)
        assertEquals(clock.millis(), submission.signedAt)
        assertEquals(
            "survey=survey-1|user=user-1|key=${"a".repeat(64)}|" +
                "options=a,b|signedAt=${clock.millis()}",
            submission.canonicalPayload,
        )
        assertEquals("signature:${submission.canonicalPayload}", submission.signature)
        assertEquals(
            "hash:${submission.canonicalPayload}|${submission.signature}|public-key",
            submission.commitmentHash,
        )
        assertEquals(listOf("user-1"), signer.votingKeyUsers)
        assertEquals(listOf("user-1"), signer.signingUsers)
    }

    @Test
    fun rejectsEveryInvalidVoteBoundaryBeforeSigningOrSubmitting() = runTest {
        val cases = listOf(
            survey(allowMultipleChoices = true, expiresAt = clock.millis()) to setOf("a"),
            survey(allowMultipleChoices = true, isActive = false) to setOf("a"),
            survey(allowMultipleChoices = true) to emptySet(),
            survey(allowMultipleChoices = false) to setOf("a", "b"),
            survey(allowMultipleChoices = true) to setOf("other"),
        )

        cases.forEach { (survey, selection) ->
            val result = useCase().invoke(
                survey = survey,
                selectedOptionIds = selection,
                operationId = OPERATION_ID,
            )
            assertTrue(result.isFailure)
        }

        assertTrue(signer.signingUsers.isEmpty())
        assertTrue(repository.submissions.isEmpty())
    }

    @Test
    fun rejectsUnauthenticatedPreparationBeforeReadingTheKey() = runTest {
        val signedOutRepository = object : AuthRepository by authRepository {
            override fun currentUserId(): String? = null
        }

        val result = useCase(auth = signedOutRepository).prepareSubmission(
            survey = survey(allowMultipleChoices = false),
            selectedOptionIds = setOf("a"),
            operationId = OPERATION_ID,
        )

        assertTrue(result.isFailure)
        assertTrue(signer.votingKeyUsers.isEmpty())
    }

    @Test
    fun preservesRepositoryFailureWithoutAttemptingReceiptVerification() = runTest {
        val failure = IllegalStateException("backend failed")
        repository.result = Result.failure(failure)
        val submission = useCase().prepareSubmission(
            survey = survey(allowMultipleChoices = false),
            selectedOptionIds = setOf("a"),
            operationId = OPERATION_ID,
        ).getOrThrow()

        val result = useCase().submit(submission)

        assertSame(failure, result.exceptionOrNull())
        assertEquals(listOf(submission), repository.submissions)
    }

    @Test
    fun preparationRethrowsCancellation() = runTest {
        val cancellation = CancellationException("cancelled")
        signer.votingKeyFailure = cancellation

        var thrown: CancellationException? = null
        try {
            useCase().prepareSubmission(
                survey = survey(allowMultipleChoices = false),
                selectedOptionIds = setOf("a"),
                operationId = OPERATION_ID,
            )
        } catch (error: CancellationException) {
            thrown = error
        }

        assertSame(cancellation, thrown)
        assertFalse(repository.submissions.isNotEmpty())
    }

    private fun useCase(
        auth: AuthRepository = authRepository,
    ) = SubmitVoteUseCase(
        authRepository = auth,
        voteSigner = signer,
        votingRepository = repository,
        receiptVerifier = VoteReceiptVerifier(),
        clock = clock,
    )

    private inner class RecordingVotingRepository : VotingRepository {
        val submissions = mutableListOf<VoteSubmission>()
        var result: Result<VoteReceipt> = Result.failure(UnsupportedOperationException())

        override fun observeHasVoted(surveyId: String) = flowOf(false)

        override suspend fun createSurvey(request: com.dvote.core.model.CreateSurveyRequest) =
            Result.failure<String>(UnsupportedOperationException())

        override suspend fun submitVote(submission: VoteSubmission): Result<VoteReceipt> {
            submissions += submission
            return result
        }
    }

    private class RecordingVoteSigner : VoteSigner {
        val votingKeyUsers = mutableListOf<String>()
        val signingUsers = mutableListOf<String>()
        var votingKeyFailure: Throwable? = null

        override suspend fun votingKey(userId: String): VotingKey {
            votingKeyUsers += userId
            votingKeyFailure?.let { throw it }
            return VotingKey(
                keyId = "a".repeat(64),
                deviceId = "b".repeat(64),
                publicKey = "public-key",
            )
        }

        override suspend fun replaceVotingKey(userId: String) = votingKey(userId)

        override suspend fun signBase64(userId: String, payload: String): String {
            signingUsers += userId
            return "signature:$payload"
        }

        override suspend fun sha256Base64(payload: String) = "hash:$payload"
    }

    private fun survey(
        allowMultipleChoices: Boolean,
        expiresAt: Long = clock.millis() + 60_000,
        isActive: Boolean = true,
    ) = Survey(
        id = "survey-1",
        title = "Survey",
        description = "Description",
        creatorId = "creator",
        creatorName = "Creator",
        createdAt = 1,
        expiresAt = expiresAt,
        isActive = isActive,
        allowMultipleChoices = allowMultipleChoices,
        options = persistentListOf(
            SurveyOption("a", "A"),
            SurveyOption("b", "B"),
        ),
    )

    private companion object {
        const val OPERATION_ID = "01234567-89ab-4cde-8fab-0123456789ab"
    }
}
