package com.dvote.data.firebase.repository

import com.dvote.domain.repository.RepositoryFailure
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseFailureMapperTest {
    @Test
    fun classifiesEveryCloudStatusCategory() {
        val retryableStatuses = setOf(
            "ABORTED",
            "DEADLINE_EXCEEDED",
            "INTERNAL",
            "RESOURCE_EXHAUSTED",
            "UNAVAILABLE",
            "UNKNOWN",
        )
        retryableStatuses.forEach { status ->
            assertEquals(
                RepositoryFailure.Kind.RETRYABLE,
                cloudStatusFailureKind(status),
            )
        }

        assertEquals(
            RepositoryFailure.Kind.INVALID_DATA,
            cloudStatusFailureKind("DATA_LOSS"),
        )
        setOf(
            "ALREADY_EXISTS",
            "CANCELLED",
            "FAILED_PRECONDITION",
            "INVALID_ARGUMENT",
            "NOT_FOUND",
            "PERMISSION_DENIED",
            "UNAUTHENTICATED",
        ).forEach { status ->
            assertEquals(
                RepositoryFailure.Kind.REJECTED,
                cloudStatusFailureKind(status),
            )
        }
    }

    @Test
    fun preservesAnExistingRepositoryFailure() {
        val source = invalidRepositoryData()

        assertSame(source, source.toRepositoryFailure())
    }

    @Test
    fun mapsIoFailureWithoutExposingItsDiagnosticMessage() {
        val source = IOException("private backend detail")
        val failure = source.toRepositoryFailure() as RepositoryFailure

        assertEquals(RepositoryFailure.Kind.RETRYABLE, failure.kind)
        assertFalse(failure.message.orEmpty().contains(source.message.orEmpty()))
        assertSame(source, failure.cause)
    }

    @Test
    fun exactLongDecoderRejectsFractionalNonFiniteAndUnknownNumbers() {
        assertEquals(1L, 1.toExactLongOrNull())
        assertEquals(1L, 1L.toExactLongOrNull())
        assertEquals(1L, 1.0.toExactLongOrNull())
        assertEquals(1L, 1.0f.toExactLongOrNull())

        assertNull(1.5.toExactLongOrNull())
        assertNull(Double.NaN.toExactLongOrNull())
        assertNull(Double.POSITIVE_INFINITY.toExactLongOrNull())
        assertNull("1".toExactLongOrNull())
        assertNull(null.toExactLongOrNull())
    }

    @Test
    fun firebaseCatchingRethrowsCancellationAndMapsOrdinaryFailure() = runTest {
        val cancellation = CancellationException("cancelled")
        var thrown: CancellationException? = null

        try {
            runFirebaseCatching<Unit> { throw cancellation }
        } catch (error: CancellationException) {
            thrown = error
        }

        assertSame(cancellation, thrown)

        val result = runFirebaseCatching<Unit> {
            throw IOException("offline")
        }
        assertTrue(result.exceptionOrNull() is RepositoryFailure)
        assertEquals(
            RepositoryFailure.Kind.RETRYABLE,
            (result.exceptionOrNull() as RepositoryFailure).kind,
        )
    }
}
