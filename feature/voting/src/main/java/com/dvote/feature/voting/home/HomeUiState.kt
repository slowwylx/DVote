package com.dvote.feature.voting.home

import com.dvote.core.model.Survey
import com.dvote.feature.voting.VotingUiError
import kotlinx.collections.immutable.ImmutableList

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Content(
        val surveys: ImmutableList<Survey>,
        val canLoadMore: Boolean = false,
        val isLoadingMore: Boolean = false,
        val loadMoreError: VotingUiError? = null,
    ) : HomeUiState

    data class Error(val error: VotingUiError) : HomeUiState
}
