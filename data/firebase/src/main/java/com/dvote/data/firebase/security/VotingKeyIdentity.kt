package com.dvote.data.firebase.security

import java.security.MessageDigest

internal object VotingKeyIdentity {
    fun deviceId(
        userId: String,
        installationId: String,
    ): String {
        return sha256Hex("dvote-device-v2\u0000$userId\u0000$installationId")
    }

    fun alias(
        userId: String,
        installationId: String,
    ): String {
        return "dvote_vote_v2_${sha256Hex("$userId\u0000$installationId")}"
    }

    fun keyId(publicKeyBytes: ByteArray): String = sha256Hex(publicKeyBytes)

    private fun sha256Hex(value: String): String =
        sha256Hex(value.toByteArray(Charsets.UTF_8))

    private fun sha256Hex(value: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value)
            .toLowerHex()
    }

    private fun ByteArray.toLowerHex(): String = buildString(size * 2) {
        this@toLowerHex.forEach { byte ->
            val value = byte.toInt() and 0xff
            append(HEX_DIGITS[value ushr 4])
            append(HEX_DIGITS[value and 0x0f])
        }
    }

    private const val HEX_DIGITS = "0123456789abcdef"
}
