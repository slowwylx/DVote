package com.dvote.data.firebase.dto

import com.dvote.core.model.UserProfile
import java.security.KeyFactory
import java.security.interfaces.ECPublicKey
import java.security.spec.X509EncodedKeySpec
import java.text.Normalizer
import java.util.Base64
import kotlinx.collections.immutable.toImmutableList

internal fun Map<String, Any?>.toUserProfileOrNull(
    expectedUserId: String,
): UserProfile? {
    if (
        keys.any { it !in PROFILE_FIELDS } ||
        !keys.containsAll(REQUIRED_PROFILE_FIELDS)
    ) {
        return null
    }

    val id = this["id"] as? String ?: return null
    val displayName = this["displayName"] as? String ?: return null
    val createdSurveyIds = optionalDocumentIds("createdSurveyIds") ?: return null
    val votedSurveyIds = optionalDocumentIds("votedSurveyIds") ?: return null

    if (
        id != expectedUserId ||
        displayName.length !in DISPLAY_NAME_RANGE ||
        displayName != displayName.trim() ||
        displayName != Normalizer.normalize(displayName, Normalizer.Form.NFC) ||
        !hasValidOptionalLegacyPublicKey()
    ) {
        return null
    }

    return UserProfile(
        id = id,
        displayName = displayName,
        createdSurveyIds = createdSurveyIds.toImmutableList(),
        votedSurveyIds = votedSurveyIds.toImmutableList(),
    )
}

private fun Map<String, Any?>.hasValidOptionalLegacyPublicKey(): Boolean {
    if (!containsKey("publicKey")) return true
    return (this["publicKey"] as? String)?.isCanonicalP256PublicKey() == true
}

private fun Map<String, Any?>.optionalDocumentIds(field: String): List<String>? {
    if (!containsKey(field)) return emptyList()
    val value = this[field] ?: return null
    val values = value as? List<*> ?: return null
    if (
        values.size > MAX_PROFILE_AGGREGATE_IDS ||
        values.any { item -> item !is String || !item.matches(DOCUMENT_ID_PATTERN) }
    ) {
        return null
    }
    return values.filterIsInstance<String>()
}

internal fun String.isCanonicalP256PublicKey(): Boolean {
    if (length !in PUBLIC_KEY_RANGE) return false

    return try {
        val bytes = Base64.getDecoder().decode(this)
        if (Base64.getEncoder().encodeToString(bytes) != this) return false
        val publicKey = KeyFactory.getInstance("EC")
            .generatePublic(X509EncodedKeySpec(bytes)) as? ECPublicKey
            ?: return false
        publicKey.params.curve.field.fieldSize == P256_FIELD_SIZE
    } catch (_: IllegalArgumentException) {
        false
    } catch (_: java.security.GeneralSecurityException) {
        false
    }
}

private val REQUIRED_PROFILE_FIELDS = setOf("id", "displayName")
private val PROFILE_FIELDS = REQUIRED_PROFILE_FIELDS + setOf(
    "publicKey",
    "createdSurveyIds",
    "votedSurveyIds",
)
private val DOCUMENT_ID_PATTERN = Regex("^[A-Za-z0-9_-]{1,128}$")
private val DISPLAY_NAME_RANGE = 1..80
private val PUBLIC_KEY_RANGE = 80..256
private const val MAX_PROFILE_AGGREGATE_IDS = 2_000
private const val P256_FIELD_SIZE = 256
