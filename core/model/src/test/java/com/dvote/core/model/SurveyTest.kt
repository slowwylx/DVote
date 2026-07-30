package com.dvote.core.model

import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SurveyTest {
    @Test
    fun activeSurveyClosesAtTheExactExpiryBoundary() {
        val survey = survey(isActive = true, expiresAt = 1_000L)

        assertFalse(survey.isClosedAt(999L))
        assertTrue(survey.isClosedAt(1_000L))
        assertTrue(survey.isClosedAt(1_001L))
    }

    @Test
    fun inactiveSurveyIsClosedBeforeItsExpiry() {
        assertTrue(
            survey(
                isActive = false,
                expiresAt = 10_000L,
            ).isClosedAt(1_000L)
        )
    }

    private fun survey(
        isActive: Boolean,
        expiresAt: Long,
    ) = Survey(
        id = "survey-1",
        title = "Survey",
        description = "Description",
        creatorId = "creator-1",
        creatorName = "Creator",
        createdAt = 1L,
        expiresAt = expiresAt,
        isActive = isActive,
        allowMultipleChoices = false,
        options = persistentListOf(
            SurveyOption("option-1", "One"),
            SurveyOption("option-2", "Two"),
        ),
    )
}
