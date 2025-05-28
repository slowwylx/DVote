package com.dvote.domain

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GoogleAuthUseCase @Inject constructor(
    private val provideGoogleSignInRequest: GetCredentialRequest,
    private val provideCredentialManager: CredentialManager,
) {

    suspend fun invoke(context: Context): AuthResult {

        return try {
            withContext(Dispatchers.IO) {
                val googleAuthRequest = provideGoogleSignInRequest
                val credentialManager = provideCredentialManager
                val logInResponse = credentialManager.getCredential(
                    context = context,
                    request = googleAuthRequest
                )
                handleGoogleLoginResponse(logInResponse)
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            AuthResult.Error(ex.localizedMessage)
        }
    }

    private fun handleGoogleLoginResponse(response: GetCredentialResponse): AuthResult {
        val credential = response.credential

        return when (credential) {
            is CustomCredential -> {
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    try {
                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        val credentialResult = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                        AuthResult.Success(credentialResult)
                    } catch (e: GoogleIdTokenParsingException) {
                        e.printStackTrace()
                        AuthResult.Error(e.localizedMessage)
                    }
                } else {
                    AuthResult.Error("something_went_wrong")
                }
            }

            else -> {
                AuthResult.Error("something_went_wrong")
            }
        }
    }


    sealed class AuthResult {
        data class Success(val authCredential: AuthCredential) : AuthResult()
        data class Error(val message: String?) : AuthResult()
    }
}