package com.rishinnovations.isai.data

import android.content.ContentResolver
import android.provider.MediaStore

class MusicRepository(private val resolver: ContentResolver) {
    fun scan(): List<MusicTrack> {
        val result = mutableListOf<MusicTrack>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION
        )

        resolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            MediaStore.Audio.Media.IS_MUSIC + " != 0",
            null,
            MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                result += MusicTrack(
                    id = id,
                    title = cursor.getString(titleIndex).orEmpty(),
                    artist = cursor.getString(artistIndex).orEmpty()
                        .takeUnless { it == "<unknown>" } ?: "Unknown artist",
                    album = cursor.getString(albumIndex).orEmpty()
                        .takeUnless { it == "<unknown>" } ?: "Unknown album",
                    durationMs = cursor.getLong(durationIndex),
                    uri = MediaStore.Audio.Media.getContentUri("external", id).toString()
                )
            }
        }
        return result
    }
}
