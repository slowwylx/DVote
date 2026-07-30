package com.dvote.domain.coroutines

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RunSuspendCatchingTest {
    @Test
    fun rethrowsCancellation() = runTest {
        var cancellationWasRethrown = false

        try {
            runSuspendCatching<Unit> {
                throw CancellationException("cancelled")
            }
        } catch (_: CancellationException) {
            cancellationWasRethrown = true
        }

        assertTrue(cancellationWasRethrown)
    }

    @Test
    fun wrapsOrdinaryFailure() = runTest {
        val result = runSuspendCatching<Unit> {
            error("failed")
        }

        assertTrue(result.isFailure)
        assertEquals("failed", result.exceptionOrNull()?.message)
    }
}
