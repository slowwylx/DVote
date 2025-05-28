package com.dvote.ui.main.navigation

import kotlinx.serialization.Serializable

sealed interface MainDestinations {
    @Serializable
    data object Home : MainDestinations

    @Serializable
    data class Survey(val surveyId: String) : MainDestinations

    @Serializable
    data object CreateSurvey : MainDestinations

    @Serializable
    data object Profile : MainDestinations

}