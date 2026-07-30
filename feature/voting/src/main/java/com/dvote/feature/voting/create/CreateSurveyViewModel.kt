package com.dvote.feature.voting.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dvote.core.model.CreateSurveyRequest
import com.dvote.domain.usecase.CreateSurveyUseCase
import com.dvote.feature.voting.VotingUiError
import com.dvote.feature.voting.toVotingUiError
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class CreateSurveyViewModel @Inject constructor(
    private val createSurvey: CreateSurveyUseCase,
    private val clock: Clock,
) : ViewModel() {
    private var pendingRequest: CreateSurveyRequest? = null

    private val _uiState = MutableStateFlow(CreateSurveyUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = Channel<CreateSurveyEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onTitleChanged(value: String) {
        pendingRequest = null
        _uiState.update { it.copy(title = value, error = null) }
    }

    fun onDescriptionChanged(value: String) {
        pendingRequest = null
        _uiState.update { it.copy(description = value, error = null) }
    }

    fun onAllowMultipleChoicesChanged(value: Boolean) {
        pendingRequest = null
        _uiState.update { it.copy(allowMultipleChoices = value) }
    }

    fun onOptionChanged(index: Int, value: String) {
        pendingRequest = null
        _uiState.update { state ->
            state.copy(
                options = state.options.mapIndexed { currentIndex, option ->
                    if (currentIndex == index) value else option
                }.toImmutableList(),
                error = null,
            )
        }
    }

    fun addOption() {
        pendingRequest = null
        _uiState.update { state ->
            if (state.options.size >= MAX_SURVEY_OPTION_COUNT) {
                state
            } else {
                state.copy(options = (state.options + "").toImmutableList())
            }
        }
    }

    fun removeOption(index: Int) {
        pendingRequest = null
        _uiState.update { state ->
            if (state.options.size <= MIN_SURVEY_OPTION_COUNT) {
                state
            } else {
                state.copy(
                    options = state.options
                        .filterIndexed { currentIndex, _ -> currentIndex != index }
                        .toImmutableList()
                )
            }
        }
    }

    fun submit() {
        val state = _uiState.value
        if (state.isSubmitting) return

        _uiState.value = state.copy(
            isSubmitting = true,
            error = null,
        )

        viewModelScope.launch {
            val request = pendingRequest ?: CreateSurveyRequest(
                operationId = UUID.randomUUID().toString(),
                title = state.title,
                description = state.description,
                allowMultipleChoices = state.allowMultipleChoices,
                options = state.options,
                expiresAt = clock.instant().plus(30, ChronoUnit.DAYS).toEpochMilli(),
            ).also { pendingRequest = it }
            createSurvey(request)
                .onSuccess { surveyId ->
                    pendingRequest = null
                    _uiState.update { it.copy(isSubmitting = false) }
                    _effects.send(CreateSurveyEffect.OpenSurvey(surveyId))
                }
                .onFailure { cause ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            error = cause.toVotingUiError(
                                VotingUiError.CREATE_SURVEY_FAILED
                            ),
                        )
                    }
                }
        }
    }
}
