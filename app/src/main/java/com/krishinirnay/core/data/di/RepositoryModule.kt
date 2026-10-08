package com.krishinirnay.core.data.di

import com.krishinirnay.core.data.composite.AlertGenerator
import com.krishinirnay.core.data.composite.DefaultFieldStateRepository
import com.krishinirnay.core.data.composite.DefaultMarketRepository
import com.krishinirnay.core.data.composite.DefaultWeatherRepository
import com.krishinirnay.core.data.firebase.FirebaseAuthRepositoryImpl
import com.krishinirnay.core.data.local.AppPreferences
import com.krishinirnay.core.data.local.FarmerProfileRepositoryImpl
import com.krishinirnay.core.data.local.FeedbackRepositoryImpl
import com.krishinirnay.core.data.mock.MockFieldStateRepositoryImpl
import com.krishinirnay.core.data.mock.MockMarketRepositoryImpl
import com.krishinirnay.core.data.mock.MockSchemesRepositoryImpl
import com.krishinirnay.core.data.mock.MockWeatherRepositoryImpl
import com.krishinirnay.core.data.network.CropHealthRepositoryImpl
import com.krishinirnay.core.data.network.LiveFieldStateRepositoryImpl
import com.krishinirnay.core.data.network.LiveMarketRepositoryImpl
import com.krishinirnay.core.data.network.LiveWeatherRepositoryImpl
import com.krishinirnay.core.data.network.PestRepositoryImpl
import com.krishinirnay.core.data.network.RiskRepositoryImpl
import com.krishinirnay.core.data.repository.AlertsRepository
import com.krishinirnay.core.data.repository.AuthRepository
import com.krishinirnay.core.data.repository.CropHealthRepository
import com.krishinirnay.core.data.repository.FeedbackRepository
import com.krishinirnay.core.data.repository.FieldStateRepository
import com.krishinirnay.core.data.repository.MarketRepository
import com.krishinirnay.core.data.repository.PestRepository
import com.krishinirnay.core.data.repository.ProfileRepository
import com.krishinirnay.core.data.repository.RiskRepository
import com.krishinirnay.core.data.repository.SchemesRepository
import com.krishinirnay.core.data.repository.SettingsRepository
import com.krishinirnay.core.data.repository.WeatherRepository
import com.krishinirnay.core.llm.local.AiProviderCoordinator
import com.krishinirnay.core.llm.local.LocalLlmRepository
import com.krishinirnay.core.llm.local.OnDeviceLlmProvider
import com.krishinirnay.core.llm.local.MediaPipeOnDeviceLlmProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/** Tags a Mock Mode binding for a [DefaultFieldStateRepository]-style switcher's constructor. Reused across FieldState/Weather/Market — it's a plain marker, not tied to one repository type. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MockSource

/** Tags a Live Mode binding for a [DefaultFieldStateRepository]-style switcher's constructor. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LiveSource

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    @MockSource
    abstract fun bindMockFieldStateRepository(
        impl: MockFieldStateRepositoryImpl,
    ): FieldStateRepository

    @Binds
    @Singleton
    @LiveSource
    abstract fun bindLiveFieldStateRepository(
        impl: LiveFieldStateRepositoryImpl,
    ): FieldStateRepository

    /**
     * The one [FieldStateRepository] every screen actually injects —
     * switches between [MockSource]/[LiveSource] at runtime per
     * [SettingsRepository.appMode]. See [DefaultFieldStateRepository].
     */
    @Binds
    @Singleton
    abstract fun bindFieldStateRepository(
        impl: DefaultFieldStateRepository,
    ): FieldStateRepository

    @Binds
    @Singleton
    @MockSource
    abstract fun bindMockWeatherRepository(
        impl: MockWeatherRepositoryImpl,
    ): WeatherRepository

    @Binds
    @Singleton
    @LiveSource
    abstract fun bindLiveWeatherRepository(
        impl: LiveWeatherRepositoryImpl,
    ): WeatherRepository

    @Binds
    @Singleton
    abstract fun bindWeatherRepository(
        impl: DefaultWeatherRepository,
    ): WeatherRepository

    @Binds
    @Singleton
    @MockSource
    abstract fun bindMockMarketRepository(
        impl: MockMarketRepositoryImpl,
    ): MarketRepository

    @Binds
    @Singleton
    @LiveSource
    abstract fun bindLiveMarketRepository(
        impl: LiveMarketRepositoryImpl,
    ): MarketRepository

    @Binds
    @Singleton
    abstract fun bindMarketRepository(
        impl: DefaultMarketRepository,
    ): MarketRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        impl: AppPreferences,
    ): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindAlertsRepository(
        impl: AlertGenerator,
    ): AlertsRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        impl: FirebaseAuthRepositoryImpl,
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindCropHealthRepository(
        impl: CropHealthRepositoryImpl,
    ): CropHealthRepository

    @Binds
    @Singleton
    abstract fun bindRiskRepository(
        impl: RiskRepositoryImpl,
    ): RiskRepository

    @Binds
    @Singleton
    abstract fun bindPestRepository(
        impl: PestRepositoryImpl,
    ): PestRepository

    @Binds
    @Singleton
    abstract fun bindSchemesRepository(
        impl: MockSchemesRepositoryImpl,
    ): SchemesRepository

    @Binds
    @Singleton
    abstract fun bindProfileRepository(
        impl: FarmerProfileRepositoryImpl,
    ): ProfileRepository

    @Binds
    @Singleton
    abstract fun bindLocalLlmRepository(
        impl: AiProviderCoordinator,
    ): LocalLlmRepository

    /** Real LiteRT-LM on-device engine — reports MODEL_MISSING until the farmer downloads the model, see MediaPipeOnDeviceLlmProvider. */
    @Binds
    @Singleton
    abstract fun bindOnDeviceLlmProvider(
        impl: MediaPipeOnDeviceLlmProvider,
    ): OnDeviceLlmProvider

    @Binds
    @Singleton
    abstract fun bindFeedbackRepository(
        impl: FeedbackRepositoryImpl,
    ): FeedbackRepository
}
