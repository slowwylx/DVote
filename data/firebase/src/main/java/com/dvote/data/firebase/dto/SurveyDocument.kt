package com.dvote.data.firebase.dto

import com.dvote.core.model.Survey
import com.dvote.core.model.SurveyOption
import com.dvote.core.model.SurveyResult
import java.text.Normalizer
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap

internal fun Map<String, Any?>.toSurveyOrNull(
    expectedSurveyId: String,
): Survey? {
    if (!hasExactFields(SURVEY_FIELDS)) return null

    val id = this["id"] as? String ?: return null
    val title = this["title"] as? String ?: return null
    val description = this["description"] as? String ?: return null
    val creatorId = this["creatorId"] as? String ?: return null
    val creatorName = this["creatorName"] as? String ?: return null
    val createdAt = this["createdAt"] as? Long ?: return null
    val expiresAt = this["expiresAt"] as? Long ?: return null
    val isActive = this["isActive"] as? Boolean ?: return null
    val resultsVisibility = this["resultsVisibility"] as? String ?: return null
    val allowMultipleChoices = this["allowMultipleChoices"] as? Boolean ?: return null
    val rawOptions = this["options"] as? List<*> ?: return null
    val options = rawOptions.map { rawOption ->
        (rawOption as? Map<*, *>)?.toOptionOrNull() ?: return null
    }

    if (
        id != expectedSurveyId ||
        !id.isDocumentId() ||
        !(title inNormalizedRange TITLE_RANGE) ||
        !(description inNormalizedRange DESCRIPTION_RANGE) ||
        !creatorId.isDocumentId() ||
        !(creatorName inNormalizedRange CREATOR_NAME_RANGE) ||
        createdAt < 0L ||
        expiresAt <= createdAt ||
        resultsVisibility != RESULTS_VISIBILITY_AFTER_CLOSE ||
        options.size !in OPTION_COUNT_RANGE ||
        options.map(SurveyOption::id).distinct().size != options.size
    ) {
        return null
    }

    return Survey(
        id = id,
        title = title,
        description = description,
        creatorId = creatorId,
        creatorName = creatorName,
        createdAt = createdAt,
        expiresAt = expiresAt,
        isActive = isActive,
        allowMultipleChoices = allowMultipleChoices,
        options = options.toImmutableList(),
    )
}

internal fun Map<String, Any?>.toSurveyResultOrNull(
    expectedSurveyId: String,
    expectedOptionIds: Set<String>,
): SurveyResult? {
    if (!hasExactFields(RESULT_FIELDS)) return null

    val surveyId = this["surveyId"] as? String ?: return null
    val totalVotes = this["totalVotes"] as? Long ?: return null
    val rawOptionVotes = this["optionVotes"] as? Map<*, *> ?: return null
    if (
        surveyId != expectedSurveyId ||
        totalVotes < 0L ||
        expectedOptionIds.size !in OPTION_COUNT_RANGE ||
        rawOptionVotes.size != expectedOptionIds.size
    ) {
        return null
    }

    val optionVotes = buildMap {
        rawOptionVotes.forEach { (rawOptionId, rawVotes) ->
            val optionId = rawOptionId as? String ?: return null
            val votes = rawVotes as? Long ?: return null
            if (
                optionId !in expectedOptionIds ||
                !optionId.isDocumentId() ||
                votes !in 0L..totalVotes
            ) {
                return null
            }
            put(optionId, votes)
        }
    }
    if (optionVotes.keys != expectedOptionIds) return null

    return SurveyResult(
        totalVotes = totalVotes,
        optionVotes = optionVotes.toImmutableMap(),
    )
}

private fun Map<*, *>.toOptionOrNull(): SurveyOption? {
    if (!hasExactFields(OPTION_FIELDS)) return null
    val id = this["id"] as? String ?: return null
    val title = this["title"] as? String ?: return null
    return if (id.isDocumentId() && (title inNormalizedRange OPTION_TITLE_RANGE)) {
        SurveyOption(id = id, title = title)
    } else {
        null
    }
}

private fun Map<*, *>.hasExactFields(expectedFields: Set<String>): Boolean {
    return size == expectedFields.size &&
        keys.all { field -> field is String && field in expectedFields }
}

private fun String.isDocumentId(): Boolean = matches(DOCUMENT_ID_PATTERN)

private infix fun String.inNormalizedRange(range: IntRange): Boolean {
    return length in range &&
        this == trim() &&
        this == Normalizer.normalize(this, Normalizer.Form.NFC)
}

private val SURVEY_FIELDS = setOf(
    "id",
    "title",
    "description",
    "creatorId",
    "creatorName",
    "createdAt",
    "expiresAt",
    "isActive",
    "resultsVisibility",
    "allowMultipleChoices",
    "options",
)
private val OPTION_FIELDS = setOf("id", "title")
private val RESULT_FIELDS = setOf("surveyId", "totalVotes", "optionVotes")
private val DOCUMENT_ID_PATTERN = Regex("^[A-Za-z0-9_-]{1,128}$")
private val TITLE_RANGE = 3..80
private val DESCRIPTION_RANGE = 10..500
private val CREATOR_NAME_RANGE = 1..80
private val OPTION_TITLE_RANGE = 1..80
private val OPTION_COUNT_RANGE = 2..10
private const val RESULTS_VISIBILITY_AFTER_CLOSE = "after_close"
