package com.dvote.data.repository

import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
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

    fun logOut() {
        auth.signOut()
    }

    sealed class AuthResult {
        data class Success(val user: FirebaseUser?) : AuthResult()
        data class ManualSignUp(val userEmail: String?) : AuthResult()
        data class Error(val message: String) : AuthResult()
    }

}