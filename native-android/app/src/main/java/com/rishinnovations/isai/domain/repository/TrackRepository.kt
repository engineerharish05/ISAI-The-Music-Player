package com.rishinnovations.isai.domain.repository

import com.rishinnovations.isai.domain.model.Track
import kotlinx.coroutines.flow.Flow

interface TrackRepository {
    fun observeTracks(): Flow<List<Track>>
    suspend fun refreshLocalTracks(): Result<Int>
}
