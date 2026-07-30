package com.dvote.feature.auth

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: AuthUiError? = null,
)
