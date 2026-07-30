package com.dvote.feature.voting.home

import com.dvote.core.model.Survey
import com.dvote.core.model.SurveyOption
import com.dvote.core.model.SurveyResult
import com.dvote.domain.repository.SurveyPage
import com.dvote.domain.repository.SurveyPageCursor
import com.dvote.domain.repository.SurveyRepository
import com.dvote.feature.voting.VotingUiError
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun removesSurveyWhenItExpiresWhileObserved() = runTest(dispatcher) {
        val clock = MutableClock(10_000L)
        val repository = FakeSurveyRepository(
            persistentListOf(survey(expiresAt = 11_000L))
        )
        val viewModel = HomeViewModel(repository, clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        runCurrent()
        assertEquals(1, viewModel.uiState.value.surveys().size)

        clock.advanceBy(1_000L)
        advanceTimeBy(1_000L)
        runCurrent()

        assertTrue(viewModel.uiState.value.surveys().isEmpty())
    }

    @Test
    fun updatedExpiryCancelsThePreviousRemovalTimer() = runTest(dispatcher) {
        val clock = MutableClock(10_000L)
        val repository = FakeSurveyRepository(
            persistentListOf(survey(expiresAt = 11_000L))
        )
        val viewModel = HomeViewModel(repository, clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        runCurrent()

        clock.advanceBy(500L)
        advanceTimeBy(500L)
        repository.emit(persistentListOf(survey(expiresAt = 12_000L)))
        runCurrent()

        clock.advanceBy(500L)
        advanceTimeBy(500L)
        runCurrent()
        assertEquals(1, viewModel.uiState.value.surveys().size)

        clock.advanceBy(1_000L)
        advanceTimeBy(1_000L)
        runCurrent()
        assertTrue(viewModel.uiState.value.surveys().isEmpty())
    }

    @Test
    fun filtersInactiveAndAlreadyExpiredSurveyDocuments() = runTest(dispatcher) {
        val clock = MutableClock(10_000L)
        val repository = FakeSurveyRepository(
            persistentListOf(
                survey(id = "active", expiresAt = 11_000L),
                survey(id = "expired", expiresAt = 10_000L),
                survey(id = "inactive", expiresAt = 11_000L, isActive = false),
            )
        )
        val viewModel = HomeViewModel(repository, clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        runCurrent()

        assertEquals(listOf("active"), viewModel.uiState.value.surveys().map(Survey::id))
    }

    @Test
    fun appendsTheNextPageOnlyWhenRequested() = runTest(dispatcher) {
        val cursor = SurveyPageCursor(
            expiresAt = 20_000L,
            createdAt = 2L,
            surveyId = "survey-1",
        )
        val repository = FakeSurveyRepository(
            surveys = persistentListOf(
                survey(id = "survey-1", expiresAt = 20_000L),
            ),
            firstPageCursor = cursor,
            nextPages = ArrayDeque(
                listOf(
                    SurveyPage(
                        surveys = persistentListOf(
                            survey(id = "survey-2", expiresAt = 30_000L),
                        ),
                        nextCursor = null,
                    )
                )
            ),
        )
        val viewModel = HomeViewModel(repository, MutableClock(10_000L))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }

        runCurrent()
        assertEquals(listOf("survey-1"), viewModel.uiState.value.surveys().map(Survey::id))
        assertEquals(0, repository.pageLoads)

        viewModel.loadNextPage()
        runCurrent()

        assertEquals(
            listOf("survey-1", "survey-2"),
            viewModel.uiState.value.surveys().map(Survey::id),
        )
        assertEquals(1, repository.pageLoads)
        assertTrue(!(viewModel.uiState.value as HomeUiState.Content).canLoadMore)
    }

    @Test
    fun keepsLoadedSurveysVisibleWhenAppendFails() = runTest(dispatcher) {
        val repository = FakeSurveyRepository(
            surveys = persistentListOf(
                survey(id = "survey-1", expiresAt = 20_000L),
            ),
            firstPageCursor = SurveyPageCursor(
                expiresAt = 20_000L,
                createdAt = 2L,
                surveyId = "survey-1",
            ),
            pageFailure = IllegalStateException("network"),
        )
        val viewModel = HomeViewModel(repository, MutableClock(10_000L))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        runCurrent()

        viewModel.loadNextPage()
        runCurrent()

        val state = viewModel.uiState.value as HomeUiState.Content
        assertEquals(listOf("survey-1"), state.surveys.map(Survey::id))
        assertEquals(VotingUiError.LOAD_SURVEYS_FAILED, state.loadMoreError)
        assertTrue(state.canLoadMore)
    }

    private fun HomeUiState.surveys(): ImmutableList<Survey> {
        return (this as HomeUiState.Content).surveys
    }

    private fun survey(
        id: String = "survey-1",
        expiresAt: Long,
        isActive: Boolean = true,
    ) = Survey(
        id = id,
        title = "Survey",
        description = "Description",
        creatorId = "creator-1",
        creatorName = "Creator",
        createdAt = 1L,
        expiresAt = expiresAt,
        isActive = isActive,
        allowMultipleChoices = false,
        options = persistentListOf(
            SurveyOption("option-a", "A"),
            SurveyOption("option-b", "B"),
        ),
    )

    private class FakeSurveyRepository(
        surveys: ImmutableList<Survey>,
        private val firstPageCursor: SurveyPageCursor? = null,
        private val nextPages: ArrayDeque<SurveyPage> = ArrayDeque(),
        private val pageFailure: Throwable? = null,
    ) : SurveyRepository {
        private val surveysFlow = MutableStateFlow(surveys)
        var pageLoads = 0
            private set

        fun emit(surveys: ImmutableList<Survey>) {
            surveysFlow.value = surveys
        }

        override fun observeFirstActiveSurveyPage(pageSize: Int): Flow<SurveyPage> {
            return surveysFlow.map { values ->
                SurveyPage(
                    surveys = values,
                    nextCursor = firstPageCursor,
                )
            }
        }

        override suspend fun loadActiveSurveyPage(
            after: SurveyPageCursor,
            pageSize: Int,
        ): SurveyPage {
            pageLoads += 1
            pageFailure?.let { throw it }
            return nextPages.removeFirst()
        }

        override fun cachedSurvey(surveyId: String): Survey? = null

        override fun observeSurvey(surveyId: String): Flow<Survey?> = flowOf(null)

        override fun observeFinalResult(
            surveyId: String,
            optionIds: ImmutableSet<String>,
        ): Flow<SurveyResult?> = flowOf(null)
    }

    private class MutableClock(
        private var currentMillis: Long,
    ) : Clock() {
        override fun instant(): Instant = Instant.ofEpochMilli(currentMillis)

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        fun advanceBy(millis: Long) {
            currentMillis += millis
        }
    }
}
