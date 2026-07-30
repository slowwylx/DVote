package com.dvote.feature.voting.vote

import com.dvote.core.model.Survey
import com.dvote.core.model.SurveyResult
import com.dvote.core.model.VoteReceipt
import com.dvote.feature.voting.VotingUiError
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

data class VoteSurveyUiState(
    val survey: Survey? = null,
    val selectedOptionIds: ImmutableSet<String> = persistentSetOf(),
    val receipt: VoteReceipt? = null,
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val isSurveyClosed: Boolean = false,
    val hasVoted: Boolean = false,
    val isVoteStatusLoading: Boolean = true,
    val voteStatusError: VotingUiError? = null,
    val finalResult: SurveyResult? = null,
    val isFinalResultLoading: Boolean = false,
    val finalResultError: VotingUiError? = null,
    val error: VotingUiError? = null,
)
