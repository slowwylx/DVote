package com.dvote.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ktx.storage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    fun bindFirebaseAuth(): FirebaseAuth {
        return Firebase.auth
    }

    @Provides
    fun bindFirebaseStorage(): FirebaseStorage {
        return Firebase.storage
    }

    @Provides
    fun bindFirebaseMessaging() = FirebaseMessaging.getInstance()
}