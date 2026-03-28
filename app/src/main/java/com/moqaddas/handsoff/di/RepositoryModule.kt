package com.moqaddas.handsoff.di

import com.moqaddas.handsoff.data.repository.PrivacyRepositoryImpl
import com.moqaddas.handsoff.domain.repository.PrivacyRepository
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
    abstract fun bindPrivacyRepository(impl: PrivacyRepositoryImpl): PrivacyRepository
}
