package com.dvote.data.firebase.repository

import com.dvote.core.model.Survey
import com.dvote.core.model.SurveyOption
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SurveyMemoryCacheTest {

    @Test
    fun keepsLatestSurveySnapshotByStableId() {
        val cache = SurveyMemoryCache()
        val initial = survey(title = "Initial")
        val updated = survey(title = "Updated")

        cache.put(initial)
        cache.put(updated)

        assertEquals(updated, cache[SurveyId])
    }

    @Test
    fun removesSnapshotWhenRemoteDocumentDisappears() {
        val cache = SurveyMemoryCache()
        cache.put(survey(title = "Survey"))

        cache.remove(SurveyId)

        assertNull(cache[SurveyId])
    }

    @Test
    fun evictsTheLeastRecentlyUsedSurveyAtCapacity() {
        val cache = SurveyMemoryCache(maxSize = 2)
        cache.put(survey(id = "survey-1", title = "One"))
        cache.put(survey(id = "survey-2", title = "Two"))
        cache["survey-1"]

        cache.put(survey(id = "survey-3", title = "Three"))

        assertEquals("One", cache["survey-1"]?.title)
        assertNull(cache["survey-2"])
        assertEquals("Three", cache["survey-3"]?.title)
    }

    private fun survey(
        id: String = SurveyId,
        title: String,
    ) = Survey(
        id = id,
        title = title,
        description = "Description",
        creatorId = "creator-1",
        creatorName = "Creator",
        createdAt = 1L,
        expiresAt = 2L,
        isActive = true,
        allowMultipleChoices = false,
        options = persistentListOf(
            SurveyOption(id = "option-1", title = "Option"),
        ),
    )

    private companion object {
        const val SurveyId = "survey-1"
    }
}
