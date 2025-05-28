package com.dvote.ui.main.vote

import com.dvote.data.model.SurveyDataItem

sealed class SurveyViewUiState {
    object Loading : SurveyViewUiState()
    data class Success(val survey: SurveyDataItem) : SurveyViewUiState()
    data class Error(val message: String) : SurveyViewUiState()
}