package com.dvote.ui.navigation


import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.dvote.ui.MainViewModel
import com.dvote.ui.auth.navigation.AuthNavGraph
import com.dvote.ui.main.navigation.MainNavGraph

@Composable
fun RootNavGraph(
    navController: NavHostController,
    mainViewModel: MainViewModel = hiltViewModel()
) {

    NavHost(
        navController = navController,
        startDestination = RootDestinations.Auth
    ) {
        composable<RootDestinations.Auth> {
            AuthNavGraph()
        }
        composable<RootDestinations.Main> {
            MainNavGraph()
        }
    }


    LaunchedEffect(true) {
        mainViewModel.uiState.collect{
            when(it){
                true -> {
                    navController.navigate(RootDestinations.Main)
                }
                false -> {
                    navController.navigate(RootDestinations.Auth)
                }
                null -> {
                    // Do nothing
                }
            }
        }
    }


}