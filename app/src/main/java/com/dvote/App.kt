package com.dvote

import android.app.Application
import com.dvote.data.firebase.FirebaseSecurityInitializer
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseSecurityInitializer.installAppCheck()
    }
}
