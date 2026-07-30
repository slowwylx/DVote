package com.dvote.data.firebase.dto

import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VotingKeyDocumentTest {
    private val publicKeyBytes = KeyPairGenerator.getInstance("EC")
        .apply { initialize(ECGenParameterSpec("secp256r1")) }
        .generateKeyPair()
        .public
        .encoded
    private val publicKey = Base64.getEncoder().encodeToString(publicKeyBytes)
    private val keyId = MessageDigest.getInstance("SHA-256")
        .digest(publicKeyBytes)
        .toHex()

    @Test
    fun mapsExactActiveAndHistoricalStates() {
        assertEquals(
            VotingKeyStatus.ACTIVE,
            activeKey().toVotingKeyDocumentOrNull(keyId)?.status,
        )
        assertEquals(
            VotingKeyStatus.ROTATED,
            (activeKey() + mapOf(
                "status" to "rotated",
                "rotatedAt" to 2L,
            )).toVotingKeyDocumentOrNull(keyId)?.status,
        )
        assertEquals(
            VotingKeyStatus.REVOKED,
            (activeKey() + mapOf(
                "status" to "revoked",
                "revokedAt" to 2L,
            )).toVotingKeyDocumentOrNull(keyId)?.status,
        )
    }

    @Test
    fun rejectsMismatchedMalformedAndAmbiguousKeyState() {
        assertNull(activeKey().toVotingKeyDocumentOrNull("a".repeat(64)))
        assertNull(
            (activeKey() + ("publicKey" to "not-a-key"))
                .toVotingKeyDocumentOrNull(keyId)
        )
        assertNull(
            (activeKey() + ("unexpected" to true))
                .toVotingKeyDocumentOrNull(keyId)
        )
        assertNull(
            (activeKey() + ("rotatedAt" to 2L))
                .toVotingKeyDocumentOrNull(keyId)
        )
        assertNull(
            (activeKey() + ("registeredAt" to 1.0))
                .toVotingKeyDocumentOrNull(keyId)
        )
    }

    private fun activeKey(): Map<String, Any?> = mapOf(
        "keyId" to keyId,
        "deviceId" to "b".repeat(64),
        "publicKey" to publicKey,
        "status" to "active",
        "registeredAt" to 1L,
    )

    private fun ByteArray.toHex(): String = joinToString("") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }
}
