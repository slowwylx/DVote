package com.dvote.core.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf

data class Survey(
    val id: String,
    val title: String,
    val description: String,
    val creatorId: String,
    val creatorName: String,
    val createdAt: Long,
    val expiresAt: Long,
    val isActive: Boolean,
    val allowMultipleChoices: Boolean,
    val options: ImmutableList<SurveyOption>,
)

data class SurveyOption(
    val id: String,
    val title: String,
)

data class SurveyResult(
    val totalVotes: Long,
    val optionVotes: ImmutableMap<String, Long>,
) {
    companion object {
        val Empty = SurveyResult(
            totalVotes = 0L,
            optionVotes = persistentMapOf(),
        )
    }
}

fun Survey.isClosedAt(timestampMillis: Long): Boolean {
    return !isActive || expiresAt <= timestampMillis
}
