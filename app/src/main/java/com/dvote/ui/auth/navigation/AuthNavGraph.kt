package com.dvote.ui.auth.navigation

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dvote.ui.auth.signin.SignInScreen

@Composable
fun AuthNavGraph(
    modifier: Modifier = Modifier,
) {

    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = AuthDestinations.SignIn
    ) {
        composable<AuthDestinations.SignIn> {
            SignInScreen(modifier = modifier)
        }
    }

}