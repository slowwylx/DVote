package com.dvote.core.model

import kotlinx.collections.immutable.ImmutableList

data class CreateSurveyRequest(
    val operationId: String,
    val title: String,
    val description: String,
    val allowMultipleChoices: Boolean,
    val options: ImmutableList<String>,
    val expiresAt: Long,
)
