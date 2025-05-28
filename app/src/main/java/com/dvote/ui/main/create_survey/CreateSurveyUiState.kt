package com.dvote.ui.main.create_survey

sealed class CreateSurveyUiState {
    object None : CreateSurveyUiState()
    object Loading : CreateSurveyUiState()
    object Success : CreateSurveyUiState()
    data class Error(val message: String) : CreateSurveyUiState()
}