package com.dvote.domain.repository

import com.dvote.core.model.Survey
import com.dvote.core.model.SurveyResult
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.coroutines.flow.Flow

interface SurveyRepository {
    fun observeFirstActiveSurveyPage(
        pageSize: Int = DEFAULT_SURVEY_PAGE_SIZE,
    ): Flow<SurveyPage>

    suspend fun loadActiveSurveyPage(
        after: SurveyPageCursor,
        pageSize: Int = DEFAULT_SURVEY_PAGE_SIZE,
    ): SurveyPage

    /**
     * Returns the latest survey already observed in this app process without performing I/O.
     */
    fun cachedSurvey(surveyId: String): Survey?

    fun observeSurvey(surveyId: String): Flow<Survey?>

    fun observeFinalResult(
        surveyId: String,
        optionIds: ImmutableSet<String>,
    ): Flow<SurveyResult?>
}

data class SurveyPage(
    val surveys: ImmutableList<Survey>,
    val nextCursor: SurveyPageCursor?,
)

data class SurveyPageCursor(
    val expiresAt: Long,
    val createdAt: Long,
    val surveyId: String,
)

const val DEFAULT_SURVEY_PAGE_SIZE = 20
