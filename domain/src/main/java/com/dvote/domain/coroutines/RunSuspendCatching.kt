package com.dvote.domain.coroutines

import kotlinx.coroutines.CancellationException

suspend inline fun <T> runSuspendCatching(
    crossinline block: suspend () -> T,
): Result<T> {
    return try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        Result.failure(error)
    }
}
