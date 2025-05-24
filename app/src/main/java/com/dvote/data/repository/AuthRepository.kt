package com.dvote.data.repository

import com.dvote.domain.security.KeyStoreService
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.security.KeyPair
import javax.inject.Inject

class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val keyStoreService: KeyStoreService
) {

    suspend fun loginWithCredentials(credential: AuthCredential): AuthResult? {
        return try {
            val authResult = auth.signInWithCredential(credential).await()
            if (authResult.user != null) {
                AuthResult.Success(authResult.user)
            } else {
                AuthResult.Error("Missing user")
            }
        } catch (e: Exception) {
            if (e is FirebaseAuthUserCollisionException) {
                AuthResult.ManualSignUp(e.email)
            } else {
                AuthResult.Error(e.localizedMessage ?: "Unknown error")
            }
        }
    }

    suspend fun initUserData() {
        val userName = auth.currentUser?.displayName
        val userId = auth.currentUser?.uid ?: return
        val pubKeyBytes = getKeyPair().public.encoded
        val pubKeyBase64 = android.util.Base64.encodeToString(pubKeyBytes, android.util.Base64.NO_WRAP)
        firestore.collection("users").document(userId).set(
            hashMapOf(
                "displayName" to userName,
                "publicKey64" to pubKeyBase64,
            )
        ).await()
    }

    fun getKeyPair(): KeyPair {
        return keyStoreService.generateKeyPairIfNeeded()
    }

    fun logOut() {
        auth.signOut()
    }

    sealed class AuthResult {
        data class Success(val user: FirebaseUser?) : AuthResult()
        data class ManualSignUp(val userEmail: String?) : AuthResult()
        data class Error(val message: String) : AuthResult()
    }

}