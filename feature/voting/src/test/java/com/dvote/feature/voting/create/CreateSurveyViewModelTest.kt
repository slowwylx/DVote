package com.dvote.feature.voting.create

import com.dvote.core.model.CreateSurveyRequest
import com.dvote.core.model.VoteReceipt
import com.dvote.core.model.VoteSubmission
import com.dvote.domain.repository.VotingRepository
import com.dvote.domain.usecase.CreateSurveyUseCase
import com.dvote.feature.voting.VotingUiError
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreateSurveyViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = Clock.fixed(Instant.ofEpochMilli(10_000), ZoneOffset.UTC)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun retryReusesTheExactCreateRequest() = runTest(dispatcher) {
        val repository = RecordingVotingRepository()
        val viewModel = CreateSurveyViewModel(
            createSurvey = CreateSurveyUseCase(repository, clock),
            clock = clock,
        )
        viewModel.onTitleChanged("Community garden")
        viewModel.onDescriptionChanged("Choose the next improvement for our community garden.")
        viewModel.onOptionChanged(0, "More benches")
        viewModel.onOptionChanged(1, "Native flowers")

        viewModel.submit()
        runCurrent()
        assertEquals(VotingUiError.CREATE_SURVEY_FAILED, viewModel.uiState.value.error)

        viewModel.submit()
        runCurrent()

        assertEquals(2, repository.requests.size)
        assertEquals(repository.requests.first(), repository.requests.last())
    }

    @Test
    fun editingAfterFailureCreatesANewOperation() = runTest(dispatcher) {
        val repository = RecordingVotingRepository()
        val viewModel = validViewModel(repository)

        viewModel.submit()
        runCurrent()
        viewModel.onTitleChanged("Updated community garden")
        viewModel.submit()
        runCurrent()

        assertEquals(2, repository.requests.size)
        assertNotEquals(
            repository.requests.first().operationId,
            repository.requests.last().operationId,
        )
        assertEquals("Updated community garden", repository.requests.last().title)
    }

    @Test
    fun submissionGuardIsSynchronousAndSuccessEmitsOneEffect() =
        runTest(dispatcher) {
            val repository = GatedVotingRepository()
            val viewModel = validViewModel(repository)

            viewModel.submit()
            viewModel.submit()
            runCurrent()

            assertEquals(1, repository.requests.size)
            assertTrue(viewModel.uiState.value.isSubmitting)

            val effect = async { viewModel.effects.first() }
            repository.gate.complete(Result.success("survey-1"))
            runCurrent()

            assertEquals(CreateSurveyEffect.OpenSurvey("survey-1"), effect.await())
            assertFalse(viewModel.uiState.value.isSubmitting)
            assertEquals(1, repository.requests.size)
        }

    @Test
    fun optionEditingStaysWithinSupportedCount() {
        val viewModel = CreateSurveyViewModel(
            createSurvey = CreateSurveyUseCase(RecordingVotingRepository(), clock),
            clock = clock,
        )

        repeat(20) { viewModel.addOption() }
        assertEquals(MAX_SURVEY_OPTION_COUNT, viewModel.uiState.value.options.size)

        repeat(20) { viewModel.removeOption(0) }
        assertEquals(MIN_SURVEY_OPTION_COUNT, viewModel.uiState.value.options.size)
    }

    private fun validViewModel(
        repository: VotingRepository,
    ): CreateSurveyViewModel {
        return CreateSurveyViewModel(
            createSurvey = CreateSurveyUseCase(repository, clock),
            clock = clock,
        ).apply {
            onTitleChanged("Community garden")
            onDescriptionChanged(
                "Choose the next improvement for our community garden."
            )
            onOptionChanged(0, "More benches")
            onOptionChanged(1, "Native flowers")
        }
    }

    private class RecordingVotingRepository : VotingRepository {
        val requests = mutableListOf<CreateSurveyRequest>()

        override fun observeHasVoted(surveyId: String) = flowOf(false)

        override suspend fun createSurvey(request: CreateSurveyRequest): Result<String> {
            requests += request
            return if (requests.size == 1) {
                Result.failure(IllegalStateException("Temporary failure"))
            } else {
                Result.success("survey-1")
            }
        }

        override suspend fun submitVote(submission: VoteSubmission): Result<VoteReceipt> {
            return Result.failure(UnsupportedOperationException())
        }
    }

    private class GatedVotingRepository : VotingRepository {
        val requests = mutableListOf<CreateSurveyRequest>()
        val gate = CompletableDeferred<Result<String>>()

        override fun observeHasVoted(surveyId: String) = flowOf(false)

        override suspend fun createSurvey(request: CreateSurveyRequest): Result<String> {
            requests += request
            return gate.await()
        }

        override suspend fun submitVote(submission: VoteSubmission): Result<VoteReceipt> {
            return Result.failure(UnsupportedOperationException())
        }
    }
}
