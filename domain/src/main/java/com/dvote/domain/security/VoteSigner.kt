package com.dvote.domain.security

import com.dvote.core.model.VotingKey

interface VoteSigner {
    suspend fun votingKey(userId: String): VotingKey

    suspend fun replaceVotingKey(userId: String): VotingKey

    suspend fun signBase64(userId: String, payload: String): String

    suspend fun sha256Base64(payload: String): String
}

fun buildVotingKeyRegistrationPayload(
    userId: String,
    votingKey: VotingKey,
    signedAt: Long,
): String {
    return listOf(
        "action=registerVotingKey",
        "user=$userId",
        "device=${votingKey.deviceId}",
        "key=${votingKey.keyId}",
        "signedAt=$signedAt",
    ).joinToString("|")
}
