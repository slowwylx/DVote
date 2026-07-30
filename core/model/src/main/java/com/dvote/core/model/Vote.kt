package com.dvote.core.model

import kotlinx.collections.immutable.ImmutableList

data class VoteSubmission(
    val operationId: String,
    val surveyId: String,
    val keyId: String,
    val optionIds: ImmutableList<String>,
    val signedAt: Long,
    val canonicalPayload: String,
    val publicKey: String,
    val signature: String,
    val commitmentHash: String,
)

data class VoteReceipt(
    val surveyId: String,
    val receiptId: String,
    val keyId: String,
    val commitmentHash: String,
    val acceptedAt: Long,
    val canonicalPayload: String,
    val publicKey: String,
    val signature: String,
)

fun VoteReceipt.toPortableJson(): String {
    return """
        {
          "format": "dvote-verification-bundle-v2",
          "surveyId": "${surveyId.jsonEscaped()}",
          "receiptId": "${receiptId.jsonEscaped()}",
          "keyId": "${keyId.jsonEscaped()}",
          "acceptedAt": $acceptedAt,
          "commitmentHash": "${commitmentHash.jsonEscaped()}",
          "canonicalPayload": "${canonicalPayload.jsonEscaped()}",
          "publicKey": "${publicKey.jsonEscaped()}",
          "signature": "${signature.jsonEscaped()}"
        }
    """.trimIndent()
}

private fun String.jsonEscaped(): String = buildString {
    this@jsonEscaped.forEach { character ->
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> {
                if (character.code < 0x20) {
                    append("\\u")
                    append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
    }
}
