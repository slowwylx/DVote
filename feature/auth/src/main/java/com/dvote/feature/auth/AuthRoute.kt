package com.dvote.feature.auth

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dvote.core.model.AuthRepairReason
import com.dvote.core.model.AuthState
import kotlinx.coroutines.launch

@Composable
fun AuthRoute(
    authState: AuthState,
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialClient = remember(context) {
        GoogleCredentialClient(CredentialManager.create(context))
    }
    val repairState = authState as? AuthState.RepairRequired

    LaunchedEffect(repairState?.userId, repairState?.reason) {
        if (
            repairState?.reason == AuthRepairReason.MISSING_PROFILE ||
            repairState?.reason == AuthRepairReason.MISSING_SIGNING_KEY
        ) {
            viewModel.repairProfile(
                userId = repairState.userId,
                reason = repairState.reason,
                automatic = true,
            )
        }
    }

    AuthScreen(
        uiState = uiState,
        isRepairRequired = repairState != null,
        onGoogleSignIn = {
            if (!viewModel.beginSignIn()) return@AuthScreen
            coroutineScope.launch {
                credentialClient.requestIdToken(context)
                    .onSuccess(viewModel::signIn)
                    .onFailure(viewModel::onCredentialError)
            }
        },
        onRepairProfile = {
            repairState?.let {
                viewModel.repairProfile(
                    userId = it.userId,
                    reason = it.reason,
                )
            }
        },
        onSignOut = viewModel::signOut,
        modifier = modifier,
    )
}
