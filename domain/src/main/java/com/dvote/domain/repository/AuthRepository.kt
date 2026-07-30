package com.dvote.domain.repository

import com.dvote.core.model.AuthRepairReason
import com.dvote.core.model.AuthState
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val authState: Flow<AuthState>

    fun currentUserId(): String?

    suspend fun signInWithGoogleIdToken(idToken: String): Result<Unit>

    suspend fun repairProfile(reason: AuthRepairReason): Result<Unit>

    suspend fun signOut(): Result<Unit>
}
