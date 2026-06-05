package com.dvote.ui.main.home

data class SurveyItemUi(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val creatorName: String = "",
    val createdAt: String = "",
    val expirationDate: String = "",
    val isActive: Boolean = true,
    val totalVotes: Long = 0L,
    val totalParticipants: Long = 0L,
)
