package com.rishinnovations.isai

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MusicRepository(private val resolver: ContentResolver) {
    suspend fun loadLocalSongs(): List<Song> = withContext(Dispatchers.IO) {
        val songs = ArrayList<Song>()
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
        val selection = MediaStore.Audio.Media.MIME_TYPE + " LIKE ? AND " +
            MediaStore.Audio.Media.DURATION + " > 0"
        val selectionArgs = arrayOf("audio/%")

        resolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val mimeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)

            while (cursor.moveToNext()) {
                val mime = mimeCol.takeIf { it >= 0 }?.let(cursor::getString)
                if (mime != null && !mime.startsWith("audio/")) continue

                val id = cursor.getLong(idCol)
                val albumId = cursor.getLong(albumIdCol)

                songs += Song(
                    id = id,
                    title = cursor.getString(titleCol).textOr("Unknown title"),
                    artist = cursor.getString(artistCol).textOr("Unknown artist", true),
                    album = cursor.getString(albumCol).textOr("Unknown album", true),
                    durationMs = cursor.getLong(durationCol),
                    uri = ContentUris.withAppendedId(collection, id),
                    artworkUri = albumArtwork(albumId)
                )
            }
        }
        songs
    }

    private fun albumArtwork(albumId: Long): Uri? =
        if (albumId > 0L) {
            ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"),
                albumId
            )
        } else {
            null
        }

    private fun String?.textOr(fallback: String, rejectUnknown: Boolean = false): String =
        this?.trim()
            ?.takeIf { it.isNotEmpty() && (!rejectUnknown || !it.equals("<unknown>", true)) }
            ?: fallback
}
