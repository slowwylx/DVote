package com.dvote.ui.main.navigation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dvote.ui.main.create_survey.CreateSurveyScreen
import com.dvote.ui.main.home.HomeScreen
import com.dvote.ui.main.navigation.toolbar.ToolbarItemData
import com.dvote.ui.main.navigation.toolbar.ToolbarViewModel
import com.dvote.ui.main.profile.ProfileScreen
import com.dvote.ui.main.survey.SurveyViewScreen

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainNavGraph(
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val toolbarViewModel = hiltViewModel<ToolbarViewModel>()
    val toolbarItems = toolbarViewModel.toolbarItems.collectAsState().value

    val toolbarState = remember {
        mutableStateOf(
            toolbarItems[MainDestinations.Home] ?: ToolbarItemData()
        )
    }

    val fabState = remember {
        mutableStateOf(false)
    }

    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.collect { backStackEntry ->
            val currentRoute = backStackEntry.destination.route
            val destination = routeToDestination(currentRoute)
            fabState.value = destination == MainDestinations.Home
            toolbarState.value = toolbarItems[destination]
                ?: ToolbarItemData(
                    title = "Default",
                    navigationIcon = Icons.AutoMirrored.Default.ArrowBack
                )
        }
    }


    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            if (fabState.value) {
                FloatingActionButton(
                    onClick = {
                        navController.navigate(MainDestinations.CreateSurvey)
                    }
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                }
            }
        },
        topBar = {
            Toolbar(
                title = toolbarState.value.title.orEmpty(),
                navigationIcon = toolbarState.value.navigationIcon
                    ?: Icons.AutoMirrored.Default.ArrowBack,
                actionIcon = toolbarState.value.actionIcon,
                onNavigationClick = {
                    val destination =
                        handleLeadingIconClick<MainDestinations?>(
                            navController.currentDestination?.route
                        )
                    if (destination != null) navController.navigate(destination)
                    else navController.navigateUp()
                },
                onActionClick = {
                    handleTrailingIconClick<MainDestinations?>(
                        navController.currentDestination?.route
                    )?.let { navController.navigate(it) }
                }
            )
        },
    ) { paddingValues ->

        MainNavHost(
            navController = navController,
            paddingValues = paddingValues,
            modifier = Modifier.fillMaxSize()
        )

    }
}


@Composable
fun MainNavHost(
    modifier: Modifier = Modifier,
    paddingValues: PaddingValues,
    navController: NavHostController,
) {
    NavHost(
        modifier = Modifier,
        navController = navController,
        startDestination = MainDestinations.Home
    ) {
        composable<MainDestinations.Home> {
            HomeScreen(
                modifier = Modifier.padding(paddingValues)
            )
        }
        composable<MainDestinations.Profile> {
            ProfileScreen(
                modifier = Modifier.padding(paddingValues)
            )
        }
        composable<MainDestinations.Survey> {
            SurveyViewScreen(
                modifier = Modifier.padding(paddingValues)
            )
        }
        composable<MainDestinations.CreateSurvey> {
            CreateSurveyScreen(
                modifier = Modifier.padding(paddingValues)
            )
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
        }, navigationIcon = {
            IconButton(
                onClick = onNavigationClick
            ) {
                Icon(
                    imageVector = navigationIcon,
                    contentDescription = null,
                )
            }
        }, actions = {
            actionIcon?.let {
                IconButton(
                    onClick = onActionClick
                ) {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                    )
                }
            }
        }, colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = Color.White,
        ), scrollBehavior = scrollBehavior
    )
}

fun routeToDestination(route: String?): MainDestinations? {
    return when (route) {
        MainDestinations.Home::class.qualifiedName -> MainDestinations.Home
        MainDestinations.Profile::class.qualifiedName -> MainDestinations.Profile
        MainDestinations.CreateSurvey::class.qualifiedName -> MainDestinations.CreateSurvey
        MainDestinations.Survey::class.qualifiedName -> MainDestinations.Survey
        else -> null
    }
}


inline fun <reified T> handleLeadingIconClick(route: String?): T? {
    return when (route) {
        MainDestinations.Home::class.qualifiedName -> MainDestinations.Profile as? T
        else -> null
    }
}

inline fun <reified T> handleTrailingIconClick(route: String?): T? {
    return when (route) {
        else -> null
    }
}