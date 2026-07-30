package com.dvote.feature.voting

import com.dvote.domain.repository.isRetryableRepositoryFailure
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.retryWhen

internal fun <T> Flow<T>.retryTransientFailures(): Flow<T> {
    return retryWhen { error, attempt ->
        if (!error.isRetryableRepositoryFailure || attempt >= MAX_RETRY_ATTEMPTS) {
            return@retryWhen false
        }

        delay(INITIAL_RETRY_DELAY_MILLIS shl attempt.toInt())
        true
    }
}

private const val MAX_RETRY_ATTEMPTS = 3L
private const val INITIAL_RETRY_DELAY_MILLIS = 250L
