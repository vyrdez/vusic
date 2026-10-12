package com.feishin.mobile.di

import com.feishin.mobile.data.repo.MusicRepository
import com.feishin.mobile.domain.repo.MusicDataRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepoModule {
    @Binds @Singleton
    abstract fun bindMusicRepository(impl: MusicRepository): MusicDataRepository
}
