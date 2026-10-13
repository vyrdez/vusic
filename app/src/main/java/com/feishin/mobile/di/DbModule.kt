package com.feishin.mobile.di

import android.content.Context
import androidx.room.Room
import com.feishin.mobile.data.db.MusicDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DbModule {
    @Provides @Singleton
    fun provideDb(@ApplicationContext ctx: Context): MusicDatabase =
        Room.databaseBuilder(ctx, MusicDatabase::class.java, "feishin.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideDownloadDao(db: MusicDatabase) = db.downloadDao()
    @Provides fun provideDownloadGroupDao(db: MusicDatabase) = db.downloadGroupDao()
    @Provides fun provideMetadataCacheDao(db: MusicDatabase) = db.metadataCacheDao()
    @Provides fun provideCachedCoverDao(db: MusicDatabase) = db.cachedCoverDao()
    @Provides fun providePendingActionDao(db: MusicDatabase) = db.pendingActionDao()
}
