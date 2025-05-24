package com.dvote.ui.main.create_survey

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dvote.data.model.SurveyDataItem
import com.dvote.data.repository.SurveyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class CreateSurveyViewModel @Inject constructor(
    private val repository: SurveyRepository
) : ViewModel() {

    val surveyTitle = MutableStateFlow("")
    val surveyDescription = MutableStateFlow("")
    val candidates = MutableStateFlow<List<String>>(emptyList())
    val isMultipleChoice = MutableStateFlow(false)

    fun addCandidate() {
        candidates.value = candidates.value + ""
    }

    fun removeCandidate(index: Int) {
        candidates.value = candidates.value.toMutableList().also { it.removeAt(index) }
    }

    fun updateCandidate(index: Int, new: String) {
        candidates.value = candidates.value.toMutableList().also { it[index] = new }
    }

    fun createSurvey() {
        viewModelScope.launch(Dispatchers.IO) {
            val title = surveyTitle.value
            val description = surveyDescription.value
            val candidateList = candidates.value.filter { it.isNotBlank() }
            val createdAt = LocalDate.now().toString()
            val expirationDate = LocalDate.now().plusDays(30).toString()
            val isMultipleChoice = isMultipleChoice.value

            if (title.isNotBlank() && description.isNotBlank() && candidateList.isNotEmpty()) {
                val item = SurveyDataItem(
                    id = "temp_id",
                    title = title,
                    description = description,
                    createdBy = "temp_user", // This should be fetched from the logged-in user
                    createdAt = createdAt,
                    expirationDate = expirationDate,
                    listOfCandidates = candidateList,
                    isMultipleChoice = isMultipleChoice,
                    isActive = true,
                    totalVotes = 0,
                    totalParticipants = 0
                )

                repository.createSurvey(item)
            } else {
                // Handle error: show a message to the user
            }
        }
    }

}