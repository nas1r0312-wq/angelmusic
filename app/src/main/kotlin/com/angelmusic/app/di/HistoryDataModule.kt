package com.angelmusic.app.di

import com.angelmusic.app.history.HistoryLocalDataSource
import com.angelmusic.app.history.RoomHistoryLocalDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HistoryDataModule {
    @Binds
    @Singleton
    abstract fun bindHistoryLocalDataSource(
        source: RoomHistoryLocalDataSource,
    ): HistoryLocalDataSource
}
