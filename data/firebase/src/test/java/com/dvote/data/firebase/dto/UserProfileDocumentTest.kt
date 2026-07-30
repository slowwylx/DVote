package com.dvote.data.firebase.dto

import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.text.Normalizer
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserProfileDocumentTest {
    private val publicKey = KeyPairGenerator.getInstance("EC")
        .apply { initialize(ECGenParameterSpec("secp256r1")) }
        .generateKeyPair()
        .public
        .encoded
        .let(Base64.getEncoder()::encodeToString)

    @Test
    fun validProfileMapsOnlyForTheAuthenticatedUser() {
        val profile = validProfile().toUserProfileOrNull("user-1")

        assertEquals("user-1", profile?.id)
        assertEquals("Alice", profile?.displayName)
        assertEquals(listOf("survey-1"), profile?.createdSurveyIds)
        assertEquals(listOf("survey-2"), profile?.votedSurveyIds)
        assertNull(validProfile().toUserProfileOrNull("user-2"))
        assertEquals(
            "user-1",
            (validProfile() - "publicKey").toUserProfileOrNull("user-1")?.id,
        )
    }

    @Test
    fun malformedOrUnexpectedFieldsFailClosed() {
        assertNull(
            (validProfile() + ("unexpected" to true))
                .toUserProfileOrNull("user-1")
        )
        assertNull(
            (validProfile() + ("createdSurveyIds" to null))
                .toUserProfileOrNull("user-1")
        )
        assertNull(
            (validProfile() + ("votedSurveyIds" to listOf("invalid/path")))
                .toUserProfileOrNull("user-1")
        )
        assertNull(
            (validProfile() + ("publicKey" to "not-a-key"))
                .toUserProfileOrNull("user-1")
        )
    }

    @Test
    fun displayNameMustBeBoundedTrimmedAndNormalized() {
        val decomposed = Normalizer.normalize("Café", Normalizer.Form.NFD)

        assertNull(
            (validProfile() + ("displayName" to " Alice"))
                .toUserProfileOrNull("user-1")
        )
        assertNull(
            (validProfile() + ("displayName" to decomposed))
                .toUserProfileOrNull("user-1")
        )
        assertNull(
            (validProfile() + ("displayName" to "A".repeat(81)))
                .toUserProfileOrNull("user-1")
        )
    }

    private fun validProfile(): Map<String, Any?> = mapOf(
        "id" to "user-1",
        "displayName" to "Alice",
        "publicKey" to publicKey,
        "createdSurveyIds" to listOf("survey-1"),
        "votedSurveyIds" to listOf("survey-2"),
    )
}
