package com.dvote.ui.navigation

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dvote.core.designsystem.LoadingState
import com.dvote.core.model.AuthState
import com.dvote.feature.auth.AuthRoute
import com.dvote.ui.MainViewModel

@Composable
fun DVoteApp(
    modifier: Modifier = Modifier,
    mainViewModel: MainViewModel = hiltViewModel(),
) {
    val authState by mainViewModel.authState.collectAsStateWithLifecycle()
    val surfaceUsesDarkIcons = MaterialTheme.colorScheme.surface.luminance() > 0.5f

    DVoteSystemBarIcons(
        useDarkStatusBarIcons = authState !is AuthState.SignedIn &&
            surfaceUsesDarkIcons,
        useDarkNavigationBarIcons = surfaceUsesDarkIcons,
    )

    when (authState) {
        AuthState.Checking -> {
            LoadingState(
                modifier = modifier
                    .fillMaxSize()
                    .systemBarsPadding(),
            )
        }
        AuthState.SignedOut -> {
            AuthRoute(
                authState = authState,
                modifier = modifier
                    .fillMaxSize(),
            )
        }
        is AuthState.RepairRequired -> {
            AuthRoute(
                authState = authState,
                modifier = modifier
                    .fillMaxSize(),
            )
        }
        is AuthState.SignedIn -> {
            MainNavigation(
                modifier = modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
            )
        }
    }
}

@Composable
private fun DVoteSystemBarIcons(
    useDarkStatusBarIcons: Boolean,
    useDarkNavigationBarIcons: Boolean,
) {
    val activity = LocalActivity.current

    SideEffect {
        val window = activity?.window ?: return@SideEffect
        WindowCompat.getInsetsController(
            window,
            window.decorView,
        ).apply {
            isAppearanceLightStatusBars = useDarkStatusBarIcons
            isAppearanceLightNavigationBars = useDarkNavigationBarIcons
        }
    }
}
