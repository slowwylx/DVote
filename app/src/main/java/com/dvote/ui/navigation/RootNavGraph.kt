package com.dvote.ui.navigation


import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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


    val infiniteTransition = rememberInfiniteTransition(label = "")

    val color1 by infiniteTransition.animateColor(
        initialValue = Color(0xFFFCECD8),
        targetValue = Color(0xFFFFCB9E),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000),
            repeatMode = RepeatMode.Reverse
        ), label = ""
    )

    val color2 by infiniteTransition.animateColor(
        initialValue = Color(0xFFFFCB9E),
        targetValue = Color(0xFFFCECD8),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000),
            repeatMode = RepeatMode.Reverse
        ), label = ""
    )

    NavHost(
        navController = navController,
        startDestination = RootDestinations.Auth
    ) {
        composable<RootDestinations.Auth> {
            AuthNavGraph(
                modifier = Modifier
                    .fillMaxSize()
                    .background(brush = Brush.linearGradient(listOf(color1, color2)))
                    .systemBarsPadding()
                    .navigationBarsPadding(),
            )
        }
        composable<RootDestinations.Main> {
            MainNavGraph(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .background(color = Color(0xFFFCECD8))
                    .navigationBarsPadding()
            )
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