package com.rishinnovations.isai

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MusicRepository(private val resolver: ContentResolver) {
    suspend fun loadLocalSongs(): List<Song> = withContext(Dispatchers.IO) {
        val result = mutableListOf<Song>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION
        )
        val selection = MediaStore.Audio.Media.IS_MUSIC + " != 0 AND " +
            MediaStore.Audio.Media.DURATION + " > 0"
        resolver.query(collection, projection, selection, null,
            MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC")?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val title = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val album = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumId = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val duration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (cursor.moveToNext()) {
                val mediaId = cursor.getLong(id)
                val aid = cursor.getLong(albumId)
                result += Song(
                    mediaId,
                    cursor.getString(title).textOr("Unknown title"),
                    cursor.getString(artist).textOr("Unknown artist", true),
                    cursor.getString(album).textOr("Unknown album", true),
                    cursor.getLong(duration),
                    ContentUris.withAppendedId(collection, mediaId),
                    if (aid > 0) ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"), aid
                    ) else null
                )
            }
        }
        result
    }

    private fun String?.textOr(fallback: String, unknownCheck: Boolean = false): String =
        this?.trim()?.takeIf { it.isNotEmpty() && (!unknownCheck || !it.equals("<unknown>", true)) } ?: fallback
}
