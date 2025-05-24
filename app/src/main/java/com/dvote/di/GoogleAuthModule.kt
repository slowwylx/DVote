package com.dvote.di

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.dvote.BuildConfig
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object GoogleAuthModule {

    @Provides
    @Singleton
    fun provideGoogleSignInRequest(): GetCredentialRequest {
        return GetCredentialRequest.Builder()
            .addCredentialOption(provideGoogleSignInOption())
            .build()
    }

    @Provides
    @Singleton
    fun provideCredentialManager(@ApplicationContext context: Context): CredentialManager {
        return CredentialManager.create(context)
    }

    private fun provideGoogleSignInOption() : GetSignInWithGoogleOption{
        return GetSignInWithGoogleOption.Builder(BuildConfig.WEB_CLIENT_ID).build()
    }
}