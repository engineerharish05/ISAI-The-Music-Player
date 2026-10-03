package com.rishinnovations.isai.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [TrackEntity::class], version = 1, exportSchema = false)
abstract class IsaiDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
}
