package com.dvote.feature.voting.profile

import com.dvote.core.model.AuthRepairReason
import com.dvote.core.model.AuthState
import com.dvote.core.model.UserProfile
import com.dvote.domain.repository.AuthRepository
import com.dvote.domain.repository.RepositoryFailure
import com.dvote.domain.repository.UserRepository
import com.dvote.feature.voting.VotingUiError
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
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
    fun profileFlowMovesFromLoadingToContent() = runTest(dispatcher) {
        val profile = profile()
        val viewModel = ProfileViewModel(
            userRepository = FakeUserRepository(MutableStateFlow(profile)),
            authRepository = FakeAuthRepository(),
        )
        collectState(viewModel)

        runCurrent()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(profile, viewModel.uiState.value.profile)
        assertEquals(null, viewModel.uiState.value.error)
    }

    @Test
    fun persistentProfileFailureMapsToTypedError() = runTest(dispatcher) {
        val repository = FakeUserRepository(
            flow {
                throw RepositoryFailure(
                    kind = RepositoryFailure.Kind.INVALID_DATA,
                    message = "private diagnostic",
                )
            }
        )
        val viewModel = ProfileViewModel(repository, FakeAuthRepository())
        collectState(viewModel)

        runCurrent()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(
            VotingUiError.INVALID_SERVICE_DATA,
            viewModel.uiState.value.error,
        )
    }

    @Test
    fun signOutGuardPreventsDuplicatesAndFailurePreservesProfile() =
        runTest(dispatcher) {
            val gate = CompletableDeferred<Result<Unit>>()
            val authRepository = FakeAuthRepository(signOutGate = gate)
            val profile = profile()
            val viewModel = ProfileViewModel(
                userRepository = FakeUserRepository(MutableStateFlow(profile)),
                authRepository = authRepository,
            )
            collectState(viewModel)
            runCurrent()

            viewModel.signOut()
            viewModel.signOut()
            runCurrent()

            assertEquals(1, authRepository.signOutCalls)

            gate.complete(
                Result.failure(
                    RepositoryFailure(
                        kind = RepositoryFailure.Kind.REJECTED,
                        message = "private diagnostic",
                    )
                )
            )
            runCurrent()

            assertEquals(profile, viewModel.uiState.value.profile)
            assertEquals(VotingUiError.REQUEST_REJECTED, viewModel.uiState.value.error)
        }

    private fun TestScope.collectState(
        viewModel: ProfileViewModel,
    ) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
    }

    private fun profile() = UserProfile(
        id = "user-1",
        displayName = "Alice",
        createdSurveyIds = persistentListOf("survey-1"),
        votedSurveyIds = persistentListOf("survey-2"),
    )

    private class FakeUserRepository(
        private val profiles: Flow<UserProfile?>,
    ) : UserRepository {
        override fun observeCurrentUser(): Flow<UserProfile?> = profiles
    }

    private class FakeAuthRepository(
        private val signOutGate: CompletableDeferred<Result<Unit>>? = null,
    ) : AuthRepository {
        override val authState = MutableStateFlow<AuthState>(AuthState.SignedIn("user-1"))
        var signOutCalls = 0

        override fun currentUserId(): String = "user-1"

        override suspend fun signInWithGoogleIdToken(idToken: String) =
            Result.success(Unit)

        override suspend fun repairProfile(reason: AuthRepairReason) =
            Result.success(Unit)

        override suspend fun signOut(): Result<Unit> {
            signOutCalls += 1
            return signOutGate?.await() ?: Result.success(Unit)
        }
    }
}
