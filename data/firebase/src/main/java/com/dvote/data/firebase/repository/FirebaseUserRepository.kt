package com.dvote.data.firebase.repository

import com.dvote.core.model.AuthState
import com.dvote.core.model.UserProfile
import com.dvote.data.firebase.dto.toUserProfileOrNull
import com.dvote.domain.repository.AuthRepository
import com.dvote.domain.repository.UserRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseUserRepository @Inject constructor(
    private val authRepository: AuthRepository,
    private val firestore: FirebaseFirestore,
) : UserRepository {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeCurrentUser(): Flow<UserProfile?> {
        return authRepository.authState
            .flatMapLatest { authState ->
                when (authState) {
                    AuthState.Checking -> emptyFlow()
                    AuthState.SignedOut -> flowOf(null)
                    is AuthState.RepairRequired -> flowOf(null)
                    is AuthState.SignedIn -> observeUser(authState.userId)
                }
            }
            .distinctUntilChanged()
    }

    private fun observeUser(userId: String): Flow<UserProfile?> = callbackFlow {
        val listener = firestore.collection(USERS)
            .document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error.toRepositoryFailure())
                    return@addSnapshotListener
                }
                trySend(snapshot?.data?.toUserProfileOrNull(userId))
            }

        awaitClose { listener.remove() }
    }

    private companion object {
        const val USERS = "users"
    }
}
