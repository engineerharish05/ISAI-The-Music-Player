package com.rishinnovations.isai.data.local

import androidx.room.Entity
import androidx.room.Index

@Entity(tableName = "tracks", primaryKeys = ["id"], indices = [Index("title"), Index("artist"), Index("album")])
data class TrackEntity(
    val id: Long,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val artworkUri: String?,
    val mimeType: String?,
    val dateAdded: Long,
    val sourceType: String = "LOCAL",
    val available: Boolean = true
)
