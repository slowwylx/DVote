package com.dvote.ui.main.create_survey

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dvote.data.model.CreateSurveyDataItem
import com.dvote.data.repository.ProfileRepository
import com.dvote.data.repository.SurveyRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CreateSurveyViewModel @Inject constructor(
    private val repository: SurveyRepository,
    private val profileRepository: ProfileRepository,
    private val firebaseAuth: FirebaseAuth,
) : ViewModel() {

    private val _uiState = MutableStateFlow<CreateSurveyUiState>(CreateSurveyUiState.None)
    val uiState = _uiState.asStateFlow()

    val surveyTitle = MutableStateFlow("")
    val surveyDescription = MutableStateFlow("")
    val candidates = MutableStateFlow<List<Map<String, String>>>(emptyList())
    val isMultipleChoice = MutableStateFlow(false)


    fun addCandidate() {
        val newCandidate = mapOf(
            "id" to UUID.randomUUID().toString(),
            "name" to ""
        )
        candidates.value = candidates.value + newCandidate
    }

    fun removeCandidate(index: Int) {
        candidates.value = candidates.value
            .toMutableList()
            .also { it.removeAt(index) }
    }

    fun updateCandidate(index: Int, newName: String) {
        candidates.value = candidates.value.mapIndexed { i, candidate ->
            if (i == index) {
                mapOf(
                    "id" to (candidate["id"]!!),
                    "name" to newName
                )
            } else candidate
        }
    }

    fun createSurvey() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = CreateSurveyUiState.Loading

            val title = surveyTitle.value
            val description = surveyDescription.value
            val validCandidates = candidates.value.filter { it["name"]?.isNotBlank() == true }
            val createdAt = LocalDate.now().toString()
            val expirationDate = LocalDate.now().plusDays(30).toString()
            val isMultipleChoice = isMultipleChoice.value
            val userName = firebaseAuth.currentUser?.displayName ?: "Anonym User"
            val userId = firebaseAuth.currentUser?.uid ?: return@launch

            if (title.isNotBlank() && description.isNotBlank() && validCandidates.isNotEmpty()) {
                val item = CreateSurveyDataItem(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    description = description,
                    creatorName = userName,
                    creatorId = userId,
                    createdAt = createdAt,
                    expirationDate = expirationDate,
                    listOfCandidates = validCandidates.map { candidate ->
                        CreateSurveyDataItem.Participant(
                            userId = candidate["id"] ?: UUID.randomUUID().toString(),
                            name = candidate["name"] ?: ""
                        )
                    },
                    isMultipleChoice = isMultipleChoice,
                )

                val surveyId = repository.createSurvey(item)
                profileRepository.addUserCreatedSurvey(item.id)
                _uiState.value = CreateSurveyUiState.Success
            } else {
                _uiState.value = CreateSurveyUiState.Error("Please fill in all fields and add candidates.")
            }
        }
    }

}