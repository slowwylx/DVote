package com.dvote.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface MainNavKey : NavKey {
    @Serializable
    data object Home : MainNavKey

    @Serializable
    data class Survey(val surveyId: String) : MainNavKey

    @Serializable
    data object CreateSurvey : MainNavKey

    @Serializable
    data object Profile : MainNavKey
}
