package com.dvote.ui.main.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dvote.data.repository.SurveyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val surveyRepository: SurveyRepository,
) : ViewModel() {

    private val _surveyListFlow = MutableStateFlow<List<SurveyItemUi>>(emptyList())
    val surveyListFlow = _surveyListFlow.asStateFlow()

    init{
        getSurveyList()
    }

    private fun getSurveyList() {
        viewModelScope.launch(Dispatchers.IO) {
            val surveyList = surveyRepository.getSurveys()
            val surveyItemList = surveyList.map { survey ->
                SurveyItemUi(
                    id = survey.id,
                    title = survey.title,
                    description = survey.description,
                    createdBy = survey.createdBy,
                    createdAt = survey.createdAt,
                    expirationDate = survey.expirationDate,
                    isActive = survey.isActive,
                    totalVotes = survey.totalVotes,
                    totalParticipants = survey.totalVotes
                )
            }
            _surveyListFlow.value = surveyItemList
        }
    }

}