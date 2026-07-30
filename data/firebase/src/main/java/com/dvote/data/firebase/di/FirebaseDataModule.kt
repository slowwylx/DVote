package com.dvote.data.firebase.di

import android.content.Context
import androidx.credentials.CredentialManager
import com.dvote.data.firebase.repository.FirebaseAuthRepository
import com.dvote.data.firebase.repository.FirebaseSurveyRepository
import com.dvote.data.firebase.repository.FirebaseUserRepository
import com.dvote.data.firebase.repository.FirebaseVotingRepository
import com.dvote.data.firebase.security.AndroidKeyStoreVoteSigner
import com.dvote.domain.repository.AuthRepository
import com.dvote.domain.repository.SurveyRepository
import com.dvote.domain.repository.UserRepository
import com.dvote.domain.repository.VotingRepository
import com.dvote.domain.security.VoteSigner
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class KeyStoreDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ComputationDispatcher

@Module
@InstallIn(SingletonComponent::class)
object FirebaseDataModule {
    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    fun provideFunctions(): FirebaseFunctions = FirebaseFunctions.getInstance()

    @Provides
    @Singleton
    fun provideCredentialManager(@ApplicationContext context: Context): CredentialManager {
        return CredentialManager.create(context)
    }

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemUTC()

    @Provides
    @KeyStoreDispatcher
    fun provideKeyStoreDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @ComputationDispatcher
    fun provideComputationDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    fun provideVoteSigner(impl: AndroidKeyStoreVoteSigner): VoteSigner = impl

    @Provides
    @Singleton
    fun provideAuthRepository(impl: FirebaseAuthRepository): AuthRepository = impl

    @Provides
    @Singleton
    fun provideUserRepository(impl: FirebaseUserRepository): UserRepository = impl

    @Provides
    @Singleton
    fun provideSurveyRepository(impl: FirebaseSurveyRepository): SurveyRepository = impl

    @Provides
    @Singleton
    fun provideVotingRepository(impl: FirebaseVotingRepository): VotingRepository = impl
}
