package com.rishinnovations.isai.logic

import com.rishinnovations.isai.model.Song

fun song(
    id: Long,
    title: String = "Song $id",
    artist: String = "Artist",
    album: String = "Album",
    albumId: Long = 1,
    genre: String? = null,
    durationMs: Long = 200_000,
    track: Int = 0,
    year: Int = 0,
    added: Long = 0
) = Song(id, title, artist, album, albumId, genre, durationMs, track, year, added, 1000, "audio/mpeg", "content://media/$id")
