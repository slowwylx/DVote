package com.dvote.data.firebase.repository

import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.dvote.core.model.AuthRepairReason
import com.dvote.core.model.AuthState
import com.dvote.core.model.VotingKey
import com.dvote.data.firebase.dto.toUserProfileOrNull
import com.dvote.data.firebase.dto.toVotingKeyDocumentOrNull
import com.dvote.data.firebase.dto.VotingKeyStatus
import com.dvote.domain.coroutines.runSuspendCatching
import com.dvote.domain.repository.AuthRepository
import com.dvote.domain.security.buildVotingKeyRegistrationPayload
import com.dvote.domain.security.VoteSigner
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableOptions
import java.time.Clock
import java.text.Normalizer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val credentialManager: CredentialManager,
    private val voteSigner: VoteSigner,
    private val clock: Clock,
) : AuthRepository {
    private val isProvisioning = MutableStateFlow(false)
    private val keyRevision = MutableStateFlow(0L)
    private val callableOptions = HttpsCallableOptions.Builder()
        .setLimitedUseAppCheckTokens(true)
        .build()

    private val firebaseAuthState: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val profileGatedAuthState: Flow<AuthState> = combine(
        firebaseAuthState,
        keyRevision,
    ) { user, _ -> user }
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(AuthState.SignedOut)
            } else {
                observeProfileReadiness(user.uid)
            }
        }
        .onStart { emit(AuthState.Checking) }

    override val authState: Flow<AuthState> = combine(
        profileGatedAuthState,
        isProvisioning,
    ) { state, provisioning ->
        if (provisioning) AuthState.Checking else state
    }.distinctUntilChanged()

    override fun currentUserId(): String? = auth.currentUser?.uid

    override suspend fun signInWithGoogleIdToken(idToken: String): Result<Unit> {
        if (!isProvisioning.compareAndSet(expect = false, update = true)) {
            return Result.failure(IllegalStateException("Sign-in is already in progress."))
        }

        return try {
            authenticateAndProvision(
                authenticate = {
                    val firebaseCredential = GoogleAuthProvider.getCredential(
                        idToken,
                        null,
                    )
                    auth.signInWithCredential(firebaseCredential).await().user
                        ?: error("Firebase did not return an authenticated user.")
                },
                provision = { user -> provisionProfile(user, replaceKey = false) },
            ).mapFirebaseFailure()
        } finally {
            isProvisioning.value = false
        }
    }

    override suspend fun repairProfile(reason: AuthRepairReason): Result<Unit> {
        if (!isProvisioning.compareAndSet(expect = false, update = true)) {
            return Result.failure(IllegalStateException("Profile repair is already in progress."))
        }

        return try {
            repairAuthenticatedProfile(
                currentUser = { auth.currentUser },
                provision = { user ->
                    provisionProfile(
                        user = user,
                        replaceKey = reason.requiresKeyReplacement(),
                    )
                },
            ).mapFirebaseFailure()
        } finally {
            isProvisioning.value = false
        }
    }

    override suspend fun signOut(): Result<Unit> {
        return runSuspendCatching {
            auth.signOut()
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        }.mapFirebaseFailure()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeProfileReadiness(userId: String): Flow<AuthState> {
        return callbackFlow {
            val listener = firestore.collection(USERS)
                .document(userId)
                .addSnapshotListener { snapshot, error ->
                    val profileSnapshot = when {
                        error != null -> ProfileSnapshot.Unavailable
                        snapshot == null || !snapshot.exists() -> ProfileSnapshot.Missing
                        else -> ProfileSnapshot.Data(snapshot.data.orEmpty())
                    }
                    trySend(profileSnapshot)
                }

            awaitClose { listener.remove() }
        }.transformLatest { profileSnapshot ->
            when (profileSnapshot) {
                ProfileSnapshot.Missing -> emit(
                    AuthState.RepairRequired(
                        userId = userId,
                        reason = AuthRepairReason.MISSING_PROFILE,
                    )
                )
                ProfileSnapshot.Unavailable -> emit(
                    AuthState.RepairRequired(
                        userId = userId,
                        reason = AuthRepairReason.SERVICE_UNAVAILABLE,
                    )
                )
                is ProfileSnapshot.Data -> {
                    val profile = profileSnapshot.values.toUserProfileOrNull(userId)
                    if (profile == null) {
                        emit(
                            AuthState.RepairRequired(
                                userId = userId,
                                reason = AuthRepairReason.INVALID_PROFILE,
                            )
                        )
                        return@transformLatest
                    }

                    val localVotingKey = runSuspendCatching {
                        voteSigner.votingKey(userId)
                    }.getOrElse {
                        emit(
                            AuthState.RepairRequired(
                                userId = userId,
                                reason = AuthRepairReason.SIGNING_KEY_UNAVAILABLE,
                            )
                        )
                        return@transformLatest
                    }

                    emitAll(
                        observeVotingKeyReadiness(
                            userId = userId,
                            localVotingKey = localVotingKey,
                        )
                    )
                }
            }
        }.distinctUntilChanged()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeVotingKeyReadiness(
        userId: String,
        localVotingKey: VotingKey,
    ): Flow<AuthState> {
        return callbackFlow {
            val listener = firestore.collection(USERS)
                .document(userId)
                .collection(KEYS)
                .document(localVotingKey.keyId)
                .addSnapshotListener { snapshot, error ->
                    val keySnapshot = when {
                        error != null -> VotingKeySnapshot.Unavailable
                        snapshot == null || !snapshot.exists() -> VotingKeySnapshot.Missing
                        else -> VotingKeySnapshot.Data(snapshot.data.orEmpty())
                    }
                    trySend(keySnapshot)
                }

            awaitClose { listener.remove() }
        }.transformLatest { keySnapshot ->
            val state = when (keySnapshot) {
                VotingKeySnapshot.Missing -> AuthState.RepairRequired(
                    userId = userId,
                    reason = AuthRepairReason.MISSING_SIGNING_KEY,
                )
                VotingKeySnapshot.Unavailable -> AuthState.RepairRequired(
                    userId = userId,
                    reason = AuthRepairReason.SERVICE_UNAVAILABLE,
                )
                is VotingKeySnapshot.Data -> {
                    val registeredKey = keySnapshot.values
                        .toVotingKeyDocumentOrNull(localVotingKey.keyId)
                    when {
                        registeredKey == null -> AuthState.RepairRequired(
                            userId = userId,
                            reason = AuthRepairReason.INVALID_SIGNING_KEY_REGISTRATION,
                        )
                        registeredKey.deviceId != localVotingKey.deviceId ||
                            registeredKey.publicKey != localVotingKey.publicKey ->
                            AuthState.RepairRequired(
                                userId = userId,
                                reason = AuthRepairReason.INVALID_SIGNING_KEY_REGISTRATION,
                            )
                        registeredKey.status != VotingKeyStatus.ACTIVE ->
                            AuthState.RepairRequired(
                                userId = userId,
                                reason = AuthRepairReason.INACTIVE_SIGNING_KEY,
                            )
                        else -> AuthState.SignedIn(userId)
                    }
                }
            }
            emit(state)
        }.distinctUntilChanged()
    }

    private suspend fun provisionProfile(
        user: FirebaseUser,
        replaceKey: Boolean,
    ) {
        val displayName = user.displayName
            ?.let { Normalizer.normalize(it, Normalizer.Form.NFC) }
            ?.trim()
            ?.take(MAX_DISPLAY_NAME_LENGTH)
            ?.takeIf { it.isNotBlank() }
            ?: ANONYMOUS_DISPLAY_NAME

        firestore.collection(USERS)
            .document(user.uid)
            .set(
                mapOf(
                    "id" to user.uid,
                    "displayName" to displayName,
                ),
                SetOptions.merge(),
            )
            .await()

        val votingKey = if (replaceKey) {
            voteSigner.replaceVotingKey(user.uid).also {
                keyRevision.value += 1L
            }
        } else {
            voteSigner.votingKey(user.uid)
        }
        registerVotingKey(
            userId = user.uid,
            votingKey = votingKey,
        )
    }

    private suspend fun registerVotingKey(
        userId: String,
        votingKey: VotingKey,
    ) {
        val signedAt = clock.millis()
        val proof = buildVotingKeyRegistrationPayload(
            userId = userId,
            votingKey = votingKey,
            signedAt = signedAt,
        )
        val signature = voteSigner.signBase64(userId, proof)
        val response = functions
            .getHttpsCallable(REGISTER_VOTING_KEY, callableOptions)
            .call(
                mapOf(
                    "deviceId" to votingKey.deviceId,
                    "keyId" to votingKey.keyId,
                    "publicKey" to votingKey.publicKey,
                    "signedAt" to signedAt,
                    "signature" to signature,
                )
            )
            .await()
            .data
        validateVotingKeyRegistrationResponse(
            data = response,
            votingKey = votingKey,
        )
    }

    private companion object {
        const val ANONYMOUS_DISPLAY_NAME = "Anonymous voter"
        const val MAX_DISPLAY_NAME_LENGTH = 80
        const val USERS = "users"
        const val KEYS = "keys"
        const val REGISTER_VOTING_KEY = "registerVotingKey"
    }
}

internal fun validateVotingKeyRegistrationResponse(
    data: Any?,
    votingKey: VotingKey,
) {
    val response = data as? Map<*, *> ?: throw invalidRepositoryData()
    if (
        response.keys != REGISTER_VOTING_KEY_RESPONSE_FIELDS ||
        response["keyId"] != votingKey.keyId ||
        response["deviceId"] != votingKey.deviceId ||
        response["status"] != ACTIVE_KEY_STATUS ||
        response["registeredAt"].toExactLongOrNull()?.let { it >= 0L } != true
    ) {
        throw invalidRepositoryData()
    }
}

internal fun AuthRepairReason.requiresKeyReplacement(): Boolean {
    return when (this) {
        AuthRepairReason.INACTIVE_SIGNING_KEY,
        AuthRepairReason.INVALID_SIGNING_KEY_REGISTRATION,
        AuthRepairReason.SIGNING_KEY_UNAVAILABLE,
        -> true
        AuthRepairReason.MISSING_PROFILE,
        AuthRepairReason.MISSING_SIGNING_KEY,
        AuthRepairReason.SERVICE_UNAVAILABLE,
        AuthRepairReason.INVALID_PROFILE,
        -> false
    }
}

private sealed interface ProfileSnapshot {
    data object Missing : ProfileSnapshot
    data object Unavailable : ProfileSnapshot
    data class Data(val values: Map<String, Any?>) : ProfileSnapshot
}

private sealed interface VotingKeySnapshot {
    data object Missing : VotingKeySnapshot
    data object Unavailable : VotingKeySnapshot
    data class Data(val values: Map<String, Any?>) : VotingKeySnapshot
}

private const val ACTIVE_KEY_STATUS = "active"
private val REGISTER_VOTING_KEY_RESPONSE_FIELDS = setOf(
    "keyId",
    "deviceId",
    "status",
    "registeredAt",
)
