package com.dvote.di

import com.dvote.data.security.KeyStoreManager
import com.dvote.domain.security.KeyStoreService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {
    @Provides
    @Singleton
    fun provideKeyStoreService(): KeyStoreService = KeyStoreManager
}