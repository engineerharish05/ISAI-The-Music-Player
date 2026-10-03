package com.rishinnovations.isai

import android.net.Uri

data class Song(val id: Long,val title: String,val artist: String,val album: String,val durationMs: Long,val uri: Uri,val artworkUri: Uri? = null)
enum class RepeatMode { OFF, ALL, ONE }
data class PlayerState(val currentSong: Song? = null,val isPlaying: Boolean = false,val positionMs: Long = 0L,val durationMs: Long = 0L,val queue: List<Song> = emptyList(),val queueIndex: Int = -1,val shuffle: Boolean = false,val repeatMode: RepeatMode = RepeatMode.OFF)
