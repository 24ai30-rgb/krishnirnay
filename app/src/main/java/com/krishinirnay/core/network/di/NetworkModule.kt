package com.krishinirnay.core.network.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.krishinirnay.BuildConfig
import com.krishinirnay.core.llm.ChatApiService
import com.krishinirnay.core.llm.local.LocalLlmApiService
import com.krishinirnay.core.network.DiseaseApiService
import com.krishinirnay.core.network.MarketApiService
import com.krishinirnay.core.network.PestApiService
import com.krishinirnay.core.network.RiskApiService
import com.krishinirnay.core.network.SensorApiService
import com.krishinirnay.core.network.WeatherApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

/**
 * Tags the long-read-timeout client/Retrofit used *only* for the Local LLM.
 * Local inference legitimately takes tens of seconds, while every other
 * endpoint (weather, market, sensors) answers in ~1-2s and should fail fast —
 * so the two cannot share one timeout budget.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LocalLlmClient

/**
 * Tags the client used to download the on-device LLM model file (500MB+) —
 * needs a much longer read timeout than any other request in the app (a slow
 * connection downloading hundreds of MB can easily exceed 15s between
 * progress, even though it is still succeeding), and no API-key header is
 * strictly required here, but reusing the same base client keeps this
 * consistent with every other request this app makes to its own server.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ModelDownloadClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * OkHttp's defaults are 10s for connect/read/write. That is right for the
     * fast endpoints but silently broke the Local LLM: the server can take
     * ~20s+ to return a real locally-generated answer (measured), so Android
     * gave up long before any reply existed and the farmer only ever saw a
     * failure. See LOCAL_LLM_IMPLEMENTATION_REPORT.md.
     */
    private const val DEFAULT_TIMEOUT_SECONDS = 15L
    private const val LOCAL_LLM_READ_TIMEOUT_SECONDS = 150L

    // A 500MB+ model over a slow/unreliable connection can legitimately take
    // many minutes — this is a one-time explicit download the farmer
    // triggers, not a request that should ever "time out" while still making
    // progress. Connect timeout stays short since a dead server should fail
    // fast either way.
    private const val MODEL_DOWNLOAD_READ_TIMEOUT_MINUTES = 30L

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
            .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                },
            )
        }

        return builder.build()
    }

    /**
     * Same interceptors as the default client, but with a read timeout sized
     * for local model inference rather than for a network API. Only the Local
     * LLM service uses it, so a slow model can never make weather/market/
     * sensor calls hang.
     */
    @Provides
    @Singleton
    @LocalLlmClient
    fun provideLocalLlmOkHttpClient(
        okHttpClient: OkHttpClient,
    ): OkHttpClient {
        return okHttpClient.newBuilder()
            .readTimeout(LOCAL_LLM_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(LOCAL_LLM_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    /**
     * No read/call timeout at all (0 = infinite in OkHttp) — a large download
     * must be judged by whether bytes are still arriving, not by a fixed
     * clock. [OnDeviceModelManager] is what actually protects the farmer
     * from a truly stalled connection, by checking real storage space first
     * and reporting a clear failure if the stream ends short of the
     * server's declared size.
     */
    @Provides
    @Singleton
    @ModelDownloadClient
    fun provideModelDownloadOkHttpClient(
        okHttpClient: OkHttpClient,
    ): OkHttpClient {
        return okHttpClient.newBuilder()
            .readTimeout(MODEL_DOWNLOAD_READ_TIMEOUT_MINUTES, TimeUnit.MINUTES)
            .callTimeout(0, TimeUnit.MINUTES)
            .build()
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

    @Provides
    @Singleton
    fun provideWeatherApiService(
        retrofit: Retrofit,
    ): WeatherApiService {

        return retrofit.create(
            WeatherApiService::class.java
        )
    }

    @Provides
    @Singleton
    fun provideMarketApiService(
        retrofit: Retrofit,
    ): MarketApiService {

        return retrofit.create(
            MarketApiService::class.java
        )
    }

    @Provides
    @Singleton
    @LocalLlmClient
    fun provideLocalLlmRetrofit(
        @LocalLlmClient okHttpClient: OkHttpClient,
        json: Json,
    ): Retrofit {

        return Retrofit.Builder()
            .baseUrl(
                BuildConfig.SERVER_BASE_URL
            )
            .client(okHttpClient)
            .addConverterFactory(
                json.asConverterFactory(
                    "application/json".toMediaType()
                )
            )
            .build()
    }

    @Provides
    @Singleton
    fun provideLocalLlmApiService(
        @LocalLlmClient retrofit: Retrofit,
    ): LocalLlmApiService {

        return retrofit.create(
            LocalLlmApiService::class.java
        )
    }
}