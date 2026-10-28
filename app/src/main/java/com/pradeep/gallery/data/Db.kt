package com.pradeep.gallery.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

const val SCOPE_ALL = "all"

/** A pin lives in one scope: "all" (main grid) or an album's bucketId as a string. */
@Entity(tableName = "pins", primaryKeys = ["mediaId", "scope"])
data class Pin(val mediaId: Long, val scope: String, val pinnedAt: Long)

@Entity(tableName = "favorites")
data class Favorite(@PrimaryKey val mediaId: Long)

@Dao
interface GalleryDao {
    @Query("SELECT * FROM pins")
    fun pins(): Flow<List<Pin>>

    @Query("SELECT mediaId FROM favorites")
    fun favorites(): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addPins(pins: List<Pin>)

    @Query("DELETE FROM pins WHERE scope = :scope AND mediaId IN (:ids)")
    suspend fun removePins(scope: String, ids: List<Long>)

    @Query("DELETE FROM pins WHERE mediaId IN (:ids)")
    suspend fun removeAllPinsFor(ids: List<Long>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorites(favs: List<Favorite>)

    @Query("DELETE FROM favorites WHERE mediaId IN (:ids)")
    suspend fun removeFavorites(ids: List<Long>)
}

@Database(entities = [Pin::class, Favorite::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): GalleryDao
}
