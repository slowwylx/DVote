package com.dvote.domain

import android.util.Base64
import com.dvote.data.repository.ProfileRepository
import com.dvote.data.repository.SurveyRepository
import com.dvote.domain.security.KeyStoreService
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class PlaceVoteUseCase @Inject constructor(
    private val surveyRepository: SurveyRepository,
    private val firebaseAuth: FirebaseAuth,
    private val keyStoreService: KeyStoreService,
    private val profileRepository: ProfileRepository
) {

    suspend operator fun invoke(
        surveyId: String,
        choiceIds: Set<String>
    ): VoteResult = withContext(Dispatchers.IO) {
        val uid = firebaseAuth.currentUser?.uid
            ?: return@withContext VoteResult.Error("User not authenticated")

        if (choiceIds.isEmpty()) {
            return@withContext VoteResult.Error("No choices selected")
        }

        val now = System.currentTimeMillis()
        val pubBytes = keyStoreService.generateKeyPairIfNeeded().public.encoded
        val pubBase64 = Base64.encodeToString(pubBytes, Base64.NO_WRAP)

        var count = 0
        for (choiceId in choiceIds) {
            val payload = "$surveyId|$choiceId|$now".toByteArray(Charsets.UTF_8)
            val sigBytes = keyStoreService.signData(payload)
            val signature = Base64.encodeToString(sigBytes, Base64.NO_WRAP)

            surveyRepository.submitSignedVote(
                surveyId  = surveyId,
                userId    = uid,
                choiceId  = choiceId,
                timestamp = now,
                signature = signature,
                publicKey = pubBase64
            )
            count++
        }

        profileRepository.addUserVotedSurvey(surveyId)

        VoteResult.Success("Successfully voted for $count choices")
    }

    sealed class VoteResult {
        data class Success(val message: String) : VoteResult()
        data class Error(val errorMessage: String) : VoteResult()
    }
}