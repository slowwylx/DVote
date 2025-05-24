package com.dvote.data.model

data class SurveyDataItem(
    val id: String,
    val title: String,
    val description: String,
    val createdBy: String,
    val createdAt: String,
    val expirationDate: String,
    val isMultipleChoice: Boolean,
    val listOfCandidates: List<String>,
    val isActive: Boolean,
    val totalVotes: Int,
    val totalParticipants: Int
)
