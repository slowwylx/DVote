package com.dvote.core.model

import org.junit.Assert.assertTrue
import org.junit.Test

class VoteReceiptTest {
    @Test
    fun portableBundleEscapesJsonControlAndDelimiterCharacters() {
        val json = VoteReceipt(
            surveyId = "survey-1",
            receiptId = "a".repeat(64),
            keyId = "b".repeat(64),
            commitmentHash = "hash",
            acceptedAt = 123L,
            canonicalPayload = "quote=\" slash=\\ newline=\n tab=\t control=\u0001",
            publicKey = "public-key",
            signature = "signature",
        ).toPortableJson()

        assertTrue(json.contains("\"format\": \"dvote-verification-bundle-v2\""))
        assertTrue(
            json.contains(
                "\"canonicalPayload\": " +
                    "\"quote=\\\" slash=\\\\ newline=\\n tab=\\t control=\\u0001\""
            )
        )
        assertTrue(json.contains("\"acceptedAt\": 123"))
    }
}
