package com.dvote.ui.main.home

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(

) : ViewModel() {

    private val _surveyListFlow = MutableStateFlow<List<SurveyItemUi>>(emptyList())
    val surveyListFlow = _surveyListFlow.asStateFlow()

    init{
        initTestList()
    }

    private fun initTestList() {
        val testList = listOf(
            SurveyItemUi(
                id = "1",
                title = "Favorite Color",
                description = "What is your favorite color?",
                createdBy = "Alice",
                createdAt = "2023-10-01",
                expirationDate = "2023-10-31",
                isActive = true,
                isProtected = false,
                totalVotes = 100,
                totalParticipants = 50
            ),
            SurveyItemUi(
                id = "2",
                title = "Best Programming Language",
                description = "Which programming language do you prefer?",
                createdBy = "Bob",
                createdAt = "2023-10-02",
                expirationDate = "2023-11-01",
                isActive = true,
                isProtected = true,
                totalVotes = 200,
                totalParticipants = 100
            ),
            SurveyItemUi(
                id = "3",
                title = "Weekend Plans",
                description = "What are your plans for the weekend?",
                createdBy = "Charlie",
                createdAt = "2023-10-03",
                expirationDate = "2023-10-15",
                isActive = false,
                isProtected = false,
                totalVotes = 50,
                totalParticipants = 30
            ),
            SurveyItemUi(
                id = "4",
                title = "Favorite Food",
                description = "What is your favorite food?",
                createdBy = "David",
                createdAt = "2023-10-04",
                expirationDate = "2023-10-20",
                isActive = true,
                isProtected = false,
                totalVotes = 150,
                totalParticipants = 80
            ),
            SurveyItemUi(
                id = "5",
                title = "Travel Destinations",
                description = "Where would you like to travel next?",
                createdBy = "Eve",
                createdAt = "2023-10-05",
                expirationDate = "2023-11-05",
                isActive = true,
                isProtected = true,
                totalVotes = 300,
                totalParticipants = 150
            ),
            SurveyItemUi(
                id = "6",
                title = "Favorite Movie Genre",
                description = "What is your favorite movie genre?",
                createdBy = "Frank",
                createdAt = "2023-10-06",
                expirationDate = "2023-10-25",
                isActive = true,
                isProtected = false,
                totalVotes = 80,
                totalParticipants = 40
            ),
            SurveyItemUi(
                id = "7",
                title = "Best Smartphone Brand",
                description = "Which smartphone brand do you prefer?",
                createdBy = "Grace",
                createdAt = "2023-10-07",
                expirationDate = "2023-11-10",
                isActive = true,
                isProtected = true,
                totalVotes = 120,
                totalParticipants = 60
            )
        )
        _surveyListFlow.value = testList
    }


}