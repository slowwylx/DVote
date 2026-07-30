package com.dvote.data.firebase.repository

import com.dvote.domain.repository.RepositoryFailure
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.functions.FirebaseFunctionsException
import java.io.IOException
import kotlinx.coroutines.CancellationException

internal suspend inline fun <T> runFirebaseCatching(
    crossinline block: suspend () -> T,
): Result<T> {
    return try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        Result.failure(error.toRepositoryFailure())
    }
}

internal fun <T> Result<T>.mapFirebaseFailure(): Result<T> {
    return fold(
        onSuccess = { value -> Result.success(value) },
        onFailure = { error -> Result.failure(error.toRepositoryFailure()) },
    )
}

internal fun invalidRepositoryData(
    cause: Throwable? = null,
): RepositoryFailure {
    return RepositoryFailure(
        kind = RepositoryFailure.Kind.INVALID_DATA,
        message = "The service returned invalid data.",
        cause = cause,
    )
}

internal fun Any?.toExactLongOrNull(): Long? {
    val number = this as? Number ?: return null
    val longValue = number.toLong()
    return longValue.takeIf { value ->
        when (number) {
            is Byte, is Short, is Int, is Long -> true
            is Float -> number.isFinite() && number.toDouble() == value.toDouble()
            is Double -> number.isFinite() && number == value.toDouble()
            else -> false
        }
    }
}

internal fun Throwable.toRepositoryFailure(): Throwable {
    if (this is CancellationException || this is RepositoryFailure) return this

    val kind = when (this) {
        is FirebaseFirestoreException -> cloudStatusFailureKind(code.name)
        is FirebaseFunctionsException -> cloudStatusFailureKind(code.name)
        is FirebaseNetworkException,
        is IOException,
        -> RepositoryFailure.Kind.RETRYABLE
        else -> RepositoryFailure.Kind.REJECTED
    }
    val message = when (kind) {
        RepositoryFailure.Kind.RETRYABLE ->
            "The service is temporarily unavailable. Please try again."
        RepositoryFailure.Kind.INVALID_DATA ->
            "The service returned invalid data."
        RepositoryFailure.Kind.REJECTED ->
            "The service could not complete this request."
    }
    return RepositoryFailure(
        kind = kind,
        message = message,
        cause = this,
    )
}

internal fun cloudStatusFailureKind(statusName: String): RepositoryFailure.Kind {
    return when (statusName) {
        "ABORTED",
        "DEADLINE_EXCEEDED",
        "INTERNAL",
        "RESOURCE_EXHAUSTED",
        "UNAVAILABLE",
        "UNKNOWN",
        -> RepositoryFailure.Kind.RETRYABLE
        "DATA_LOSS" ->
            RepositoryFailure.Kind.INVALID_DATA
        else -> RepositoryFailure.Kind.REJECTED
    }
}
