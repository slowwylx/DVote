package com.dvote.data.model

data class SurveyDataItem(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val createdBy: String = "",
    val createdAt: String = "",
    val expirationDate: String = "",
    val isActive: Boolean = true,
    val multipleChoice: Boolean = false,
    val listOfCandidates: List<Participant> = emptyList(),
    val totalVotes: Long = 0L
){
    data class Participant(
        val userId: String = "",
        val vote: Long = 0L,
        val name: String = ""
    )
}
