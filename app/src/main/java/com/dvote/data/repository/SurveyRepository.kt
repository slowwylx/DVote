package com.dvote.data.repository

import com.dvote.data.model.CreateSurveyDataItem
import com.dvote.data.model.SurveyDataItem
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class SurveyRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {

    suspend fun getSurveys(): List<SurveyDataItem> {
        return firestore.collection("surveys")
            .get()
            .await()
            .documents
            .map { it.toObject(SurveyDataItem::class.java)!! }
    }

    suspend fun getMySurveys(userId: String): List<CreateSurveyDataItem> {
        return firestore.collection("users")
            .whereEqualTo("createdBy", userId)
            .get()
            .await()
            .documents
            .map { it.toObject(CreateSurveyDataItem::class.java)!! }
    }

    fun observeSurveyById(surveyId: String): Flow<SurveyDataItem> = callbackFlow {
        val docRef = firestore.collection("surveys").document(surveyId)
        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            snapshot?.toObject(SurveyDataItem::class.java)
                ?.let { trySend(it).isSuccess }
        }
        awaitClose { listener.remove() }
    }

    suspend fun createSurvey(survey: CreateSurveyDataItem) {
         firestore.collection("surveys").document(survey.id).set(survey).await()
    }

    suspend fun submitSignedVote(
        surveyId: String,
        userId: String,
        choiceId: String,
        timestamp: Long,
        signature: String,
        publicKey: String
    ) {
        val voteData = mapOf(
            "choiceId"  to choiceId,
            "timestamp" to timestamp,
            "signature" to signature,
            "publicKey" to publicKey
        )
        val voteDocId = "${userId}_$choiceId"

        firestore
            .collection("surveys")
            .document(surveyId)
            .collection("voters")
            .document(voteDocId)
            .set(voteData)
            .await()
    }

}