package com.krishinirnay.core.data.di

import com.krishinirnay.core.data.composite.AlertGenerator
import com.krishinirnay.core.data.firebase.FirebaseAuthRepositoryImpl
import com.krishinirnay.core.data.local.AppPreferences
import com.krishinirnay.core.data.mock.MockProfileRepositoryImpl
import com.krishinirnay.core.data.mock.MockSchemesRepositoryImpl
import com.krishinirnay.core.data.mock.MockWeatherRepositoryImpl
import com.krishinirnay.core.data.network.CropHealthRepositoryImpl
import com.krishinirnay.core.data.network.LiveFieldStateRepositoryImpl
import com.krishinirnay.core.data.network.PestRepositoryImpl
import com.krishinirnay.core.data.network.RiskRepositoryImpl
import com.krishinirnay.core.data.repository.AlertsRepository
import com.krishinirnay.core.data.repository.AuthRepository
import com.krishinirnay.core.data.repository.CropHealthRepository
import com.krishinirnay.core.data.repository.FieldStateRepository
import com.krishinirnay.core.data.repository.PestRepository
import com.krishinirnay.core.data.repository.ProfileRepository
import com.krishinirnay.core.data.repository.RiskRepository
import com.krishinirnay.core.data.repository.SchemesRepository
import com.krishinirnay.core.data.repository.SettingsRepository
import com.krishinirnay.core.data.repository.WeatherRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindFieldStateRepository(
        impl: LiveFieldStateRepositoryImpl,
    ): FieldStateRepository

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
    abstract fun bindWeatherRepository(
        impl: MockWeatherRepositoryImpl,
    ): WeatherRepository

    @Binds
    @Singleton
    abstract fun bindSchemesRepository(
        impl: MockSchemesRepositoryImpl,
    ): SchemesRepository

    @Binds
    @Singleton
    abstract fun bindProfileRepository(
        impl: MockProfileRepositoryImpl,
    ): ProfileRepository
}