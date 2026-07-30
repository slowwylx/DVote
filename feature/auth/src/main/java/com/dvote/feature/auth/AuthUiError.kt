package com.dvote.feature.auth

import androidx.annotation.StringRes
import com.dvote.domain.repository.RepositoryFailure

enum class AuthUiError {
    SERVICE_UNAVAILABLE,
    INVALID_SERVICE_DATA,
    REQUEST_REJECTED,
    SIGN_IN_FAILED,
    ACCOUNT_SETUP_FAILED,
    SIGN_OUT_FAILED,
}

internal fun Throwable.toAuthUiError(
    fallback: AuthUiError,
): AuthUiError = when ((this as? RepositoryFailure)?.kind) {
    RepositoryFailure.Kind.RETRYABLE -> AuthUiError.SERVICE_UNAVAILABLE
    RepositoryFailure.Kind.INVALID_DATA -> AuthUiError.INVALID_SERVICE_DATA
    RepositoryFailure.Kind.REJECTED -> AuthUiError.REQUEST_REJECTED
    null -> fallback
}

@get:StringRes
internal val AuthUiError.messageResource: Int
    get() = when (this) {
        AuthUiError.SERVICE_UNAVAILABLE -> R.string.auth_error_service_unavailable
        AuthUiError.INVALID_SERVICE_DATA -> R.string.auth_error_invalid_service_data
        AuthUiError.REQUEST_REJECTED -> R.string.auth_error_request_rejected
        AuthUiError.SIGN_IN_FAILED -> R.string.auth_error_sign_in
        AuthUiError.ACCOUNT_SETUP_FAILED -> R.string.auth_error_account_setup
        AuthUiError.SIGN_OUT_FAILED -> R.string.auth_error_sign_out
    }
