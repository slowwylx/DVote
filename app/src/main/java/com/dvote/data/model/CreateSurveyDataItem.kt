package com.dvote.data.model

data class CreateSurveyDataItem(
    val id: String,
    val title: String,
    val description: String,
    val creatorName: String,
    val creatorId: String,
    val createdAt: String,
    val expirationDate: String,
    val isMultipleChoice: Boolean,
    val listOfCandidates: List<Participant>,
){
    data class Participant(
        val userId: String = "",
        val vote: Long = 0L,
        val name: String = ""
    )
}
