package com.dvote.data.firebase.repository

import com.dvote.core.model.VoteSubmission
import com.dvote.core.model.VotingKey
import com.dvote.domain.repository.RepositoryFailure
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FirebaseVotingResponseTest {
    @Test
    fun validatesExactMatchingKeyRegistrationResponse() {
        val votingKey = votingKey()

        validateVotingKeyRegistrationResponse(
            data = keyRegistrationResponse(votingKey),
            votingKey = votingKey,
        )
    }

    @Test
    fun rejectsMalformedKeyRegistrationResponse() {
        val votingKey = votingKey()

        assertInvalidData {
            validateVotingKeyRegistrationResponse(
                data = keyRegistrationResponse(votingKey) + ("unexpected" to true),
                votingKey = votingKey,
            )
        }
        assertInvalidData {
            validateVotingKeyRegistrationResponse(
                data = keyRegistrationResponse(votingKey).toMutableMap().apply {
                    this["registeredAt"] = 1.5
                },
                votingKey = votingKey,
            )
        }
        assertInvalidData {
            validateVotingKeyRegistrationResponse(
                data = keyRegistrationResponse(votingKey).toMutableMap().apply {
                    this["keyId"] = "c".repeat(64)
                },
                votingKey = votingKey,
            )
        }
        assertInvalidData {
            validateVotingKeyRegistrationResponse(
                data = keyRegistrationResponse(votingKey).toMutableMap().apply {
                    this["deviceId"] = "c".repeat(64)
                },
                votingKey = votingKey,
            )
        }
        assertInvalidData {
            validateVotingKeyRegistrationResponse(
                data = keyRegistrationResponse(votingKey).toMutableMap().apply {
                    this["status"] = "rotated"
                },
                votingKey = votingKey,
            )
        }
        assertInvalidData {
            validateVotingKeyRegistrationResponse(
                data = keyRegistrationResponse(votingKey).toMutableMap().apply {
                    this["registeredAt"] = -1L
                },
                votingKey = votingKey,
            )
        }
    }

    @Test
    fun parsesExactCreateSurveyResponse() {
        assertEquals(
            "survey-1",
            parseCreateSurveyResponse(mapOf("surveyId" to "survey-1")),
        )
    }

    @Test
    fun rejectsMalformedCreateSurveyResponse() {
        assertInvalidData {
            parseCreateSurveyResponse(
                mapOf(
                    "surveyId" to "survey-1",
                    "unexpected" to true,
                )
            )
        }
        assertInvalidData {
            parseCreateSurveyResponse(mapOf("surveyId" to "invalid/path"))
        }
    }

    @Test
    fun parsesExactMatchingVoteResponse() {
        val submission = submission()
        val receipt = parseSubmitVoteResponse(
            data = response(submission),
            submission = submission,
        )

        assertEquals(submission.surveyId, receipt.surveyId)
        assertEquals(submission.keyId, receipt.keyId)
        assertEquals(123L, receipt.acceptedAt)
    }

    @Test
    fun rejectsExtraMismatchedAndFractionalVoteResponseData() {
        val submission = submission()

        assertInvalidData {
            parseSubmitVoteResponse(
                data = response(submission) + ("unexpected" to true),
                submission = submission,
            )
        }
        assertInvalidData {
            parseSubmitVoteResponse(
                data = response(submission).toMutableMap().apply {
                    this["canonicalPayload"] = "changed"
                },
                submission = submission,
            )
        }
        assertInvalidData {
            parseSubmitVoteResponse(
                data = response(submission).toMutableMap().apply {
                    this["acceptedAt"] = 123.5
                },
                submission = submission,
            )
        }
    }

    private fun assertInvalidData(block: () -> Unit) {
        val failure = assertThrows(RepositoryFailure::class.java, block)
        assertEquals(RepositoryFailure.Kind.INVALID_DATA, failure.kind)
    }

    private fun submission() = VoteSubmission(
        operationId = "01234567-89ab-4cde-8fab-0123456789ab",
        surveyId = "survey-1",
        keyId = "a".repeat(64),
        optionIds = persistentListOf("option-1"),
        signedAt = 100L,
        canonicalPayload = "payload",
        publicKey = "public-key",
        signature = "signature",
        commitmentHash = "commitment",
    )

    private fun votingKey() = VotingKey(
        keyId = "a".repeat(64),
        deviceId = "b".repeat(64),
        publicKey = "public-key",
    )

    private fun keyRegistrationResponse(
        votingKey: VotingKey,
    ): Map<String, Any?> = mapOf(
        "keyId" to votingKey.keyId,
        "deviceId" to votingKey.deviceId,
        "status" to "active",
        "registeredAt" to 123L,
    )

    private fun response(
        submission: VoteSubmission,
    ): Map<String, Any?> = mapOf(
        "surveyId" to submission.surveyId,
        "receiptId" to "b".repeat(64),
        "keyId" to submission.keyId,
        "commitmentHash" to submission.commitmentHash,
        "acceptedAt" to 123L,
        "canonicalPayload" to submission.canonicalPayload,
        "publicKey" to submission.publicKey,
        "signature" to submission.signature,
    )
}
