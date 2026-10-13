package com.feishin.mobile.di

import com.feishin.mobile.ui.home.DiskHomeCache
import com.feishin.mobile.ui.home.HomeCache
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CacheModule {
    /** Disk-backed in the app; tests construct the in-memory implementation. */
    @Binds @Singleton
    abstract fun bindHomeCache(impl: DiskHomeCache): HomeCache
}
