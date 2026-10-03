package com.rishinnovations.isai

import android.content.Context
import androidx.room.Room
import com.rishinnovations.isai.data.local.IsaiDatabase
import com.rishinnovations.isai.data.preferences.IsaiPreferences
import com.rishinnovations.isai.data.TrackRepositoryImpl
import com.rishinnovations.isai.domain.repository.TrackRepository

class IsaiContainer(context: Context) {
    private val appContext = context.applicationContext

    val preferences = IsaiPreferences(appContext)

    val database: IsaiDatabase = Room.databaseBuilder(
        appContext, IsaiDatabase::class.java, "isai.db"
    ).build()

    val trackRepository: TrackRepository = TrackRepositoryImpl(
        appContext.contentResolver, database.trackDao()
    )
}
