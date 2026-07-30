package com.dvote.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.dvote.R
import com.dvote.core.designsystem.DVoteBrandColors
import com.dvote.feature.voting.create.CreateSurveyRoute
import com.dvote.feature.voting.home.HomeRoute
import com.dvote.feature.voting.profile.ProfileRoute
import com.dvote.feature.voting.vote.VoteSurveyRoute
import kotlinx.serialization.serializer

@Composable
fun MainNavigation(
    modifier: Modifier = Modifier,
) {
    val backStack = rememberMainNavBackStack(MainNavKey.Home)
    val currentKey = backStack.lastOrNull() ?: MainNavKey.Home
    val canNavigateBack = backStack.size > 1
    val createSurveyDescription = stringResource(
        R.string.navigation_create_survey_action
    )

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            if (currentKey == MainNavKey.Home) {
                FloatingActionButton(
                    modifier = Modifier.semantics {
                        contentDescription = createSurveyDescription
                    },
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                    onClick = {
                        if (backStack.lastOrNull() != MainNavKey.CreateSurvey) {
                            backStack.add(MainNavKey.CreateSurvey)
                        }
                    }
                ) {
                    Text(
                        text = stringResource(
                            R.string.navigation_create_survey_symbol
                        ),
                        modifier = Modifier.clearAndSetSemantics { },
                    )
                }
            }
        },
        topBar = {
            MainTopAppBar(
                title = stringResource(currentKey.titleResource),
                canNavigateBack = canNavigateBack,
                showProfileAction = currentKey == MainNavKey.Home,
                onBack = { backStack.removeLastOrNull() },
                onProfile = {
                    if (backStack.lastOrNull() != MainNavKey.Profile) {
                        backStack.add(MainNavKey.Profile)
                    }
                },
            )
        },
    ) { paddingValues ->
        NavDisplay(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            transitionSpec = {
                slideInHorizontally(
                    initialOffsetX = { width -> width },
                    animationSpec = mainNavigationTween(),
                ) togetherWith slideOutHorizontally(
                    targetOffsetX = { width -> -width },
                    animationSpec = mainNavigationTween(),
                )
            },
            popTransitionSpec = {
                slideInHorizontally(
                    initialOffsetX = { width -> -width },
                    animationSpec = mainNavigationTween(),
                ) togetherWith slideOutHorizontally(
                    targetOffsetX = { width -> width },
                    animationSpec = mainNavigationTween(),
                )
            },
            predictivePopTransitionSpec = {
                slideInHorizontally(
                    initialOffsetX = { width -> -width },
                    animationSpec = mainNavigationTween(),
                ) togetherWith slideOutHorizontally(
                    targetOffsetX = { width -> width },
                    animationSpec = mainNavigationTween(),
                )
            },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry<MainNavKey.Home> {
                    HomeRoute(
                        modifier = Modifier.fillMaxSize(),
                        onSurveyClick = { surveyId ->
                            val key = MainNavKey.Survey(surveyId)
                            if (backStack.lastOrNull() != key) {
                                backStack.add(key)
                            }
                        },
                    )
                }

                entry<MainNavKey.Profile> {
                    ProfileRoute(modifier = Modifier.fillMaxSize())
                }

                entry<MainNavKey.Survey> { key ->
                    VoteSurveyRoute(
                        modifier = Modifier.fillMaxSize(),
                        surveyId = key.surveyId,
                    )
                }

                entry<MainNavKey.CreateSurvey> {
                    CreateSurveyRoute(
                        modifier = Modifier.fillMaxSize(),
                        onSurveyCreated = { surveyId ->
                            backStack.removeLastOrNull()
                            backStack.add(MainNavKey.Survey(surveyId))
                        },
                    )
                }
            },
        )
    }
}

private fun <T> mainNavigationTween() = tween<T>(
    durationMillis = MainNavigationTransitionDurationMillis,
    easing = FastOutSlowInEasing,
)

private const val MainNavigationTransitionDurationMillis = 300

@Composable
internal fun rememberMainNavBackStack(
    vararg elements: MainNavKey,
): NavBackStack<MainNavKey> {
    return rememberSerializable(serializer = serializer()) {
        NavBackStack(*elements)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun MainTopAppBar(
    title: String,
    canNavigateBack: Boolean,
    showProfileAction: Boolean,
    onBack: () -> Unit,
    onProfile: () -> Unit,
) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineMedium,
            )
        },
        navigationIcon = {
            if (canNavigateBack) {
                TextButton(
                    onClick = onBack,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = DVoteBrandColors.topAppBarAction,
                    ),
                ) {
                    Text(stringResource(R.string.navigation_back))
                }
            }
        },
        actions = {
            if (showProfileAction) {
                TextButton(
                    onClick = onProfile,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = DVoteBrandColors.topAppBarAction,
                    ),
                ) {
                    Text(stringResource(R.string.navigation_profile))
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = DVoteBrandColors.topAppBarContainer,
            scrolledContainerColor = DVoteBrandColors.topAppBarContainer,
            navigationIconContentColor = DVoteBrandColors.onTopAppBar,
            titleContentColor = DVoteBrandColors.onTopAppBar,
            actionIconContentColor = DVoteBrandColors.onTopAppBar,
        ),
    )
}

@get:StringRes
private val MainNavKey.titleResource: Int
    get() = when (this) {
        MainNavKey.Home -> R.string.navigation_surveys_title
        MainNavKey.Profile -> R.string.navigation_profile_title
        MainNavKey.CreateSurvey -> R.string.navigation_create_survey_title
        is MainNavKey.Survey -> R.string.navigation_vote_title
    }
