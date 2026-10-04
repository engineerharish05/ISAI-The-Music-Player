package com.rishinnovations.isai.domain.model

import android.net.Uri

enum class TrackSource { LOCAL, YOUTUBE, SPOTIFY, DOWNLOADED }

data class Track(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val artworkUri: Uri?,
    val mimeType: String?,
    val dateAdded: Long,
    val source: TrackSource,
    val available: Boolean = true
)
