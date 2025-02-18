package com.dvote.ui.auth.signin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dvote.ui.SessionService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val service: SessionService
) : ViewModel() {


    fun loginWithDiia() {
        viewModelScope.launch(Dispatchers.IO) {
            delay(3000)
            service.login()
        }
    }


}