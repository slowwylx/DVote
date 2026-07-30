package com.dvote.feature.voting.profile

import com.dvote.core.model.UserProfile
import com.dvote.feature.voting.VotingUiError

data class ProfileUiState(
    val profile: UserProfile? = null,
    val isLoading: Boolean = true,
    val error: VotingUiError? = null,
)
