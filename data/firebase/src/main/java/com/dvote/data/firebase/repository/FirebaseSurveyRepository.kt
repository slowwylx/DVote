package com.dvote.data.firebase.repository

import android.util.Log
import com.dvote.core.model.Survey
import com.dvote.core.model.SurveyResult
import com.dvote.data.firebase.dto.toSurveyOrNull
import com.dvote.data.firebase.dto.toSurveyResultOrNull
import com.dvote.domain.repository.SurveyRepository
import com.dvote.domain.repository.SurveyPage
import com.dvote.domain.repository.SurveyPageCursor
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.time.Clock
import java.util.LinkedHashMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseSurveyRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val clock: Clock,
) : SurveyRepository {
    private val surveyCache = SurveyMemoryCache()

    override fun observeFirstActiveSurveyPage(
        pageSize: Int,
    ): Flow<SurveyPage> = callbackFlow {
        requireValidPageSize(pageSize)
        val listener = activeSurveysQuery()
            .limit(pageSize.toLong() + LOOK_AHEAD_DOCUMENTS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error.toRepositoryFailure())
                    return@addSnapshotListener
                }
                val page = try {
                    snapshot
                        ?.documents
                        ?.toSurveyPage(pageSize)
                        ?: SurveyPage(
                            surveys = emptyList<Survey>().toImmutableList(),
                            nextCursor = null,
                        )
                } catch (failure: Throwable) {
                    close(failure.toRepositoryFailure())
                    return@addSnapshotListener
                }
                trySend(page)
            }

        awaitClose { listener.remove() }
    }

    override suspend fun loadActiveSurveyPage(
        after: SurveyPageCursor,
        pageSize: Int,
    ): SurveyPage {
        requireValidPageSize(pageSize)
        return runFirebaseCatching {
            activeSurveysQuery()
                .startAfter(
                    after.expiresAt,
                    after.createdAt,
                    after.surveyId,
                )
                .limit(pageSize.toLong() + LOOK_AHEAD_DOCUMENTS)
                .get()
                .await()
                .documents
                .toSurveyPage(pageSize)
        }.getOrThrow()
    }

    private fun activeSurveysQuery(): Query {
        return firestore.collection(SURVEYS)
            .whereEqualTo(RESULTS_VISIBILITY, RESULTS_VISIBILITY_AFTER_CLOSE)
            .whereEqualTo(IS_ACTIVE, true)
            .whereGreaterThan(EXPIRES_AT, clock.millis())
            .orderBy(EXPIRES_AT, Query.Direction.ASCENDING)
            .orderBy(CREATED_AT, Query.Direction.DESCENDING)
            .orderBy(FieldPath.documentId(), Query.Direction.DESCENDING)
    }

    override fun cachedSurvey(surveyId: String): Survey? = surveyCache[surveyId]

    override fun observeSurvey(surveyId: String): Flow<Survey?> = callbackFlow {
        cachedSurvey(surveyId)?.let { cached ->
            trySend(cached)
        }

        val listener = firestore.collection(SURVEYS)
            .document(surveyId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error.toRepositoryFailure())
                    return@addSnapshotListener
                }
                when {
                    snapshot == null || !snapshot.exists() -> {
                        surveyCache.remove(surveyId)
                        trySend(null)
                    }
                    else -> {
                        val survey = snapshot.toSurveyOrNull()
                        if (survey == null) {
                            close(invalidRepositoryData())
                        } else {
                            surveyCache.put(survey)
                            trySend(survey)
                        }
                    }
                }
            }

        awaitClose { listener.remove() }
    }.distinctUntilChanged()

    override fun observeFinalResult(
        surveyId: String,
        optionIds: ImmutableSet<String>,
    ): Flow<SurveyResult?> = callbackFlow {
        val listener = firestore.collection(SURVEYS)
            .document(surveyId)
            .collection(RESULTS)
            .document(FINAL_RESULT)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error.toRepositoryFailure())
                    return@addSnapshotListener
                }
                when {
                    snapshot == null || !snapshot.exists() -> trySend(null)
                    else -> {
                        val result = snapshot.toResultOrNull(optionIds)
                        if (result == null) {
                            close(invalidRepositoryData())
                        } else {
                            trySend(result)
                        }
                    }
                }
            }

        awaitClose { listener.remove() }
    }

    private fun DocumentSnapshot.toSurveyOrNull(): Survey? {
        val survey = data?.toSurveyOrNull(id)
        if (survey == null) {
            Log.w(TAG, "Skipping malformed survey document $id.")
        }
        return survey
    }

    private fun DocumentSnapshot.toResultOrNull(
        optionIds: ImmutableSet<String>,
    ): SurveyResult? {
        val surveyId = reference.parent.parent?.id ?: return null
        val result = data?.toSurveyResultOrNull(
            expectedSurveyId = surveyId,
            expectedOptionIds = optionIds,
        )
        if (result == null) {
            Log.w(TAG, "Skipping malformed final result for survey $surveyId.")
        }
        return result
    }

    private fun List<DocumentSnapshot>.toSurveyPage(pageSize: Int): SurveyPage {
        val surveys = take(pageSize)
            .map { snapshot ->
                snapshot.toSurveyOrNull() ?: throw invalidRepositoryData()
            }
            .toImmutableList()
        surveyCache.putAll(surveys)
        val nextCursor = if (size > pageSize) {
            surveys.lastOrNull()?.toPageCursor()
        } else {
            null
        }
        return SurveyPage(
            surveys = surveys,
            nextCursor = nextCursor,
        )
    }

    private fun requireValidPageSize(pageSize: Int) {
        require(pageSize in 1..MAX_PAGE_SIZE) {
            "Page size must be between 1 and $MAX_PAGE_SIZE."
        }
    }

    private fun Survey.toPageCursor() = SurveyPageCursor(
        expiresAt = expiresAt,
        createdAt = createdAt,
        surveyId = id,
    )

    private companion object {
        const val TAG = "DVoteFirestore"
        const val LOOK_AHEAD_DOCUMENTS = 1L
        const val MAX_PAGE_SIZE = 50
        const val SURVEYS = "surveys"
        const val RESULTS = "results"
        const val FINAL_RESULT = "final"
        const val RESULTS_VISIBILITY = "resultsVisibility"
        const val RESULTS_VISIBILITY_AFTER_CLOSE = "after_close"
        const val IS_ACTIVE = "isActive"
        const val EXPIRES_AT = "expiresAt"
        const val CREATED_AT = "createdAt"
    }
}

internal class SurveyMemoryCache(
    private val maxSize: Int = DEFAULT_MAX_SIZE,
) {
    init {
        require(maxSize > 0)
    }

    private val surveys = LinkedHashMap<String, Survey>(
        maxSize,
        LOAD_FACTOR,
        true,
    )

    @Synchronized
    operator fun get(surveyId: String): Survey? = surveys[surveyId]

    @Synchronized
    fun put(survey: Survey) {
        surveys[survey.id] = survey
        while (surveys.size > maxSize) {
            val oldestId = surveys.entries.iterator().next().key
            surveys.remove(oldestId)
        }
    }

    fun putAll(values: Iterable<Survey>) {
        values.forEach(::put)
    }

    @Synchronized
    fun remove(surveyId: String) {
        surveys.remove(surveyId)
    }

    private companion object {
        const val DEFAULT_MAX_SIZE = 100
        const val LOAD_FACTOR = 0.75f
    }
}
