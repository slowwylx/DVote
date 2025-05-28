package com.dvote.data.repository

import com.dvote.domain.security.KeyStoreService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.security.KeyPair
import javax.inject.Inject

class ProfileRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val keyStoreService: KeyStoreService
) {

    suspend fun initUserData() {
        val userName = auth.currentUser?.displayName
        val userId = auth.currentUser?.uid ?: return
        val pubKeyBytes = getKeyPair().public.encoded
        val pubKeyBase64 =
            android.util.Base64.encodeToString(pubKeyBytes, android.util.Base64.NO_WRAP)
        firestore.collection("users").document(userId).set(
            hashMapOf(
                "displayName" to userName,
                "publicKey64" to pubKeyBase64,
            )
        ).await()
    }

    suspend fun addUserCreatedSurvey(surveyId: String) {
        val userId = auth.currentUser?.uid ?: return
        firestore.collection("users").document(userId).update(
            "createdSurveys", FieldValue.arrayUnion(surveyId)
        ).await()
    }

    suspend fun addUserVotedSurvey(surveyId: String) {
        val userId = auth.currentUser?.uid ?: return
        firestore.collection("users").document(userId).update(
            "participatedSurveys", FieldValue.arrayUnion(surveyId)
        ).await()
    }

    fun getKeyPair(): KeyPair {
        return keyStoreService.generateKeyPairIfNeeded()
    }

}