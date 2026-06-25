package com.deciboost.app.di

import com.deciboost.app.service.BoostServiceClient
import com.deciboost.app.service.BoostServiceClientImpl
import com.deciboost.core.domain.BoostEngineService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ServiceModule {

    @Binds
    @Singleton
    abstract fun bindBoostServiceClient(impl: BoostServiceClientImpl): BoostServiceClient

    @Binds
    @Singleton
    abstract fun bindBoostEngineService(impl: BoostServiceClientImpl): BoostEngineService
}