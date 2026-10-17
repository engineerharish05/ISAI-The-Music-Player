package com.rishinnovations.isai.data.online

/** A track from an open music catalogue. Only sources that permit free listening are supported. */
data class OnlineTrack(
    val source: String,          // "archive" or "jamendo"
    val sourceId: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val streamUrl: String,
    val artworkUrl: String?,
    val license: String,         // human-readable license or rights statement
    val pageUrl: String
) {
    val mediaId: String get() = "online:$source:$sourceId"
    val fileExtension: String get() = streamUrl.substringBefore('?').substringAfterLast('.', "mp3").take(4).lowercase()
}

sealed interface OnlineResult {
    data class Success(val tracks: List<OnlineTrack>) : OnlineResult
    data class Failure(val message: String) : OnlineResult
}
