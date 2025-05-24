package com.dvote.ui.auth.signin

sealed class SignInState {
    data object None : SignInState()
    data object Loading : SignInState()
    data object SignedIn : SignInState()
    data object NeedSignUp : SignInState()
    data object LoggedOut : SignInState()
    data class Error(val errorMessage: String? = null) : SignInState()
}