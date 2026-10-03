package com.rishinnovations.isai

import android.content.ContentResolver
import android.content.ContentUris
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MusicRepository(private val resolver: ContentResolver) {
    suspend fun scan(): List<Song> = withContext(Dispatchers.IO) {
        val result = mutableListOf<Song>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.MIME_TYPE
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 0"

        resolver.query(collection, projection, selection, null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val title = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val album = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumId = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val duration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val mime = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)

            while (cursor.moveToNext()) {
                val mediaId = cursor.getLong(id)
                val mimeType = if (mime >= 0) cursor.getString(mime) else null
                if (mimeType != null && !mimeType.startsWith("audio/")) continue
                val albumIdValue = cursor.getLong(albumId)
                result += Song(
                    id = mediaId,
                    title = cursor.getString(title)?.takeIf { it.isNotBlank() } ?: "Unknown title",
                    artist = cursor.getString(artist)?.takeIf { it != "<unknown>" && it.isNotBlank() } ?: "Unknown artist",
                    album = cursor.getString(album)?.takeIf { it != "<unknown>" && it.isNotBlank() } ?: "Unknown album",
                    durationMs = cursor.getLong(duration),
                    uri = ContentUris.withAppendedId(collection, mediaId),
                    artworkUri = if (albumIdValue > 0) ContentUris.withAppendedId(
                        MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumIdValue
                    ).buildUpon().appendPath("albumart").build() else null
                )
            }
        }
        result
    }
}
