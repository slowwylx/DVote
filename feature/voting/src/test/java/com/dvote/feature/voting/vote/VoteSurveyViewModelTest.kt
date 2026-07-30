package com.dvote.feature.voting.vote

import com.dvote.core.model.AuthState
import com.dvote.core.model.CreateSurveyRequest
import com.dvote.core.model.Survey
import com.dvote.core.model.SurveyOption
import com.dvote.core.model.SurveyResult
import com.dvote.core.model.VoteReceipt
import com.dvote.core.model.VoteSubmission
import com.dvote.core.model.VotingKey
import com.dvote.domain.repository.AuthRepository
import com.dvote.domain.repository.RepositoryFailure
import com.dvote.domain.repository.SurveyRepository
import com.dvote.domain.repository.SurveyPage
import com.dvote.domain.repository.SurveyPageCursor
import com.dvote.domain.repository.VotingRepository
import com.dvote.domain.security.VoteReceiptVerifier
import com.dvote.domain.security.VoteSigner
import com.dvote.domain.usecase.SubmitVoteUseCase
import com.dvote.feature.voting.VotingUiError
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoteSurveyViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = Clock.fixed(Instant.ofEpochMilli(10_000), ZoneOffset.UTC)
    private val keyPair = KeyPairGenerator.getInstance("EC").run {
        initialize(ECGenParameterSpec("secp256r1"))
        generateKeyPair()
    }
    private val encodedPublicKey = Base64.getEncoder().encodeToString(keyPair.public.encoded)
    private val keyId = sha256Hex(keyPair.public.encoded)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun cachedSurveySeedsDestinationBeforeFlowCollection() = runTest(dispatcher) {
        val cachedSurvey = survey(
            expiresAt = clock.millis() + 60_000,
            isActive = true,
        )
        val repository = FakeSurveyRepository(
            survey = cachedSurvey,
            finalResult = SurveyResult.Empty,
        )

        val viewModel = viewModel(repository)

        assertEquals(cachedSurvey, viewModel.uiState.value.survey)
        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.isVoteStatusLoading)
    }

    @Test
    fun keepsAvailableAggregateHiddenWhileSurveyIsActive() = runTest(dispatcher) {
        val repository = FakeSurveyRepository(
            survey = survey(
                expiresAt = clock.millis() + 60_000,
                isActive = true,
            ),
            finalResult = SurveyResult(
                totalVotes = 2,
                optionVotes = persistentMapOf("option-a" to 2L),
            ),
        )
        val viewModel = viewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        runCurrent()

        assertFalse(viewModel.uiState.value.isSurveyClosed)
        assertNull(viewModel.uiState.value.finalResult)
        assertEquals(0, repository.finalResultCollections)
    }

    @Test
    fun loadsFinalResultOnlyAfterSurveyExpiry() = runTest(dispatcher) {
        val result = SurveyResult(
            totalVotes = 2,
            optionVotes = persistentMapOf("option-a" to 2L),
        )
        val repository = FakeSurveyRepository(
            survey = survey(
                expiresAt = clock.millis() + 1_000,
                isActive = true,
            ),
            finalResult = result,
        )
        val viewModel = viewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        runCurrent()
        assertFalse(viewModel.uiState.value.isSurveyClosed)
        assertEquals(0, repository.finalResultCollections)

        advanceTimeBy(2_000)
        runCurrent()

        assertTrue(viewModel.uiState.value.isSurveyClosed)
        assertEquals(result, viewModel.uiState.value.finalResult)
        assertEquals(1, repository.finalResultCollections)
    }

    @Test
    fun retryReusesTheExactSignedSubmission() = runTest(dispatcher) {
        val surveyRepository = FakeSurveyRepository(
            survey = survey(
                expiresAt = clock.millis() + 60_000,
                isActive = true,
            ),
            finalResult = SurveyResult.Empty,
        )
        val votingRepository = RecordingVotingRepository()
        val viewModel = viewModel(surveyRepository, votingRepository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        runCurrent()

        viewModel.toggleOption("option-a")
        runCurrent()
        viewModel.submit()
        runCurrent()
        viewModel.submit()
        runCurrent()

        assertEquals(2, votingRepository.submissions.size)
        assertEquals(
            votingRepository.submissions.first(),
            votingRepository.submissions.last(),
        )
    }

    @Test
    fun successfulVoteDisablesInteractionAfterReceiptDismissal() = runTest(dispatcher) {
        val surveyRepository = FakeSurveyRepository(
            survey = survey(
                expiresAt = clock.millis() + 60_000,
                isActive = true,
            ),
            finalResult = SurveyResult.Empty,
        )
        val votingRepository = RecordingVotingRepository(failFirstSubmission = false)
        val viewModel = viewModel(surveyRepository, votingRepository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        runCurrent()

        viewModel.toggleOption("option-a")
        runCurrent()
        viewModel.submit()
        runCurrent()

        assertTrue(viewModel.uiState.value.hasVoted)
        assertEquals(1, votingRepository.submissions.size)
        assertTrue(viewModel.uiState.value.receipt != null)

        viewModel.dismissReceipt()
        viewModel.toggleOption("option-b")
        viewModel.submit()
        runCurrent()

        assertNull(viewModel.uiState.value.receipt)
        assertTrue(viewModel.uiState.value.hasVoted)
        assertTrue(viewModel.uiState.value.selectedOptionIds.isEmpty())
        assertEquals(1, votingRepository.submissions.size)
    }

    @Test
    fun authoritativeVoteDocumentDisablesVotingAfterDestinationReentry() = runTest(dispatcher) {
        val surveyRepository = FakeSurveyRepository(
            survey = survey(
                expiresAt = clock.millis() + 60_000,
                isActive = true,
            ),
            finalResult = SurveyResult.Empty,
        )
        val votingRepository = RecordingVotingRepository(initialHasVoted = true)
        val viewModel = viewModel(surveyRepository, votingRepository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        runCurrent()

        assertTrue(viewModel.uiState.value.hasVoted)
        assertFalse(viewModel.uiState.value.isVoteStatusLoading)

        viewModel.toggleOption("option-a")
        viewModel.submit()
        runCurrent()

        assertTrue(viewModel.uiState.value.selectedOptionIds.isEmpty())
        assertTrue(votingRepository.submissions.isEmpty())
    }

    @Test
    fun surveyUpdateCancelsThePreviousExpiryTimer() = runTest(dispatcher) {
        val repository = FakeSurveyRepository(
            survey = survey(
                expiresAt = clock.millis() + 1_000,
                isActive = true,
            ),
            finalResult = SurveyResult.Empty,
        )
        val viewModel = viewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        runCurrent()

        repository.emitSurvey(
            survey(
                expiresAt = clock.millis() + 5_000,
                isActive = true,
            )
        )
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()

        assertFalse(viewModel.uiState.value.isSurveyClosed)
        assertEquals(0, repository.finalResultCollections)
    }

    @Test
    fun expiryClearsSelectionAndPreventsSubmission() = runTest(dispatcher) {
        val surveyRepository = FakeSurveyRepository(
            survey = survey(
                expiresAt = clock.millis() + 1_000,
                isActive = true,
            ),
            finalResult = SurveyResult.Empty,
        )
        val votingRepository = RecordingVotingRepository(failFirstSubmission = false)
        val viewModel = viewModel(surveyRepository, votingRepository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        runCurrent()

        viewModel.toggleOption("option-a")
        runCurrent()
        advanceTimeBy(2_000)
        runCurrent()

        assertTrue(viewModel.uiState.value.isSurveyClosed)
        assertTrue(viewModel.uiState.value.selectedOptionIds.isEmpty())

        viewModel.submit()
        runCurrent()
        assertTrue(votingRepository.submissions.isEmpty())
    }

    @Test
    fun missingSurveyProducesPersistentNotFoundState() = runTest(dispatcher) {
        val repository = FakeSurveyRepository(
            survey = null,
            finalResult = SurveyResult.Empty,
        )
        val viewModel = viewModel(repository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        runCurrent()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(VotingUiError.SURVEY_NOT_FOUND, viewModel.uiState.value.error)
    }

    @Test
    fun voteStatusFailureBlocksSelectionAndSubmission() = runTest(dispatcher) {
        val surveyRepository = FakeSurveyRepository(
            survey = survey(
                expiresAt = clock.millis() + 60_000,
                isActive = true,
            ),
            finalResult = SurveyResult.Empty,
        )
        val votingRepository = object : VotingRepository {
            override fun observeHasVoted(surveyId: String): Flow<Boolean> = flow {
                throw RepositoryFailure(
                    kind = RepositoryFailure.Kind.REJECTED,
                    message = "private diagnostic",
                )
            }

            override suspend fun createSurvey(request: CreateSurveyRequest) =
                Result.failure<String>(UnsupportedOperationException())

            override suspend fun submitVote(submission: VoteSubmission) =
                Result.failure<VoteReceipt>(UnsupportedOperationException())
        }
        val viewModel = viewModel(surveyRepository, votingRepository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        runCurrent()

        assertEquals(
            VotingUiError.REQUEST_REJECTED,
            viewModel.uiState.value.voteStatusError,
        )

        viewModel.toggleOption("option-a")
        viewModel.submit()
        runCurrent()

        assertTrue(viewModel.uiState.value.selectedOptionIds.isEmpty())
    }

    @Test
    fun submissionGuardPreventsConcurrentDuplicateVotes() = runTest(dispatcher) {
        val surveyRepository = FakeSurveyRepository(
            survey = survey(
                expiresAt = clock.millis() + 60_000,
                isActive = true,
            ),
            finalResult = SurveyResult.Empty,
        )
        val votingRepository = GatedVotingRepository()
        val viewModel = viewModel(surveyRepository, votingRepository)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        runCurrent()

        viewModel.toggleOption("option-a")
        runCurrent()
        viewModel.submit()
        viewModel.submit()
        runCurrent()

        assertEquals(1, votingRepository.submissions.size)
        assertTrue(viewModel.uiState.value.isSubmitting)

        votingRepository.gate.complete(Unit)
        runCurrent()

        assertEquals(1, votingRepository.submissions.size)
        assertTrue(viewModel.uiState.value.hasVoted)
    }

    private fun viewModel(
        repository: SurveyRepository,
        votingRepository: VotingRepository = this.votingRepository,
    ) = VoteSurveyViewModel(
        surveyId = "survey-1",
        surveyRepository = repository,
        votingRepository = votingRepository,
        submitVote = SubmitVoteUseCase(
            authRepository = authRepository,
            voteSigner = signer,
            votingRepository = votingRepository,
            receiptVerifier = VoteReceiptVerifier(),
            clock = clock,
        ),
        clock = clock,
    )

    private fun survey(
        expiresAt: Long,
        isActive: Boolean,
    ) = Survey(
        id = "survey-1",
        title = "Survey",
        description = "Description",
        creatorId = "creator-1",
        creatorName = "Creator",
        createdAt = 1,
        expiresAt = expiresAt,
        isActive = isActive,
        allowMultipleChoices = false,
        options = persistentListOf(
            SurveyOption("option-a", "A"),
            SurveyOption("option-b", "B"),
        ),
    )

    private val authRepository = object : AuthRepository {
        override val authState = flowOf(AuthState.SignedIn("user-1"))

        override fun currentUserId() = "user-1"

        override suspend fun signInWithGoogleIdToken(idToken: String) = Result.success(Unit)

        override suspend fun repairProfile(reason: com.dvote.core.model.AuthRepairReason) =
            Result.success(Unit)

        override suspend fun signOut() = Result.success(Unit)
    }

    private val signer = object : VoteSigner {
        override suspend fun votingKey(userId: String) = VotingKey(
            keyId = keyId,
            deviceId = "b".repeat(64),
            publicKey = encodedPublicKey,
        )

        override suspend fun replaceVotingKey(userId: String) = votingKey(userId)

        override suspend fun signBase64(userId: String, payload: String): String {
            return Signature.getInstance("SHA256withECDSA").run {
                initSign(keyPair.private)
                update(payload.toByteArray(Charsets.UTF_8))
                Base64.getEncoder().encodeToString(sign())
            }
        }

        override suspend fun sha256Base64(payload: String): String {
            return Base64.getEncoder().encodeToString(
                MessageDigest.getInstance("SHA-256")
                    .digest(payload.toByteArray(Charsets.UTF_8))
            )
        }
    }

    private val votingRepository = object : VotingRepository {
        override fun observeHasVoted(surveyId: String) = flowOf(false)

        override suspend fun createSurvey(request: CreateSurveyRequest) =
            Result.failure<String>(UnsupportedOperationException())

        override suspend fun submitVote(submission: VoteSubmission) =
            Result.success(receiptFor(submission))
    }

    private inner class RecordingVotingRepository(
        initialHasVoted: Boolean = false,
        private val failFirstSubmission: Boolean = true,
    ) : VotingRepository {
        val submissions = mutableListOf<VoteSubmission>()
        private val hasVotedFlow = MutableStateFlow(initialHasVoted)

        override fun observeHasVoted(surveyId: String) = hasVotedFlow

        override suspend fun createSurvey(request: CreateSurveyRequest) =
            Result.failure<String>(UnsupportedOperationException())

        override suspend fun submitVote(submission: VoteSubmission): Result<VoteReceipt> {
            submissions += submission
            return if (failFirstSubmission && submissions.size == 1) {
                Result.failure(IllegalStateException("Temporary failure"))
            } else {
                Result.success(receiptFor(submission))
            }
        }
    }

    private inner class GatedVotingRepository : VotingRepository {
        val submissions = mutableListOf<VoteSubmission>()
        val gate = CompletableDeferred<Unit>()

        override fun observeHasVoted(surveyId: String) = flowOf(false)

        override suspend fun createSurvey(request: CreateSurveyRequest) =
            Result.failure<String>(UnsupportedOperationException())

        override suspend fun submitVote(submission: VoteSubmission): Result<VoteReceipt> {
            submissions += submission
            gate.await()
            return Result.success(receiptFor(submission))
        }
    }

    private fun receiptFor(submission: VoteSubmission) = VoteReceipt(
        surveyId = submission.surveyId,
        receiptId = sha256Hex(submission.commitmentHash.toByteArray(Charsets.UTF_8)),
        keyId = submission.keyId,
        commitmentHash = submission.commitmentHash,
        acceptedAt = clock.millis(),
        canonicalPayload = submission.canonicalPayload,
        publicKey = submission.publicKey,
        signature = submission.signature,
    )

    private fun sha256Hex(value: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private class FakeSurveyRepository(
        survey: Survey?,
        finalResult: SurveyResult,
    ) : SurveyRepository {
        private val surveyFlow = MutableStateFlow<Survey?>(survey)
        private val finalResultFlow = MutableStateFlow<SurveyResult?>(finalResult)

        var finalResultCollections = 0
            private set

        fun emitSurvey(survey: Survey?) {
            surveyFlow.value = survey
        }

        override fun observeFirstActiveSurveyPage(pageSize: Int): Flow<SurveyPage> =
            flowOf(
                SurveyPage(
                    surveys = persistentListOf(),
                    nextCursor = null,
                )
            )

        override suspend fun loadActiveSurveyPage(
            after: SurveyPageCursor,
            pageSize: Int,
        ): SurveyPage = SurveyPage(
            surveys = persistentListOf(),
            nextCursor = null,
        )

        override fun cachedSurvey(surveyId: String): Survey? = surveyFlow.value

        override fun observeSurvey(surveyId: String): Flow<Survey?> = surveyFlow

        override fun observeFinalResult(
            surveyId: String,
            optionIds: ImmutableSet<String>,
        ): Flow<SurveyResult?> = flow {
            finalResultCollections += 1
            emitAll(finalResultFlow)
        }
    }
}
