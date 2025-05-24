package com.dvote.ui.main.navigation

import kotlinx.serialization.Serializable

sealed interface MainDestinations {
    @Serializable
    data object Home : MainDestinations

    @Serializable
    data object Survey : MainDestinations

    @Serializable
    data object CreateSurvey : MainDestinations

    @Serializable
    data object Profile : MainDestinations

}