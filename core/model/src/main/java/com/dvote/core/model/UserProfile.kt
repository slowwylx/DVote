package com.dvote.core.model

import kotlinx.collections.immutable.ImmutableList

data class UserProfile(
    val id: String,
    val displayName: String,
    val createdSurveyIds: ImmutableList<String>,
    val votedSurveyIds: ImmutableList<String>,
)
