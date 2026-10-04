package com.rishinnovations.isai.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks WHERE available = 1 ORDER BY title COLLATE NOCASE ASC")
    fun observeAvailableTracks(): Flow<List<TrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tracks: List<TrackEntity>)

    @Query("UPDATE tracks SET available = 0")
    suspend fun markAllUnavailable()

    @Query("UPDATE tracks SET available = 1 WHERE id IN (:ids)")
    suspend fun markAvailable(ids: List<Long>)

    @Query("DELETE FROM tracks WHERE available = 0")
    suspend fun deleteUnavailable()
}
