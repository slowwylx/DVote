package com.dvote.data.firebase.dto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SurveyDocumentTest {
    @Test
    fun exactDocumentMapsToDomain() {
        val survey = validSurvey().toSurveyOrNull("survey-1")

        assertEquals("survey-1", survey?.id)
        assertEquals(listOf("option-1", "option-2"), survey?.options?.map { it.id })
    }

    @Test
    fun malformedSurveyIsRejectedWithoutAffectingValidDocuments() {
        val documents = listOf(
            "survey-1" to validSurvey(),
            "survey-2" to validSurvey(),
            "survey-1" to validSurvey() + ("unexpected" to true),
            "survey-1" to validSurvey(
                options = listOf(
                    option("option-1", "One"),
                    option("option-1", "Duplicate"),
                )
            ),
            "survey-1" to validSurvey(resultsVisibility = "live"),
        )

        val mapped = documents.mapNotNull { (documentId, values) ->
            values.toSurveyOrNull(documentId)
        }

        assertEquals(listOf("survey-1"), mapped.map { it.id })
    }

    @Test
    fun unknownNestedFieldsAndNonFirestoreIntegerTypesAreRejected() {
        assertNull(
            validSurvey(
                options = listOf(
                    option("option-1", "One") + ("unexpected" to true),
                    option("option-2", "Two"),
                )
            ).toSurveyOrNull("survey-1")
        )
        assertNull(
            validSurvey()
                .toMutableMap()
                .apply { this["createdAt"] = 100.0 }
                .toSurveyOrNull("survey-1")
        )
    }

    @Test
    fun nonCanonicalOrOutOfRangeTextIsRejected() {
        assertNull(validSurvey(title = " Survey title").toSurveyOrNull("survey-1"))
        assertNull(validSurvey(description = "short").toSurveyOrNull("survey-1"))
        assertNull(validSurvey(creatorName = "A".repeat(81)).toSurveyOrNull("survey-1"))
        assertNull(validSurvey(expiresAt = 100L).toSurveyOrNull("survey-1"))
    }

    @Test
    fun exactResultMapsOnlyForTheExpectedSurveyAndOptions() {
        val result = validResult().toSurveyResultOrNull(
            expectedSurveyId = "survey-1",
            expectedOptionIds = setOf("option-1", "option-2"),
        )

        assertEquals(1L, result?.totalVotes)
        assertEquals(1L, result?.optionVotes?.get("option-1"))
    }

    @Test
    fun malformedResultIsRejected() {
        val expectedOptions = setOf("option-1", "option-2")

        assertNull(
            validResult(
                optionVotes = mapOf(
                    "option-1" to 2L,
                    "option-2" to 0L,
                )
            ).toSurveyResultOrNull("survey-1", expectedOptions)
        )
        assertNull(
            validResult(surveyId = "survey-2")
                .toSurveyResultOrNull("survey-1", expectedOptions)
        )
        assertNull(
            (validResult() + ("unexpected" to true))
                .toSurveyResultOrNull("survey-1", expectedOptions)
        )
        assertNull(
            validResult(optionVotes = mapOf("option-1" to 1L, "other" to 0L))
                .toSurveyResultOrNull("survey-1", expectedOptions)
        )
        assertNull(
            validResult()
                .toMutableMap()
                .apply { this["totalVotes"] = 1.0 }
                .toSurveyResultOrNull("survey-1", expectedOptions)
        )
        assertTrue(
            validResult().toSurveyResultOrNull(
                expectedSurveyId = "survey-1",
                expectedOptionIds = emptySet(),
            ) == null
        )
    }

    private fun validSurvey(
        title: String = "Survey title",
        description: String = "A sufficiently descriptive survey.",
        creatorName: String = "Alice",
        expiresAt: Long = 200L,
        resultsVisibility: String = "after_close",
        options: List<Map<String, Any?>> = listOf(
            option("option-1", "One"),
            option("option-2", "Two"),
        ),
    ): Map<String, Any?> = mapOf(
        "id" to "survey-1",
        "title" to title,
        "description" to description,
        "creatorId" to "user-1",
        "creatorName" to creatorName,
        "createdAt" to 100L,
        "expiresAt" to expiresAt,
        "isActive" to true,
        "resultsVisibility" to resultsVisibility,
        "allowMultipleChoices" to false,
        "options" to options,
    )

    private fun option(
        id: String,
        title: String,
    ): Map<String, Any?> = mapOf(
        "id" to id,
        "title" to title,
    )

    private fun validResult(
        surveyId: String = "survey-1",
        optionVotes: Map<String, Long> = mapOf(
            "option-1" to 1L,
            "option-2" to 0L,
        ),
    ): Map<String, Any?> = mapOf(
        "surveyId" to surveyId,
        "totalVotes" to 1L,
        "optionVotes" to optionVotes,
    )
}
