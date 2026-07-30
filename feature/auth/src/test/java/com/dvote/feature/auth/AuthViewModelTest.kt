package com.dvote.feature.auth

import com.dvote.core.model.AuthState
import com.dvote.core.model.AuthRepairReason
import com.dvote.domain.repository.AuthRepository
import com.dvote.domain.repository.RepositoryFailure
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun automaticRepairRunsOnlyOnceForTheSameUser() = runTest(dispatcher) {
        val repository = FakeAuthRepository()
        val viewModel = AuthViewModel(repository)

        viewModel.repairProfile(
            userId = "user-1",
            reason = AuthRepairReason.MISSING_PROFILE,
            automatic = true,
        )
        runCurrent()
        viewModel.repairProfile(
            userId = "user-1",
            reason = AuthRepairReason.MISSING_PROFILE,
            automatic = true,
        )
        runCurrent()

        assertEquals(1, repository.repairCalls)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun failedRepairLeavesRetryAndSignOutAvailable() = runTest(dispatcher) {
        val repository = FakeAuthRepository(
            repairResult = Result.failure(IllegalStateException("backend details")),
        )
        val viewModel = AuthViewModel(repository)

        viewModel.repairProfile(
            userId = "user-1",
            reason = AuthRepairReason.INVALID_PROFILE,
        )
        runCurrent()

        assertEquals(
            AuthUiError.ACCOUNT_SETUP_FAILED,
            viewModel.uiState.value.error,
        )
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun repositoryFailureKindReplacesBackendMessage() {
        val error = RepositoryFailure(
            kind = RepositoryFailure.Kind.INVALID_DATA,
            message = "sensitive backend details",
        )

        assertEquals(
            AuthUiError.INVALID_SERVICE_DATA,
            error.toAuthUiError(AuthUiError.SIGN_IN_FAILED),
        )
    }

    @Test
    fun beginSignInSynchronouslyGuardsDuplicateCredentialLaunches() =
        runTest(dispatcher) {
            val repository = FakeAuthRepository()
            val viewModel = AuthViewModel(repository)

            assertTrue(viewModel.beginSignIn())
            assertFalse(viewModel.beginSignIn())

            viewModel.signIn("id-token")
            runCurrent()

            assertEquals(1, repository.signInCalls)
            assertFalse(viewModel.uiState.value.isLoading)
        }

    @Test
    fun signInFailureUsesTypedErrorAndReleasesLoadingGuard() =
        runTest(dispatcher) {
            val repository = FakeAuthRepository(
                signInResult = Result.failure(
                    RepositoryFailure(
                        kind = RepositoryFailure.Kind.RETRYABLE,
                        message = "private diagnostic",
                    )
                )
            )
            val viewModel = AuthViewModel(repository)

            viewModel.beginSignIn()
            viewModel.signIn("id-token")
            runCurrent()

            assertEquals(AuthUiError.SERVICE_UNAVAILABLE, viewModel.uiState.value.error)
            assertFalse(viewModel.uiState.value.isLoading)
        }

    @Test
    fun signOutSynchronouslyGuardsDuplicateRequests() = runTest(dispatcher) {
        val signOutGate = CompletableDeferred<Result<Unit>>()
        val repository = FakeAuthRepository(signOutGate = signOutGate)
        val viewModel = AuthViewModel(repository)

        viewModel.signOut()
        viewModel.signOut()
        runCurrent()

        assertEquals(1, repository.signOutCalls)
        assertTrue(viewModel.uiState.value.isLoading)

        signOutGate.complete(Result.success(Unit))
        runCurrent()

        assertFalse(viewModel.uiState.value.isLoading)
    }

    private class FakeAuthRepository(
        private val repairResult: Result<Unit> = Result.success(Unit),
        private val signInResult: Result<Unit> = Result.success(Unit),
        private val signOutGate: CompletableDeferred<Result<Unit>>? = null,
    ) : AuthRepository {
        override val authState = MutableStateFlow<AuthState>(AuthState.SignedOut)
        var repairCalls = 0
        var signInCalls = 0
        var signOutCalls = 0

        override fun currentUserId(): String? = null

        override suspend fun signInWithGoogleIdToken(idToken: String): Result<Unit> {
            signInCalls += 1
            return signInResult
        }

        override suspend fun repairProfile(reason: AuthRepairReason): Result<Unit> {
            repairCalls += 1
            return repairResult
        }

        override suspend fun signOut(): Result<Unit> {
            signOutCalls += 1
            return signOutGate?.await() ?: Result.success(Unit)
        }
    }
}
