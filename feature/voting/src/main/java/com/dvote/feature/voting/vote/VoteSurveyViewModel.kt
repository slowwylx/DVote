package com.dvote.feature.voting.vote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dvote.core.model.Survey
import com.dvote.core.model.SurveyResult
import com.dvote.core.model.VoteReceipt
import com.dvote.core.model.VoteSubmission
import com.dvote.core.model.isClosedAt
import com.dvote.domain.repository.SurveyRepository
import com.dvote.domain.repository.VotingRepository
import com.dvote.domain.usecase.SubmitVoteUseCase
import com.dvote.feature.voting.VotingUiError
import com.dvote.feature.voting.retryTransientFailures
import com.dvote.feature.voting.toVotingUiError
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.util.UUID
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = VoteSurveyViewModel.Factory::class)
class VoteSurveyViewModel @AssistedInject constructor(
    @Assisted surveyId: String,
    surveyRepository: SurveyRepository,
    votingRepository: VotingRepository,
    private val submitVote: SubmitVoteUseCase,
    private val clock: Clock,
) : ViewModel() {
    private val interactionState = MutableStateFlow(VoteInteractionState())
    private val initialUiState = surveyRepository.cachedSurvey(surveyId)
        ?.let { survey ->
            val isClosed = survey.isClosedAt(clock.millis())
            VoteSurveyUiState(
                survey = survey,
                isLoading = false,
                isSurveyClosed = isClosed,
                isVoteStatusLoading = true,
                isFinalResultLoading = isClosed,
            )
        }
        ?: VoteSurveyUiState()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val surveyState = surveyRepository.observeSurvey(surveyId)
        .retryTransientFailures()
        .transformLatest { survey ->
            if (survey == null) {
                emit(SurveyLoadState.NotFound)
                return@transformLatest
            }

            val now = clock.millis()
            val isClosed = survey.isClosedAt(now)
            if (!isClosed) {
                emit(SurveyLoadState.Content(survey, isClosed = false))

                val remainingMillis = survey.expiresAt - now
                if (remainingMillis > 0) {
                    delay(remainingMillis)
                }
            }

            emit(
                SurveyLoadState.Content(
                    survey = survey,
                    isClosed = true,
                    isFinalResultLoading = true,
                )
            )
            if (survey.isActive) {
                delay(FINAL_RESULT_VISIBILITY_GRACE_MILLIS)
            }
            emitAll(
                surveyRepository.observeFinalResult(
                    surveyId = surveyId,
                    optionIds = survey.options
                        .map { option -> option.id }
                        .toImmutableSet(),
                )
                    .retryTransientFailures()
                    .map { result ->
                        SurveyLoadState.Content(
                            survey = survey,
                            isClosed = true,
                            finalResult = result,
                            finalResultError = if (result == null) {
                                VotingUiError.FINAL_RESULT_UNAVAILABLE
                            } else {
                                null
                            },
                        )
                    }
                    .catch { cause ->
                        emit(
                            SurveyLoadState.Content(
                                survey = survey,
                                isClosed = true,
                                finalResultError = cause.toVotingUiError(
                                    VotingUiError.LOAD_FINAL_RESULT_FAILED
                                ),
                            )
                        )
                    }
            )
        }
        .catch { cause ->
            emit(
                SurveyLoadState.Error(
                    cause.toVotingUiError(VotingUiError.LOAD_SURVEY_FAILED)
                )
            )
        }

    private val voteStatusState = votingRepository.observeHasVoted(surveyId)
        .retryTransientFailures()
        .map<Boolean, VoteStatusState>(VoteStatusState::Known)
        .onStart { emit(VoteStatusState.Loading) }
        .catch { cause ->
            emit(
                VoteStatusState.Error(
                    cause.toVotingUiError(VotingUiError.CHECK_VOTE_STATUS_FAILED)
                )
            )
        }

    val uiState = combine(
        surveyState,
        voteStatusState,
        interactionState,
    ) { loadState, voteStatus, interaction ->
        when (loadState) {
            SurveyLoadState.NotFound -> VoteSurveyUiState(
                isLoading = false,
                error = VotingUiError.SURVEY_NOT_FOUND,
            )
            is SurveyLoadState.Error -> VoteSurveyUiState(
                isLoading = false,
                error = loadState.error,
            )
            is SurveyLoadState.Content -> {
                val hasVoted = interaction.hasAcceptedVote ||
                    (voteStatus as? VoteStatusState.Known)?.hasVoted == true
                val isVoteStatusLoading = voteStatus is VoteStatusState.Loading
                val voteStatusError = (voteStatus as? VoteStatusState.Error)?.error
                val canSelect = !loadState.isClosed &&
                    !hasVoted &&
                    !isVoteStatusLoading &&
                    voteStatusError == null
                val validOptionIds = loadState.survey.options
                    .mapTo(mutableSetOf()) { it.id }
                VoteSurveyUiState(
                    survey = loadState.survey,
                    selectedOptionIds = if (canSelect) {
                        interaction.selectedOptionIds
                            .filter { it in validOptionIds }
                            .toImmutableSet()
                    } else {
                        persistentSetOf()
                    },
                    receipt = interaction.receipt,
                    isLoading = false,
                    isSubmitting = interaction.isSubmitting,
                    isSurveyClosed = loadState.isClosed,
                    hasVoted = hasVoted,
                    isVoteStatusLoading = isVoteStatusLoading,
                    voteStatusError = voteStatusError,
                    finalResult = loadState.finalResult,
                    isFinalResultLoading = loadState.isFinalResultLoading,
                    finalResultError = loadState.finalResultError,
                    error = interaction.error,
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = initialUiState,
    )

    fun toggleOption(optionId: String) {
        val state = uiState.value
        val survey = state.survey ?: return
        if (
            state.isSurveyClosed ||
            state.hasVoted ||
            state.isVoteStatusLoading ||
            state.voteStatusError != null ||
            state.isSubmitting ||
            survey.options.none { it.id == optionId }
        ) {
            return
        }

        interactionState.update { interaction ->
            val updatedSelection = if (survey.allowMultipleChoices) {
                interaction.selectedOptionIds.toMutableSet().apply {
                    if (!add(optionId)) remove(optionId)
                }.toImmutableSet()
            } else {
                persistentSetOf(optionId)
            }
            interaction.copy(
                selectedOptionIds = updatedSelection,
                pendingSubmission = null,
                error = null,
            )
        }
    }

    fun submit() {
        val state = uiState.value
        val survey = state.survey ?: return
        if (
            interactionState.value.isSubmitting ||
            state.isSurveyClosed ||
            state.hasVoted ||
            state.isVoteStatusLoading ||
            state.voteStatusError != null ||
            state.selectedOptionIds.isEmpty()
        ) {
            return
        }

        interactionState.update {
            it.copy(
                isSubmitting = true,
                error = null,
            )
        }
        viewModelScope.launch {
            val submission = interactionState.value.pendingSubmission
                ?: submitVote.prepareSubmission(
                    survey = survey,
                    selectedOptionIds = state.selectedOptionIds,
                    operationId = UUID.randomUUID().toString(),
                ).getOrElse { cause ->
                    interactionState.update {
                        it.copy(
                            isSubmitting = false,
                            error = cause.toVotingUiError(
                                VotingUiError.PREPARE_VOTE_FAILED
                            ),
                        )
                    }
                    return@launch
                }.also { prepared ->
                    interactionState.update { it.copy(pendingSubmission = prepared) }
                }

            submitVote.submit(submission)
                .onSuccess { receipt ->
                    interactionState.update {
                        it.copy(
                            selectedOptionIds = persistentSetOf(),
                            receipt = receipt,
                            pendingSubmission = null,
                            isSubmitting = false,
                            hasAcceptedVote = true,
                        )
                    }
                }
                .onFailure { cause ->
                    interactionState.update {
                        it.copy(
                            isSubmitting = false,
                            error = cause.toVotingUiError(
                                VotingUiError.SUBMIT_VOTE_FAILED
                            ),
                        )
                    }
                }
        }
    }

    fun dismissReceipt() {
        interactionState.update { it.copy(receipt = null) }
    }

    @AssistedFactory
    interface Factory {
        fun create(surveyId: String): VoteSurveyViewModel
    }

    private sealed interface SurveyLoadState {
        data object NotFound : SurveyLoadState

        data class Content(
            val survey: Survey,
            val isClosed: Boolean,
            val finalResult: SurveyResult? = null,
            val isFinalResultLoading: Boolean = false,
            val finalResultError: VotingUiError? = null,
        ) : SurveyLoadState

        data class Error(val error: VotingUiError) : SurveyLoadState
    }

    private sealed interface VoteStatusState {
        data object Loading : VoteStatusState
        data class Known(val hasVoted: Boolean) : VoteStatusState
        data class Error(val error: VotingUiError) : VoteStatusState
    }

    private data class VoteInteractionState(
        val selectedOptionIds: ImmutableSet<String> = persistentSetOf(),
        val pendingSubmission: VoteSubmission? = null,
        val receipt: VoteReceipt? = null,
        val isSubmitting: Boolean = false,
        val hasAcceptedVote: Boolean = false,
        val error: VotingUiError? = null,
    )

    private companion object {
        const val FINAL_RESULT_VISIBILITY_GRACE_MILLIS = 1_000L
    }
}
