package com.dvote.feature.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.CancellationException

internal class GoogleCredentialClient(
    private val credentialManager: CredentialManager,
) {
    suspend fun requestIdToken(context: Context): Result<String> {
        return try {
            check(BuildConfig.WEB_CLIENT_ID.isNotBlank()) {
                "WEB_CLIENT_ID is not configured."
            }

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(
                    GetSignInWithGoogleOption.Builder(BuildConfig.WEB_CLIENT_ID).build()
                )
                .build()
            val credential = credentialManager.getCredential(
                context = context,
                request = request,
            ).credential

            if (credential !is CustomCredential || !credential.isGoogleIdToken()) {
                error("Unsupported credential type.")
            }

            Result.success(GoogleIdTokenCredential.createFrom(credential.data).idToken)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: NoCredentialException) {
            Result.failure(IllegalStateException("No Google account is available.", error))
        } catch (error: GoogleIdTokenParsingException) {
            Result.failure(IllegalStateException("Google token could not be parsed.", error))
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    private fun CustomCredential.isGoogleIdToken(): Boolean {
        return type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL ||
            type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_SIWG_CREDENTIAL
    }
}
