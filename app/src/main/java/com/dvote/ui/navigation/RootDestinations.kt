package com.dvote.ui.navigation

import kotlinx.serialization.Serializable

sealed interface RootDestinations {
    @Serializable
    data object Auth : RootDestinations
    @Serializable
    data object Main : RootDestinations
}