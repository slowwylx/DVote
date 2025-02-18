package com.dvote.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionService @Inject constructor(

) {
    private val _isUserLoggedIn = MutableStateFlow<Boolean?>(null)
    val isUserLoggedIn = _isUserLoggedIn.asStateFlow()

    fun login() {
        _isUserLoggedIn.value = true
    }

    suspend fun logout() {
        _isUserLoggedIn.value = false
    }
}