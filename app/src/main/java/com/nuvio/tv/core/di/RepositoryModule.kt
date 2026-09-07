package com.nuvio.tv.core.di

import com.nuvio.tv.data.repository.LibraryRepositoryImpl
import com.nuvio.tv.data.repository.StreamRepositoryImpl
import com.nuvio.tv.data.repository.WatchProgressRepositoryImpl
import com.nuvio.tv.data.xtream.XtreamDataSource
import com.nuvio.tv.data.xtream.XtreamLiveRepository
import com.nuvio.tv.data.xtream.XtreamRemoteDataSource
import com.nuvio.tv.domain.repository.LibraryRepository
import com.nuvio.tv.domain.repository.LiveTvRepository
import com.nuvio.tv.domain.repository.StreamRepository
import com.nuvio.tv.domain.repository.WatchProgressRepository
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
    abstract fun bindXtreamDataSource(impl: XtreamRemoteDataSource): XtreamDataSource
    @Binds
    @Singleton
    abstract fun bindLiveTvRepository(impl: XtreamLiveRepository): LiveTvRepository

    @Binds
    @Singleton
    abstract fun bindLibraryRepository(impl: LibraryRepositoryImpl): LibraryRepository

    @Binds
    @Singleton
    abstract fun bindStreamRepository(impl: StreamRepositoryImpl): StreamRepository

    @Binds
    @Singleton
    abstract fun bindWatchProgressRepository(impl: WatchProgressRepositoryImpl): WatchProgressRepository
}
