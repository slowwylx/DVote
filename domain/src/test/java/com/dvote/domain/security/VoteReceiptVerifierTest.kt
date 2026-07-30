package com.dvote.domain.security

import com.dvote.core.model.VoteReceipt
import com.dvote.core.model.toPortableJson
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoteReceiptVerifierTest {
    private val verifier = VoteReceiptVerifier()
    private val keyPair = KeyPairGenerator.getInstance("EC").run {
        initialize(ECGenParameterSpec("secp256r1"))
        generateKeyPair()
    }

    @Test
    fun verifiesCompletePortableBundleOffline() {
        val receipt = receipt()

        assertTrue(verifier.verify(receipt))
        assertTrue(receipt.toPortableJson().contains("\"format\": \"dvote-verification-bundle-v2\""))
        assertTrue(receipt.toPortableJson().contains(receipt.canonicalPayload))
    }

    @Test
    fun rejectsMalformedMetadataAndCryptographicTampering() {
        val receipt = receipt()

        assertFalse(verifier.verify(receipt.copy(surveyId = "survey-2")))
        assertFalse(verifier.verify(receipt.copy(receiptId = "0".repeat(64))))
        assertFalse(verifier.verify(receipt.copy(keyId = "0".repeat(64))))
        assertFalse(verifier.verify(receipt.copy(acceptedAt = 0L)))
        assertFalse(verifier.verify(receipt.copy(commitmentHash = Base64.getEncoder()
            .encodeToString(ByteArray(32)))))
        assertFalse(verifier.verify(receipt.copy(canonicalPayload =
            receipt.canonicalPayload.replace("option-1", "option-2"))))
        assertFalse(verifier.verify(receipt.copy(publicKey = receipt.publicKey.dropLast(1) + "A")))
        assertFalse(verifier.verify(receipt.copy(signature = receipt.signature.dropLast(1) + "A")))
    }

    private fun receipt(): VoteReceipt {
        val keyId = MessageDigest.getInstance("SHA-256")
            .digest(keyPair.public.encoded)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        val payload =
            "survey=survey-1|user=user-1|key=$keyId|options=option-1|signedAt=1900000000000"
        val publicKey = Base64.getEncoder().encodeToString(keyPair.public.encoded)
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(keyPair.private)
            update(payload.toByteArray(Charsets.UTF_8))
            Base64.getEncoder().encodeToString(sign())
        }
        val commitment = Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-256")
                .digest("$payload|$signature|$publicKey".toByteArray(Charsets.UTF_8))
        )
        val receiptId = MessageDigest.getInstance("SHA-256")
            .digest(commitment.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return VoteReceipt(
            surveyId = "survey-1",
            receiptId = receiptId,
            keyId = keyId,
            commitmentHash = commitment,
            acceptedAt = 1_900_000_000_100,
            canonicalPayload = payload,
            publicKey = publicKey,
            signature = signature,
        )
    }
}
