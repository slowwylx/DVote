package com.dvote.ui.auth.signin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dvote.data.repository.AuthRepository
import com.dvote.data.repository.ProfileRepository
import com.dvote.domain.GoogleAuthUseCase
import com.dvote.ui.SessionService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val service: SessionService,
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val googleAuth: GoogleAuthUseCase
) : ViewModel() {

    private val _signInState = MutableStateFlow<SignInState>(SignInState.None)
    val signInState = _signInState.asStateFlow()

    fun loginWithGoogle(context: android.content.Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _signInState.value = SignInState.Loading
            val credentialsResult = googleAuth.invoke(context)
            handleGoogleCredentialsResult(credentialsResult)
        }
    }

    private suspend fun handleGoogleCredentialsResult(result: GoogleAuthUseCase.AuthResult) {
        when (result) {
            is GoogleAuthUseCase.AuthResult.Success -> {
                val loginResult = authRepository.loginWithCredentials(result.authCredential)
                handlerLoginResult(loginResult)
            }
            is GoogleAuthUseCase.AuthResult.Error -> {
                _signInState.value = SignInState.Error(result.message)
            }
        }
    }

    private suspend fun handlerLoginResult(result: AuthRepository.AuthResult?) {
        when (result) {
            is AuthRepository.AuthResult.Success -> {
                _signInState.value = SignInState.SignedIn
                profileRepository.initUserData()
                service.login()
            }
            is AuthRepository.AuthResult.Error -> {
                _signInState.value = SignInState.Error(result.message ?: "Login failed")
            }
            else -> {
                _signInState.value = SignInState.Error("Unknown error occurred")
            }
        }
    }


}