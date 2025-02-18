package com.dvote.ui.main.navigation

import kotlinx.serialization.Serializable

sealed interface MainDestinations {
    @Serializable
    data object SurveysList : MainDestinations

    @Serializable
    data object Settings : MainDestinations

    @Serializable
    data object Survey : MainDestinations

    @Serializable
    data object CreateSurvey : MainDestinations

    @Serializable
    data object SurveyHistory : MainDestinations

    @Serializable
    data object Notifications : MainDestinations

    @Serializable
    data object Profile : MainDestinations

    @Serializable
    data object ParticipatedSurveys : MainDestinations

    @Serializable
    data object CreatedSurveys : MainDestinations

}