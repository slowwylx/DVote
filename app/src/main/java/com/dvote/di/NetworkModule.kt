package com.dvote.di

import com.dvote.data.network.api.AuthApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
object NetworkModule {
    @Provides
    @Singleton
    fun provideGsonConverterFactory(): GsonConverterFactory {
        return GsonConverterFactory.create()
    }
    @Provides
    @Singleton
    fun provideOkHttpClient(
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        gsonConverterFactory: GsonConverterFactory
    ): Retrofit {
        return Retrofit.Builder()
            .client(okHttpClient)
            .addConverterFactory(gsonConverterFactory)
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthApi(
        retrofit: Retrofit
    ): AuthApi {
        return retrofit.create(AuthApi::class.java)
    }

}