package com.dvote.domain.repository

class RepositoryFailure(
    val kind: Kind,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    enum class Kind {
        RETRYABLE,
        INVALID_DATA,
        REJECTED,
    }
}

val Throwable.isRetryableRepositoryFailure: Boolean
    get() = this is RepositoryFailure &&
        kind == RepositoryFailure.Kind.RETRYABLE
