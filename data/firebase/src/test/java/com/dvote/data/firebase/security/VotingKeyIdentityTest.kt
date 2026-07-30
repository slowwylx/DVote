package com.dvote.data.firebase.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VotingKeyIdentityTest {
    @Test
    fun accountsOnOneInstallationHaveDifferentAliasesAndDeviceIds() {
        val installationId = "01234567-89ab-4cde-8fab-0123456789ab"

        assertNotEquals(
            VotingKeyIdentity.alias("user-1", installationId),
            VotingKeyIdentity.alias("user-2", installationId),
        )
        assertNotEquals(
            VotingKeyIdentity.deviceId("user-1", installationId),
            VotingKeyIdentity.deviceId("user-2", installationId),
        )
    }

    @Test
    fun identityIsStableForOneAccountAndChangesAfterReinstall() {
        val firstInstallation = "01234567-89ab-4cde-8fab-0123456789ab"
        val secondInstallation = "fedcba98-7654-4321-8abc-fedcba987654"

        assertEquals(
            VotingKeyIdentity.alias("user-1", firstInstallation),
            VotingKeyIdentity.alias("user-1", firstInstallation),
        )
        assertNotEquals(
            VotingKeyIdentity.alias("user-1", firstInstallation),
            VotingKeyIdentity.alias("user-1", secondInstallation),
        )
        assertTrue(
            VotingKeyIdentity.deviceId("user-1", firstInstallation)
                .matches(Regex("^[0-9a-f]{64}$"))
        )
    }

    @Test
    fun keyIdentifierDependsOnThePublicKeyBytes() {
        assertNotEquals(
            VotingKeyIdentity.keyId(byteArrayOf(1, 2, 3)),
            VotingKeyIdentity.keyId(byteArrayOf(1, 2, 4)),
        )
    }
}
