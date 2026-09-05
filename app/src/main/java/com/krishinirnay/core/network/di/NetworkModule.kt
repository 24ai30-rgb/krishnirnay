package com.krishinirnay.core.network.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.krishinirnay.BuildConfig
import com.krishinirnay.core.llm.ChatApiService
import com.krishinirnay.core.network.DiseaseApiService
import com.krishinirnay.core.network.PestApiService
import com.krishinirnay.core.network.RiskApiService
import com.krishinirnay.core.network.SensorApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json {
        return Json {
            ignoreUnknownKeys = true
        }
    }

    @Provides
    @Singleton
    fun provideApiKeyInterceptor(): Interceptor {
        return Interceptor { chain ->

            val request = chain.request()
                .newBuilder()
                .addHeader(
                    "X-API-Key",
                    BuildConfig.SERVER_API_KEY,
                )
                .build()

            chain.proceed(request)
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        apiKeyInterceptor: Interceptor,
    ): OkHttpClient {

        val builder = OkHttpClient.Builder()
            .addInterceptor(apiKeyInterceptor)

        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                },
            )
        }

        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        json: Json,
    ): Retrofit {

        val contentType =
            "application/json".toMediaType()

        return Retrofit.Builder()
            .baseUrl(
                BuildConfig.SERVER_BASE_URL
            )
            .client(okHttpClient)
            .addConverterFactory(
                json.asConverterFactory(
                    contentType
                )
            )
            .build()
    }

    @Provides
    @Singleton
    fun provideDiseaseApiService(
        retrofit: Retrofit,
    ): DiseaseApiService {

        return retrofit.create(
            DiseaseApiService::class.java
        )
    }

    @Provides
    @Singleton
    fun provideRiskApiService(
        retrofit: Retrofit,
    ): RiskApiService {

        return retrofit.create(
            RiskApiService::class.java
        )
    }

    @Provides
    @Singleton
    fun provideSensorApiService(
        retrofit: Retrofit,
    ): SensorApiService {

        return retrofit.create(
            SensorApiService::class.java
        )
    }

    @Provides
    @Singleton
    fun providePestApiService(
        retrofit: Retrofit,
    ): PestApiService {

        return retrofit.create(
            PestApiService::class.java
        )
    }

    @Provides
    @Singleton
    fun provideChatApiService(
        retrofit: Retrofit,
    ): ChatApiService {

        return retrofit.create(
            ChatApiService::class.java
        )
    }
}