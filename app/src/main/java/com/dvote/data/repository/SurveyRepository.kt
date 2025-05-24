package com.dvote.data.repository

import com.dvote.data.model.SurveyDataItem
import com.google.firebase.firestore.FirebaseFirestore
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

    suspend fun getMySurveys(userId: String): List<SurveyDataItem> {
        return firestore.collection("users")
            .whereEqualTo("createdBy", userId)
            .get()
            .await()
            .documents
            .map { it.toObject(SurveyDataItem::class.java)!! }
    }

    suspend fun getPastSurveys(): List<SurveyDataItem> {
        return firestore.collection("surveys")
            .whereLessThan("expirationDate", System.currentTimeMillis())
            .get()
            .await()
            .documents
            .map { it.toObject(SurveyDataItem::class.java)!! }
    }

    suspend fun createSurvey(survey: SurveyDataItem) {
        firestore.collection("surveys")
            .add(survey)
            .await()
    }

}