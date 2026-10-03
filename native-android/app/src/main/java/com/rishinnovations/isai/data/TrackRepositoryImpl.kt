package com.rishinnovations.isai.data

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import com.rishinnovations.isai.data.local.TrackDao
import com.rishinnovations.isai.data.local.TrackEntity
import com.rishinnovations.isai.domain.model.Track
import com.rishinnovations.isai.domain.model.TrackSource
import com.rishinnovations.isai.domain.repository.TrackRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class TrackRepositoryImpl(
    private val resolver: ContentResolver,
    private val dao: TrackDao
) : TrackRepository {

    override fun observeTracks(): Flow<List<Track>> =
        dao.observeAvailableTracks().map { rows -> rows.map(::toDomain) }

    override suspend fun refreshLocalTracks(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media.DATE_ADDED
            )
            val tracks = mutableListOf<TrackEntity>()

            resolver.query(
                collection,
                projection,
                MediaStore.Audio.Media.DURATION + " > 0",
                null,
                MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC"
            )?.use { cursor ->
                val id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val title = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val album = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumId = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val duration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val mime = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
                val added = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val mediaId = cursor.getLong(id)
                    val albumArtwork = cursor.getLong(albumId).takeIf { it > 0L }?.let {
                        ContentUris.withAppendedId(
                            Uri.parse("content://media/external/audio/albumart"), it
                        ).toString()
                    }

                    tracks += TrackEntity(
                        id = mediaId,
                        uri = ContentUris.withAppendedId(collection, mediaId).toString(),
                        title = cursor.getString(title).orUnknown("Unknown title"),
                        artist = cursor.getString(artist).orUnknown("Unknown artist"),
                        album = cursor.getString(album).orUnknown("Unknown album"),
                        durationMs = cursor.getLong(duration),
                        artworkUri = albumArtwork,
                        mimeType = mime.takeIf { it >= 0 }?.let(cursor::getString),
                        dateAdded = added.takeIf { it >= 0 }?.let(cursor::getLong) ?: 0L
                    )
                }
            }

            dao.markAllUnavailable()
            if (tracks.isNotEmpty()) {
                dao.upsertAll(tracks)
                dao.markAvailable(tracks.map { it.id })
            }
            dao.deleteUnavailable()
            tracks.size
        }
    }

    private fun toDomain(row: TrackEntity) = Track(
        id = row.id,
        uri = Uri.parse(row.uri),
        title = row.title,
        artist = row.artist,
        album = row.album,
        durationMs = row.durationMs,
        artworkUri = row.artworkUri?.let(Uri::parse),
        mimeType = row.mimeType,
        dateAdded = row.dateAdded,
        source = TrackSource.valueOf(row.sourceType),
        available = row.available
    )

    private fun String?.orUnknown(fallback: String): String =
        this?.trim()?.takeIf { it.isNotEmpty() && !it.equals("<unknown>", true) } ?: fallback
}
