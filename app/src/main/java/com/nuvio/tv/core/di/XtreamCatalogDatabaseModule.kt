package com.nuvio.tv.core.di

import android.content.Context
import androidx.room.Room
import com.nuvio.tv.data.local.XtreamCatalogDao
import com.nuvio.tv.data.local.XtreamCatalogDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object XtreamCatalogDatabaseModule {
    @Provides
    @Singleton
    fun provideXtreamCatalogDatabase(@ApplicationContext context: Context): XtreamCatalogDatabase =
        Room.databaseBuilder(
            context,
            XtreamCatalogDatabase::class.java,
            "xtream-catalog.db",
        ).build()

    @Provides
    fun provideXtreamCatalogDao(database: XtreamCatalogDatabase): XtreamCatalogDao =
        database.xtreamCatalogDao()
}
