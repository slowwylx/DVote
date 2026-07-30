package com.dvote.core.model

sealed interface AuthState {
    data object Checking : AuthState
    data object SignedOut : AuthState
    data class RepairRequired(
        val userId: String,
        val reason: AuthRepairReason,
    ) : AuthState
    data class SignedIn(val userId: String) : AuthState
}

enum class AuthRepairReason {
    MISSING_PROFILE,
    INVALID_PROFILE,
    MISSING_SIGNING_KEY,
    INACTIVE_SIGNING_KEY,
    INVALID_SIGNING_KEY_REGISTRATION,
    SIGNING_KEY_UNAVAILABLE,
    SERVICE_UNAVAILABLE,
}
