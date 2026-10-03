package com.rishinnovations.isai

import android.content.ContentResolver
import android.content.ContentUris
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MusicRepository(private val resolver: ContentResolver) {
    suspend fun scan(): List<Song> = withContext(Dispatchers.IO) {
        val out = mutableListOf<Song>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION
        )
        val selection =
            MediaStore.Audio.Media.IS_MUSIC + " != 0 AND " +
                MediaStore.Audio.Media.DURATION + " > 0"

        resolver.query(
            collection,
            projection,
            selection,
            null,
            MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC"
        )?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val title = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val album = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumId = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val duration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (cursor.moveToNext()) {
                val mediaId = cursor.getLong(id)
                val albumIdValue = cursor.getLong(albumId)
                val artwork = if (albumIdValue > 0L) {
                    ContentUris.withAppendedId(
                        "content://media/external/audio/albumart".let(android.net.Uri::parse),
                        albumIdValue
                    )
                } else null

                out += Song(
                    id = mediaId,
                    title = cursor.getString(title)?.takeIf { it.isNotBlank() } ?: "Unknown title",
                    artist = cursor.getString(artist)
                        ?.takeIf { it.isNotBlank() && it != "<unknown>" }
                        ?: "Unknown artist",
                    album = cursor.getString(album)
                        ?.takeIf { it.isNotBlank() && it != "<unknown>" }
                        ?: "Unknown album",
                    durationMs = cursor.getLong(duration),
                    uri = ContentUris.withAppendedId(collection, mediaId),
                    artworkUri = artwork
                )
            }
        }

        out
    }
}
