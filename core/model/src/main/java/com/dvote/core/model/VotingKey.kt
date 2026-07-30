package com.dvote.core.model

data class VotingKey(
    val keyId: String,
    val deviceId: String,
    val publicKey: String,
)
