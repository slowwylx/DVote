package com.dvote.feature.voting.profile

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dvote.core.designsystem.DVoteCard
import com.dvote.core.designsystem.DVoteScreen
import com.dvote.core.designsystem.ErrorState
import com.dvote.core.designsystem.LoadingState
import com.dvote.core.designsystem.PrimaryAction
import com.dvote.feature.voting.R
import com.dvote.feature.voting.messageResource

@Composable
fun ProfileRoute(
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value

    ProfileScreen(
        uiState = state,
        onSignOut = viewModel::signOut,
        modifier = modifier,
    )
}

@Composable
fun ProfileScreen(
    uiState: ProfileUiState,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        uiState.isLoading -> LoadingState(modifier)
        uiState.error != null -> ErrorState(
            message = stringResource(uiState.error.messageResource),
            modifier = modifier,
        )
        else -> DVoteScreen(modifier = modifier) {
            DVoteCard {
                Text(
                    text = uiState.profile?.displayName
                        ?: stringResource(R.string.profile_anonymous_voter),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.profile_created_surveys,
                        uiState.profile?.createdSurveyIds?.size ?: 0,
                        uiState.profile?.createdSurveyIds?.size ?: 0,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.profile_participated_surveys,
                        uiState.profile?.votedSurveyIds?.size ?: 0,
                        uiState.profile?.votedSurveyIds?.size ?: 0,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            PrimaryAction(
                text = stringResource(R.string.profile_sign_out),
                onClick = onSignOut,
            )
        }
    }
}
