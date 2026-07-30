package com.dvote.domain.repository

import com.dvote.core.model.CreateSurveyRequest
import com.dvote.core.model.VoteReceipt
import com.dvote.core.model.VoteSubmission
import kotlinx.coroutines.flow.Flow

interface VotingRepository {
    fun observeHasVoted(surveyId: String): Flow<Boolean>

    suspend fun createSurvey(request: CreateSurveyRequest): Result<String>

    suspend fun submitVote(submission: VoteSubmission): Result<VoteReceipt>
}
