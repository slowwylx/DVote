package com.dvote.feature.voting.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dvote.domain.repository.AuthRepository
import com.dvote.domain.repository.UserRepository
import com.dvote.feature.voting.VotingUiError
import com.dvote.feature.voting.retryTransientFailures
import com.dvote.feature.voting.toVotingUiError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    userRepository: UserRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val actionError = MutableStateFlow<VotingUiError?>(null)
    private var isSigningOut = false

    val uiState = combine(
        userRepository.observeCurrentUser()
            .retryTransientFailures()
            .map { profile -> ProfileUiState(profile = profile, isLoading = false) }
            .catch { cause ->
                emit(
                    ProfileUiState(
                        isLoading = false,
                        error = cause.toVotingUiError(VotingUiError.LOAD_PROFILE_FAILED),
                    )
                )
            },
        actionError,
    ) { state, error ->
        if (error == null) state else state.copy(error = error)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfileUiState(),
    )

    fun signOut() {
        if (isSigningOut) return
        isSigningOut = true

        viewModelScope.launch {
            try {
                actionError.value = null
                authRepository.signOut()
                    .onFailure { cause ->
                        actionError.value = cause.toVotingUiError(
                            VotingUiError.SIGN_OUT_FAILED
                        )
                    }
            } finally {
                isSigningOut = false
            }
        }
    }
}
