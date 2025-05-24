package com.dvote.ui.main.navigation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dvote.R
import com.dvote.ui.main.navigation.toolbar.ToolbarItemData
import com.dvote.ui.main.navigation.toolbar.ToolbarViewModel
import com.dvote.ui.main.notifications.NotificationScreen
import com.dvote.ui.main.surveys.SurveysListScreen
import kotlinx.coroutines.launch

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
            toolbarItems[MainDestinations.SurveysList] ?: ToolbarItemData()
        )
    }

    val tabs = TabDestinations.entries
    val startDestination = TabDestinations.SURVEYS_LIST
    var selectedDestination by rememberSaveable { mutableIntStateOf(startDestination.ordinal) }

    val pagerState = rememberPagerState(
        initialPage = selectedDestination,
        pageCount = { tabs.size }
    )
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(navController) {
        navController.currentBackStackEntryFlow.collect { backStackEntry ->
            val currentRoute = backStackEntry.destination.route
            val destination = routeToDestination(currentRoute)
            toolbarState.value = toolbarItems[destination]
                ?: ToolbarItemData(
                    title = "Default",
                    navigationIcon = Icons.AutoMirrored.Default.ArrowBack
                )
        }
    }

    // whenever pager is swiped, update selectedDestination and navigate
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != selectedDestination) {
            selectedDestination = pagerState.currentPage
            navController.navigate(tabs[selectedDestination].destination) {
                popUpTo(navController.graph.startDestinationId)
                launchSingleTop = true
            }
        }
    }

    fun onTabClick(index: Int) {
        selectedDestination = index
        coroutineScope.launch {
            pagerState.animateScrollToPage(index)
        }
        navController.navigate(tabs[index].destination) {
            popUpTo(navController.graph.startDestinationId)
            launchSingleTop = true
        }
    }


    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = {
                navController.navigate(MainDestinations.CreateSurvey)
            }) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
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

        Column(modifier = Modifier.fillMaxSize()) {
            PrimaryTabRow(
                modifier = Modifier.padding(paddingValues),
                selectedTabIndex = selectedDestination,
                containerColor = Color.White
            ) {
                tabs.forEachIndexed { index, item ->
                    Tab(
                        text = { Text(text = stringResource(item.displayName)) },
                        selected = selectedDestination == index,
                        onClick = { onTabClick(index) }
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { page ->
                MainNavHost(
                    navController = navController,
                    paddingValues = paddingValues,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}


@Composable
fun MainNavHost(
    modifier: Modifier = Modifier,
    paddingValues: PaddingValues,
    navController: NavHostController,
){
    NavHost(
        modifier = Modifier,
        navController = navController,
        startDestination = MainDestinations.SurveysList
    ) {
        composable<MainDestinations.SurveysList> {
            SurveysListScreen(modifier = Modifier.padding(paddingValues))
        }
        composable<MainDestinations.SurveyHistory> {
            NotificationScreen(modifier = Modifier.padding(paddingValues))
        }
        composable<MainDestinations.CreatedSurveys> {

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
        MainDestinations.SurveysList::class.qualifiedName -> MainDestinations.SurveysList
        MainDestinations.SurveyHistory::class.qualifiedName -> MainDestinations.SurveyHistory
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