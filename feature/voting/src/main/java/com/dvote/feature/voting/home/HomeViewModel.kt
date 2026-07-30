package com.dvote.feature.voting.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dvote.core.model.Survey
import com.dvote.core.model.isClosedAt
import com.dvote.domain.coroutines.runSuspendCatching
import com.dvote.domain.repository.SurveyPage
import com.dvote.domain.repository.SurveyPageCursor
import com.dvote.domain.repository.SurveyRepository
import com.dvote.feature.voting.VotingUiError
import com.dvote.feature.voting.retryTransientFailures
import com.dvote.feature.voting.toVotingUiError
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val surveyRepository: SurveyRepository,
    clock: Clock,
) : ViewModel() {
    private val refreshRequests = MutableStateFlow(0L)
    private val appendedPages = MutableStateFlow(AppendedPages())
    private var pageRequestInFlight = false

    @OptIn(ExperimentalCoroutinesApi::class)
    private val firstPageState = refreshRequests
        .flatMapLatest {
            surveyRepository.observeFirstActiveSurveyPage()
                .retryTransientFailures()
                .map<SurveyPage, FirstPageState>(FirstPageState::Content)
                .onStart { emit(FirstPageState.Loading) }
                .catch { cause ->
                    emit(
                        FirstPageState.Error(
                            cause.toVotingUiError(VotingUiError.LOAD_SURVEYS_FAILED)
                        )
                    )
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = FirstPageState.Loading,
        )

    val uiState = combine(firstPageState, appendedPages) { firstPage, appended ->
        firstPage.toHomeUiState(appended)
    }
        .whileActive(clock)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState.Loading,
        )

    fun loadNextPage() {
        if (pageRequestInFlight) return
        val firstPage = (firstPageState.value as? FirstPageState.Content)?.page ?: return
        val appended = appendedPages.value
        val cursor = if (appended.hasLoadedPage) {
            appended.nextCursor
        } else {
            firstPage.nextCursor
        } ?: return

        pageRequestInFlight = true
        appendedPages.update {
            it.copy(
                isLoading = true,
                error = null,
            )
        }

        viewModelScope.launch {
            val result = try {
                runSuspendCatching {
                    flow {
                        emit(surveyRepository.loadActiveSurveyPage(after = cursor))
                    }
                        .retryTransientFailures()
                        .single()
                }
            } finally {
                pageRequestInFlight = false
            }
            result.fold(
                onSuccess = ::appendPage,
                onFailure = ::showAppendError,
            )
        }
    }

    fun retry() {
        if (pageRequestInFlight) return
        appendedPages.value = AppendedPages()
        refreshRequests.update { request -> request + 1L }
    }

    private fun appendPage(page: SurveyPage) {
        appendedPages.update { current ->
            current.copy(
                surveys = (current.surveys + page.surveys)
                    .distinctBy(Survey::id)
                    .toImmutableList(),
                nextCursor = page.nextCursor,
                hasLoadedPage = true,
                isLoading = false,
                error = null,
            )
        }
    }

    private fun showAppendError(error: Throwable) {
        appendedPages.update {
            it.copy(
                isLoading = false,
                error = error.toVotingUiError(VotingUiError.LOAD_SURVEYS_FAILED),
            )
        }
    }
}

private sealed interface FirstPageState {
    data object Loading : FirstPageState
    data class Content(val page: SurveyPage) : FirstPageState
    data class Error(val error: VotingUiError) : FirstPageState
}

private data class AppendedPages(
    val surveys: ImmutableList<Survey> = persistentListOf(),
    val nextCursor: SurveyPageCursor? = null,
    val hasLoadedPage: Boolean = false,
    val isLoading: Boolean = false,
    val error: VotingUiError? = null,
)

private fun FirstPageState.toHomeUiState(
    appended: AppendedPages,
): HomeUiState {
    return when (this) {
        FirstPageState.Loading -> HomeUiState.Loading
        is FirstPageState.Error -> HomeUiState.Error(error)
        is FirstPageState.Content -> {
            val surveys = (page.surveys + appended.surveys)
                .distinctBy(Survey::id)
                .sortedWith(
                    compareBy<Survey>(Survey::expiresAt)
                        .thenByDescending(Survey::createdAt)
                        .thenByDescending(Survey::id)
                )
                .toImmutableList()
            HomeUiState.Content(
                surveys = surveys,
                canLoadMore = if (appended.hasLoadedPage) {
                    appended.nextCursor != null
                } else {
                    page.nextCursor != null
                },
                isLoadingMore = appended.isLoading,
                loadMoreError = appended.error,
            )
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun Flow<HomeUiState>.whileActive(
    clock: Clock,
): Flow<HomeUiState> = transformLatest { receivedState ->
    var state = receivedState.withOnlyActiveSurveys(clock)
    emit(state)

    while (state is HomeUiState.Content && state.surveys.isNotEmpty()) {
        val nextExpiry = state.surveys.minOf(Survey::expiresAt)
        val remainingMillis = (nextExpiry - clock.millis()).coerceAtLeast(1L)
        delay(remainingMillis)

        val updatedState = state.withOnlyActiveSurveys(clock)
        if (updatedState != state) {
            state = updatedState
            emit(state)
        }
    }
}

private fun HomeUiState.withOnlyActiveSurveys(clock: Clock): HomeUiState {
    if (this !is HomeUiState.Content) return this
    return copy(
        surveys = surveys
            .filterNot { survey -> survey.isClosedAt(clock.millis()) }
            .toImmutableList()
    )
}
