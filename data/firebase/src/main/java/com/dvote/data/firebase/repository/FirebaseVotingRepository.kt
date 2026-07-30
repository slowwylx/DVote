package com.dvote.data.firebase.repository

import com.dvote.core.model.AuthState
import com.dvote.core.model.CreateSurveyRequest
import com.dvote.core.model.VoteReceipt
import com.dvote.core.model.VoteSubmission
import com.dvote.domain.repository.AuthRepository
import com.dvote.domain.repository.VotingRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableOptions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseVotingRepository @Inject constructor(
    private val functions: FirebaseFunctions,
    private val authRepository: AuthRepository,
    private val firestore: FirebaseFirestore,
) : VotingRepository {
    private val callableOptions = HttpsCallableOptions.Builder()
        .setLimitedUseAppCheckTokens(true)
        .build()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeHasVoted(surveyId: String): Flow<Boolean> {
        return authRepository.authState
            .flatMapLatest { authState ->
                when (authState) {
                    is AuthState.SignedIn -> observeVote(
                        surveyId = surveyId,
                        userId = authState.userId,
                    )
                    AuthState.Checking,
                    AuthState.SignedOut,
                    is AuthState.RepairRequired,
                    -> emptyFlow()
                }
            }
            .distinctUntilChanged()
    }

    override suspend fun createSurvey(request: CreateSurveyRequest): Result<String> {
        return runFirebaseCatching {
            val response = functions
                .getHttpsCallable(CREATE_SURVEY, callableOptions)
                .call(
                    mapOf(
                        "operationId" to request.operationId,
                        "title" to request.title,
                        "description" to request.description,
                        "allowMultipleChoices" to request.allowMultipleChoices,
                        "options" to request.options,
                        "expiresAt" to request.expiresAt,
                    )
                )
                .await()
                .data
            parseCreateSurveyResponse(response)
        }
    }

    override suspend fun submitVote(submission: VoteSubmission): Result<VoteReceipt> {
        return runFirebaseCatching {
            val response = functions
                .getHttpsCallable(SUBMIT_VOTE, callableOptions)
                .call(
                    mapOf(
                        "operationId" to submission.operationId,
                        "surveyId" to submission.surveyId,
                        "keyId" to submission.keyId,
                        "optionIds" to submission.optionIds,
                        "signedAt" to submission.signedAt,
                        "publicKey" to submission.publicKey,
                        "signature" to submission.signature,
                        "commitmentHash" to submission.commitmentHash,
                    )
                )
                .await()
                .data
            parseSubmitVoteResponse(
                data = response,
                submission = submission,
            )
        }
    }

    private fun observeVote(
        surveyId: String,
        userId: String,
    ): Flow<Boolean> = callbackFlow {
        val listener = firestore.collection(SURVEYS)
            .document(surveyId)
            .collection(VOTES)
            .document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error.toRepositoryFailure())
                    return@addSnapshotListener
                }
                trySend(snapshot?.exists() == true)
            }

        awaitClose { listener.remove() }
    }

    private companion object {
        const val CREATE_SURVEY = "createSurvey"
        const val SUBMIT_VOTE = "submitVote"
        const val SURVEYS = "surveys"
        const val VOTES = "votes"
    }
}

internal fun parseCreateSurveyResponse(data: Any?): String {
    val response = data as? Map<*, *> ?: throw invalidRepositoryData()
    if (response.keys != CREATE_SURVEY_RESPONSE_FIELDS) throw invalidRepositoryData()
    return (response["surveyId"] as? String)
        ?.takeIf { it.matches(DOCUMENT_ID_PATTERN) }
        ?: throw invalidRepositoryData()
}

internal fun parseSubmitVoteResponse(
    data: Any?,
    submission: VoteSubmission,
): VoteReceipt {
    val response = data as? Map<*, *> ?: throw invalidRepositoryData()
    if (response.keys != SUBMIT_VOTE_RESPONSE_FIELDS) throw invalidRepositoryData()

    val surveyId = response.requireResponseString("surveyId")
    val receiptId = response.requireResponseString("receiptId")
    val keyId = response.requireResponseString("keyId")
    val commitmentHash = response.requireResponseString("commitmentHash")
    val canonicalPayload = response.requireResponseString("canonicalPayload")
    val publicKey = response.requireResponseString("publicKey")
    val signature = response.requireResponseString("signature")
    val acceptedAt = response["acceptedAt"]
        .toExactLongOrNull()
        ?.takeIf { it > 0L }
        ?: throw invalidRepositoryData()

    if (
        surveyId != submission.surveyId ||
        keyId != submission.keyId ||
        commitmentHash != submission.commitmentHash ||
        canonicalPayload != submission.canonicalPayload ||
        publicKey != submission.publicKey ||
        signature != submission.signature ||
        !receiptId.matches(SHA256_HEX_PATTERN)
    ) {
        throw invalidRepositoryData()
    }

    return VoteReceipt(
        surveyId = surveyId,
        receiptId = receiptId,
        keyId = keyId,
        commitmentHash = commitmentHash,
        acceptedAt = acceptedAt,
        canonicalPayload = canonicalPayload,
        publicKey = publicKey,
        signature = signature,
    )
}

private fun Map<*, *>.requireResponseString(name: String): String {
    return (this[name] as? String)
        ?.takeIf { it.isNotBlank() }
        ?: throw invalidRepositoryData()
}

private val CREATE_SURVEY_RESPONSE_FIELDS = setOf("surveyId")
private val SUBMIT_VOTE_RESPONSE_FIELDS = setOf(
    "surveyId",
    "receiptId",
    "keyId",
    "commitmentHash",
    "acceptedAt",
    "canonicalPayload",
    "publicKey",
    "signature",
)
private val DOCUMENT_ID_PATTERN = Regex("^[A-Za-z0-9_-]{1,128}$")
private val SHA256_HEX_PATTERN = Regex("^[0-9a-f]{64}$")
