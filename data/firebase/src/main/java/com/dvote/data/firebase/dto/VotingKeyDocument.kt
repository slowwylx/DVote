package com.dvote.data.firebase.dto

import java.security.MessageDigest
import java.util.Base64

internal data class VotingKeyDocument(
    val keyId: String,
    val deviceId: String,
    val publicKey: String,
    val status: VotingKeyStatus,
    val registeredAt: Long,
)

internal enum class VotingKeyStatus {
    ACTIVE,
    ROTATED,
    REVOKED,
}

internal fun Map<String, Any?>.toVotingKeyDocumentOrNull(
    expectedKeyId: String,
): VotingKeyDocument? {
    if (
        keys.any { it !in KEY_FIELDS } ||
        !keys.containsAll(REQUIRED_KEY_FIELDS)
    ) {
        return null
    }

    val keyId = this["keyId"] as? String ?: return null
    val deviceId = this["deviceId"] as? String ?: return null
    val publicKey = this["publicKey"] as? String ?: return null
    val status = when (this["status"]) {
        "active" -> VotingKeyStatus.ACTIVE
        "rotated" -> VotingKeyStatus.ROTATED
        "revoked" -> VotingKeyStatus.REVOKED
        else -> return null
    }
    val registeredAt = (this["registeredAt"] as? Long)
        ?.takeIf { it >= 0L }
        ?: return null
    val rotatedAt = optionalNonNegativeLong("rotatedAt")
    val revokedAt = optionalNonNegativeLong("revokedAt")

    if (
        keyId != expectedKeyId ||
        !keyId.matches(SHA256_HEX_PATTERN) ||
        !deviceId.matches(SHA256_HEX_PATTERN) ||
        !publicKey.matches(CANONICAL_BASE64_PATTERN) ||
        !publicKey.isCanonicalP256PublicKey() ||
        publicKey.keyId() != keyId ||
        when (status) {
            VotingKeyStatus.ACTIVE -> rotatedAt.isAbsent && revokedAt.isAbsent
            VotingKeyStatus.ROTATED -> rotatedAt.isPresent && revokedAt.isAbsent
            VotingKeyStatus.REVOKED -> rotatedAt.isAbsent && revokedAt.isPresent
        }.not()
    ) {
        return null
    }

    return VotingKeyDocument(
        keyId = keyId,
        deviceId = deviceId,
        publicKey = publicKey,
        status = status,
        registeredAt = registeredAt,
    )
}

private fun String.keyId(): String {
    return MessageDigest.getInstance("SHA-256")
        .digest(Base64.getDecoder().decode(this))
        .toLowerHex()
}

private fun ByteArray.toLowerHex(): String = buildString(size * 2) {
    this@toLowerHex.forEach { byte ->
        val value = byte.toInt() and 0xff
        append(HEX_DIGITS[value ushr 4])
        append(HEX_DIGITS[value and 0x0f])
    }
}

private fun Map<String, Any?>.optionalNonNegativeLong(field: String): OptionalLong {
    if (!containsKey(field)) return OptionalLong.Absent
    val value = (this[field] as? Long)
        ?.takeIf { it >= 0L }
        ?: return OptionalLong.Invalid
    return OptionalLong.Present(value)
}

private sealed interface OptionalLong {
    val isAbsent: Boolean
        get() = this is Absent
    val isPresent: Boolean
        get() = this is Present

    data object Absent : OptionalLong
    data object Invalid : OptionalLong
    data class Present(val value: Long) : OptionalLong
}

private val REQUIRED_KEY_FIELDS = setOf(
    "keyId",
    "deviceId",
    "publicKey",
    "status",
    "registeredAt",
)
private val KEY_FIELDS = REQUIRED_KEY_FIELDS + setOf("rotatedAt", "revokedAt")
private val SHA256_HEX_PATTERN = Regex("^[0-9a-f]{64}$")
private val CANONICAL_BASE64_PATTERN = Regex(
    "^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$"
)
private const val HEX_DIGITS = "0123456789abcdef"
