package com.dvote.domain.security

import com.dvote.core.model.VoteReceipt
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.inject.Inject

class VoteReceiptVerifier @Inject constructor() {
    fun verify(receipt: VoteReceipt): Boolean {
        return try {
            if (!receipt.hasValidMetadata() || !receipt.hasCanonicalPayload()) return false

            val publicKeyBytes = receipt.publicKey.decodeCanonicalBase64() ?: return false
            val signatureBytes = receipt.signature.decodeCanonicalBase64() ?: return false
            val commitmentBytes = receipt.commitmentHash.decodeCanonicalBase64()
                ?.takeIf { it.size == SHA256_BYTES }
                ?: return false
            val publicKey = KeyFactory.getInstance("EC")
                .generatePublic(X509EncodedKeySpec(publicKeyBytes)) as? ECPublicKey
                ?: return false
            if (
                !publicKey.encoded.contentEquals(publicKeyBytes) ||
                !publicKey.params.matchesP256()
            ) {
                return false
            }
            val expectedKeyId = MessageDigest.getInstance("SHA-256")
                .digest(publicKeyBytes)
                .toLowerHex()
            if (expectedKeyId != receipt.keyId) return false

            val commitmentPayload =
                "${receipt.canonicalPayload}|${receipt.signature}|${receipt.publicKey}"
            val expectedCommitment = MessageDigest.getInstance("SHA-256")
                .digest(commitmentPayload.toByteArray(Charsets.UTF_8))
            if (!MessageDigest.isEqual(expectedCommitment, commitmentBytes)) return false

            val expectedReceiptId = MessageDigest.getInstance("SHA-256")
                .digest(receipt.commitmentHash.toByteArray(Charsets.UTF_8))
                .toLowerHex()
            if (expectedReceiptId != receipt.receiptId) return false

            Signature.getInstance("SHA256withECDSA").run {
                initVerify(publicKey)
                update(receipt.canonicalPayload.toByteArray(Charsets.UTF_8))
                verify(signatureBytes)
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun VoteReceipt.hasValidMetadata(): Boolean {
        return surveyId.matches(DOCUMENT_ID_PATTERN) &&
            receiptId.matches(SHA256_HEX_PATTERN) &&
            keyId.matches(SHA256_HEX_PATTERN) &&
            acceptedAt > 0L
    }

    private fun VoteReceipt.hasCanonicalPayload(): Boolean {
        val parts = canonicalPayload.split('|')
        if (
            parts.size != 5 ||
            parts[0] != "survey=$surveyId" ||
            !parts[1].startsWith("user=") ||
            parts[1].removePrefix("user=").length !in 1..128 ||
            parts[2] != "key=$keyId" ||
            !parts[3].startsWith("options=") ||
            !parts[4].startsWith("signedAt=") ||
            parts[4].removePrefix("signedAt=").toLongOrNull()?.let { it >= 0L } != true
        ) {
            return false
        }
        val optionIds = parts[3].removePrefix("options=").split(',')
        return optionIds.size in 1..10 &&
            optionIds.all { it.matches(DOCUMENT_ID_PATTERN) } &&
            optionIds == optionIds.sorted() &&
            optionIds.distinct().size == optionIds.size
    }

    private fun String.decodeCanonicalBase64(): ByteArray? {
        return try {
            Base64.getDecoder().decode(this)
                .takeIf { Base64.getEncoder().encodeToString(it) == this }
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun ECParameterSpec.matchesP256(): Boolean {
        val expected = AlgorithmParameters.getInstance("EC").run {
            init(ECGenParameterSpec("secp256r1"))
            getParameterSpec(ECParameterSpec::class.java)
        }
        return curve == expected.curve &&
            generator == expected.generator &&
            order == expected.order &&
            cofactor == expected.cofactor
    }

    private fun ByteArray.toLowerHex(): String = buildString(size * 2) {
        this@toLowerHex.forEach { byte ->
            val value = byte.toInt() and 0xff
            append(HEX_DIGITS[value ushr 4])
            append(HEX_DIGITS[value and 0x0f])
        }
    }

    private companion object {
        const val SHA256_BYTES = 32
        const val HEX_DIGITS = "0123456789abcdef"
        val DOCUMENT_ID_PATTERN = Regex("^[A-Za-z0-9_-]{1,128}$")
        val SHA256_HEX_PATTERN = Regex("^[0-9a-f]{64}$")
    }
}
