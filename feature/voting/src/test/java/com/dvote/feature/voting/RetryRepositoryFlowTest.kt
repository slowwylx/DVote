package com.dvote.feature.voting

import com.dvote.domain.repository.RepositoryFailure
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.single
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RetryRepositoryFlowTest {
    @Test
    fun retriesTransientFailure() = runTest {
        var attempts = 0

        val value = flow {
            attempts += 1
            if (attempts == 1) {
                throw RepositoryFailure(
                    kind = RepositoryFailure.Kind.RETRYABLE,
                    message = "temporary",
                )
            }
            emit("ready")
        }.retryTransientFailures().single()

        assertEquals("ready", value)
        assertEquals(2, attempts)
    }

    @Test
    fun doesNotRetryPersistentInvalidData() = runTest {
        var attempts = 0
        val invalidFlow = flow<String> {
            attempts += 1
            throw RepositoryFailure(
                kind = RepositoryFailure.Kind.INVALID_DATA,
                message = "invalid",
            )
        }.retryTransientFailures()

        var failure: Throwable? = null
        try {
            invalidFlow.single()
        } catch (error: RepositoryFailure) {
            failure = error
        }
        assertTrue(failure is RepositoryFailure)
        assertEquals(1, attempts)
    }

    @Test
    fun stopsAfterThreeRetriesWithExponentialBackoff() = runTest {
        var attempts = 0
        val retryingFlow = flow<String> {
            attempts += 1
            throw RepositoryFailure(
                kind = RepositoryFailure.Kind.RETRYABLE,
                message = "temporary",
            )
        }

        var failure: Throwable? = null
        try {
            retryingFlow.retryTransientFailures().single()
        } catch (error: RepositoryFailure) {
            failure = error
        }

        assertTrue(failure is RepositoryFailure)
        assertEquals(4, attempts)
        assertEquals(1_750L, currentTime)
    }
}
