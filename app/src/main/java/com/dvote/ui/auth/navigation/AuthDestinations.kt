package com.dvote.ui.auth.navigation

import kotlinx.serialization.Serializable

sealed interface AuthDestinations {
    @Serializable
    data object SignIn : AuthDestinations
}