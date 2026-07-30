package com.dvote.data.firebase.repository

import com.dvote.core.model.AuthRepairReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthProvisioningWorkflowTest {
    @Test
    fun interruptedProvisioningKeepsTheAuthenticatedSessionRepairable() = runTest {
        var persistedUser: String? = null
        val cancellation = CancellationException("process stopped")

        val thrown = try {
            authenticateAndProvision(
                authenticate = {
                    "user-1".also { persistedUser = it }
                },
                provision = { throw cancellation },
            )
            null
        } catch (error: CancellationException) {
            error
        }

        assertSame(cancellation, thrown)
        assertEquals("user-1", persistedUser)

        var repairedUser: String? = null
        val repairResult = repairAuthenticatedProfile(
            currentUser = { persistedUser },
            provision = { repairedUser = it },
        )

        assertTrue(repairResult.isSuccess)
        assertEquals("user-1", repairedUser)
    }

    @Test
    fun ordinaryProfileFailureDoesNotRollBackTheAuthenticatedSession() = runTest {
        var persistedUser: String? = null

        val result = authenticateAndProvision(
            authenticate = {
                "user-1".also { persistedUser = it }
            },
            provision = { error("profile write failed") },
        )

        assertTrue(result.isFailure)
        assertEquals("user-1", persistedUser)
    }

    @Test
    fun authenticationFailureNeverStartsProvisioning() = runTest {
        val failure = IllegalStateException("authentication failed")
        var provisioned = false

        val result = authenticateAndProvision<String>(
            authenticate = { throw failure },
            provision = { provisioned = true },
        )

        assertSame(failure, result.exceptionOrNull())
        assertTrue(!provisioned)
    }

    @Test
    fun repairWithoutAnAuthenticatedUserFailsBeforeProvisioning() = runTest {
        var provisioned = false

        val result = repairAuthenticatedProfile<String>(
            currentUser = { null },
            provision = { provisioned = true },
        )

        assertTrue(result.isFailure)
        assertTrue(!provisioned)
    }

    @Test
    fun onlyUnrecoverableLocalKeyStatesUseTheReplacementPath() {
        val replacementReasons = setOf(
            AuthRepairReason.SIGNING_KEY_UNAVAILABLE,
            AuthRepairReason.INACTIVE_SIGNING_KEY,
            AuthRepairReason.INVALID_SIGNING_KEY_REGISTRATION,
        )

        AuthRepairReason.entries.forEach { reason ->
            assertEquals(
                reason in replacementReasons,
                reason.requiresKeyReplacement(),
            )
        }
    }
}
