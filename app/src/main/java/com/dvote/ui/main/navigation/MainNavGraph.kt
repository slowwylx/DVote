package com.dvote.ui.main.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dvote.extensions.toPainter
import com.dvote.ui.main.navigation.toolbar.ToolbarItemData
import com.dvote.ui.main.navigation.toolbar.ToolbarViewModel
import com.dvote.ui.main.notifications.NotificationScreen
import com.dvote.ui.main.surveys.SurveysListScreen

@Composable
fun MainNavGraph(
    modifier: Modifier = Modifier,
) {

    val navController = rememberNavController()
    val toolbarViewModel = hiltViewModel<ToolbarViewModel>()

    val toolbarItems = toolbarViewModel.toolbarItems.collectAsState().value

    val toolbarState = remember {
        mutableStateOf(
            toolbarItems[MainDestinations.SurveysList] ?: ToolbarItemData()
        )
    }

    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.collect { backStackEntry ->
            val currentRoute = backStackEntry.destination.route
            val destination = routeToDestination(currentRoute)
            val newToolbarState = toolbarItems[destination] ?: ToolbarItemData(
                title = "Default",
                navigationIcon = Icons.AutoMirrored.Default.ArrowBack
            )
            toolbarState.value = newToolbarState
        }
    }

    Scaffold(
        topBar = {
            Toolbar(
                title = toolbarState.value.title ?: "",
                navigationIcon = toolbarState.value.navigationIcon ?: Icons.AutoMirrored.Default.ArrowBack,
                actionIcon = toolbarState.value.actionIcon,
                onNavigationClick = {
                    val destination = handleLeadingIconClick<MainDestinations?>(navController.currentDestination?.route)
                    if (destination != null) {
                        navController.navigate(destination)
                    } else {
                        navController.navigateUp()
                    }
                },
                onActionClick = {
                    val destination = handleTrailingIconClick<MainDestinations?>(navController.currentDestination?.route)
                    if (destination != null) {
                        navController.navigate(destination)
                    }
                }
            )
        },
    ) { paddingValues ->

        NavHost(
            navController = navController,
            startDestination = MainDestinations.SurveysList
        ) {
            composable<MainDestinations.SurveysList> {
                SurveysListScreen(modifier = Modifier.padding(paddingValues))
            }
            composable<MainDestinations.Notifications> {
                NotificationScreen(modifier = Modifier.padding(paddingValues))
            }
        }
    }

}

@Composable
fun Toolbar(
    title: String,
    navigationIcon: ImageVector,
    actionIcon: ImageVector? = null,
    onNavigationClick: () -> Unit,
    onActionClick: () -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    CenterAlignedTopAppBar(
        title = {
            Text(title)
        },
        navigationIcon = {
            IconButton(
                onClick = onNavigationClick
            ) {
                Icon(
                    painter = navigationIcon.toPainter(),
                    contentDescription = null,
                )
            }
        },
        actions = {
            actionIcon?.let {
                IconButton(
                    onClick = onActionClick
                ) {
                    Icon(
                        painter = it.toPainter(),
                        contentDescription = null,
                    )
                }
            }
        },
        scrollBehavior = scrollBehavior
    )
}

fun routeToDestination(route: String?): MainDestinations? {
    return when (route) {
        MainDestinations.SurveysList::class.qualifiedName -> MainDestinations.SurveysList
        MainDestinations.Notifications::class.qualifiedName -> MainDestinations.Notifications
        MainDestinations.Settings::class.qualifiedName -> MainDestinations.Settings
        else -> null
    }
}


inline fun <reified T> handleLeadingIconClick(route: String?): T? {
    return when (route) {
        MainDestinations.SurveysList::class.qualifiedName -> MainDestinations.Notifications as? T
        else -> null
    }
}

inline fun <reified T> handleTrailingIconClick(route: String?): T? {
    return when (route) {
        MainDestinations.SurveysList::class.qualifiedName -> MainDestinations.Notifications as? T
        else -> null
    }
}