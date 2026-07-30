package com.dvote.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dvote.core.model.AuthRepairReason
import com.dvote.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState = _uiState.asStateFlow()
    private val automaticRepairAttempts = mutableSetOf<Pair<String, AuthRepairReason>>()

    fun beginSignIn(): Boolean {
        if (_uiState.value.isLoading) return false
        automaticRepairAttempts.clear()
        _uiState.value = AuthUiState(isLoading = true)
        return true
    }

    fun signIn(idToken: String) {
        viewModelScope.launch {
            authRepository.signInWithGoogleIdToken(idToken)
                .onFailure { cause ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = cause.toAuthUiError(AuthUiError.SIGN_IN_FAILED),
                        )
                    }
                }
                .onSuccess {
                    _uiState.update { AuthUiState() }
                }
        }
    }

    fun repairProfile(
        userId: String,
        reason: AuthRepairReason,
        automatic: Boolean = false,
    ) {
        val repairAttempt = userId to reason
        if (automatic && repairAttempt in automaticRepairAttempts) return
        if (!beginAction()) return
        if (automatic) automaticRepairAttempts += repairAttempt

        viewModelScope.launch {
            authRepository.repairProfile(reason)
                .onFailure { cause ->
                    _uiState.value = AuthUiState(
                        error = cause.toAuthUiError(AuthUiError.ACCOUNT_SETUP_FAILED),
                    )
                }
                .onSuccess {
                    _uiState.value = AuthUiState()
                }
        }
    }

    fun signOut() {
        if (!beginAction()) return

        viewModelScope.launch {
            authRepository.signOut()
                .onFailure { cause ->
                    _uiState.value = AuthUiState(
                        error = cause.toAuthUiError(AuthUiError.SIGN_OUT_FAILED),
                    )
                }
                .onSuccess {
                    automaticRepairAttempts.clear()
                    _uiState.value = AuthUiState()
                }
        }
    }

    fun onCredentialError(error: Throwable) {
        _uiState.value = AuthUiState(
            error = error.toAuthUiError(AuthUiError.SIGN_IN_FAILED),
        )
    }

    private fun beginAction(): Boolean {
        if (_uiState.value.isLoading) return false
        _uiState.value = AuthUiState(isLoading = true)
        return true
    }
}
