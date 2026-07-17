package com.nuvio.tv.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase

@Entity(tableName = "xtream_movie_identity", primaryKeys = ["sourceFingerprint", "streamId"])
data class XtreamMovieIdentityEntity(
    val sourceFingerprint: String,
    val streamId: Int,
    val tmdbId: Int?,
    val hydratedAtMillis: Long,
)

@Dao
interface XtreamCatalogDao {
    @Query("SELECT * FROM xtream_movie_identity WHERE sourceFingerprint = :fingerprint")
    suspend fun identities(fingerprint: String): List<XtreamMovieIdentityEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertIdentity(identity: XtreamMovieIdentityEntity)

    @Query("DELETE FROM xtream_movie_identity WHERE sourceFingerprint != :fingerprint")
    suspend fun deleteOtherSources(fingerprint: String)
}

@Database(
    entities = [XtreamMovieIdentityEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class XtreamCatalogDatabase : RoomDatabase() {
    abstract fun xtreamCatalogDao(): XtreamCatalogDao
}
