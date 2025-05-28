package com.dvote.ui.main.vote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dvote.data.repository.SurveyRepository
import com.dvote.domain.PlaceVoteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject


@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class VoteSurveyViewModel @Inject constructor(
    private val surveyRepository: SurveyRepository,
    private val votingUseCase: PlaceVoteUseCase
) : ViewModel() {

    private val surveyIdFlow = MutableStateFlow("")

    private val _uiState = MutableStateFlow<SurveyViewUiState>(SurveyViewUiState.Loading)
    val uiState: StateFlow<SurveyViewUiState> = _uiState.asStateFlow()

    private val _selectedOptions = MutableStateFlow<Set<String>>(emptySet())
    val selectedOptions = _selectedOptions.asStateFlow()

    init {
        surveyIdFlow
            .filter { it.isNotEmpty() }
            .flatMapLatest { id ->
                surveyRepository
                    .observeSurveyById(id)
                    .catch { e ->
                        _uiState.value = SurveyViewUiState.Error(e.localizedMessage ?: "Unknown error")
                    }
            }
            .onEach { survey ->
                _uiState.value = SurveyViewUiState.Success(survey)
                _selectedOptions.value = emptySet()
            }
            .launchIn(viewModelScope)
    }


    fun onOptionToggled(optionId: String) {
        when (val state = _uiState.value) {
            is SurveyViewUiState.Success -> {
                val current = _selectedOptions.value.toMutableSet()
                if (state.survey.multipleChoice) {
                    if (!current.add(optionId)) current.remove(optionId)
                } else {
                    current.clear()
                    current.add(optionId)
                }
                _selectedOptions.value = current
            }
            else -> Unit
        }
    }

    fun setSurveyId(surveyId: String){
        surveyIdFlow.value = surveyId
    }

    fun submitVote() = viewModelScope.launch(Dispatchers.IO) {
        val state = uiState.value
        if (state is SurveyViewUiState.Success && selectedOptions.value.isNotEmpty()) {
            votingUseCase(state.survey.id, selectedOptions.value)
            _selectedOptions.value = emptySet()
        }
    }
}
