package com.rishinnovations.isai.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "favorites")
data class FavoriteEntity(@PrimaryKey val songId: Long, val addedAt: Long)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long
)

@Entity(
    tableName = "playlist_songs",
    foreignKeys = [ForeignKey(entity = PlaylistEntity::class, parentColumns = ["id"], childColumns = ["playlistId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["playlistId", "songId"], unique = true)]
)
data class PlaylistSongEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val songId: Long,
    val position: Int
)

/** One listening session of one track. [counted] is true once it qualifies as a play. */
@Entity(tableName = "play_events", indices = [Index("songId"), Index("startedAt")])
data class PlayEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: Long,
    val startedAt: Long,
    val listenedMs: Long,
    val counted: Boolean
)

@Entity(tableName = "imported_songs", indices = [Index(value = ["uri"], unique = true)])
data class ImportedSongEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String?,
    val durationMs: Long,
    val trackNumber: Int,
    val year: Int,
    val dateAddedSec: Long,
    val sizeBytes: Long,
    val mimeType: String,
    val treeUri: String?
)

@Entity(tableName = "imported_folders")
data class ImportedFolderEntity(@PrimaryKey val treeUri: String, val name: String, val addedAt: Long)

data class SongPlayAggregate(val songId: Long, val plays: Int, val listenedMs: Long, val lastPlayedAt: Long)

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(entity: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE songId = :songId")
    suspend fun remove(songId: Long)
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt ASC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlist_songs ORDER BY playlistId, position")
    fun observeLinks(): Flow<List<PlaylistSongEntity>>

    @Insert
    suspend fun insertPlaylist(entity: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_songs WHERE playlistId = :id")
    suspend fun maxPosition(id: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLink(link: PlaylistSongEntity): Long

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeLink(playlistId: Long, songId: Long)

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position")
    suspend fun links(playlistId: Long): List<PlaylistSongEntity>

    @Update
    suspend fun updateLinks(links: List<PlaylistSongEntity>)

    @Transaction
    suspend fun addSongs(playlistId: Long, songIds: List<Long>) {
        var position = maxPosition(playlistId) + 1
        for (songId in songIds) {
            if (insertLink(PlaylistSongEntity(playlistId = playlistId, songId = songId, position = position)) != -1L) position++
        }
    }

    @Transaction
    suspend fun move(playlistId: Long, from: Int, to: Int) {
        val list = links(playlistId).toMutableList()
        if (from !in list.indices || to !in list.indices) return
        list.add(to, list.removeAt(from))
        updateLinks(list.mapIndexed { index, link -> link.copy(position = index) })
    }

    @Transaction
    suspend fun removeAllMatching(playlistId: Long, songIds: List<Long>) {
        songIds.forEach { removeLink(playlistId, it) }
        updateLinks(links(playlistId).mapIndexed { index, link -> link.copy(position = index) })
    }
}

@Dao
interface PlayEventDao {
    @Insert
    suspend fun insert(event: PlayEventEntity): Long

    @Query("UPDATE play_events SET listenedMs = :listenedMs, counted = :counted WHERE id = :id")
    suspend fun update(id: Long, listenedMs: Long, counted: Boolean)

    @Query(
        "SELECT songId, SUM(CASE WHEN counted = 1 THEN 1 ELSE 0 END) AS plays, SUM(listenedMs) AS listenedMs, " +
            "MAX(startedAt) AS lastPlayedAt FROM play_events GROUP BY songId"
    )
    fun observeAggregates(): Flow<List<SongPlayAggregate>>

    @Query("SELECT COALESCE(SUM(listenedMs), 0) FROM play_events")
    fun observeTotalListenedMs(): Flow<Long>

    @Query("SELECT * FROM play_events WHERE startedAt >= :since ORDER BY startedAt ASC")
    fun observeSince(since: Long): Flow<List<PlayEventEntity>>

    @Query("DELETE FROM play_events")
    suspend fun clear()
}

@Dao
interface ImportDao {
    @Query("SELECT * FROM imported_songs")
    fun observeSongs(): Flow<List<ImportedSongEntity>>

    @Query("SELECT * FROM imported_songs")
    suspend fun songs(): List<ImportedSongEntity>

    @Query("SELECT uri FROM imported_songs")
    suspend fun allUris(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSong(entity: ImportedSongEntity): Long

    @Query("DELETE FROM imported_songs WHERE id IN (:ids)")
    suspend fun deleteSongs(ids: List<Long>)

    @Query("SELECT * FROM imported_songs WHERE treeUri = :treeUri")
    suspend fun songsForTree(treeUri: String): List<ImportedSongEntity>

    @Query("DELETE FROM imported_songs WHERE treeUri = :treeUri")
    suspend fun deleteSongsForTree(treeUri: String)

    @Query("SELECT * FROM imported_folders ORDER BY addedAt")
    fun observeFolders(): Flow<List<ImportedFolderEntity>>

    @Query("SELECT * FROM imported_folders")
    suspend fun folders(): List<ImportedFolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFolder(entity: ImportedFolderEntity)

    @Query("DELETE FROM imported_folders WHERE treeUri = :treeUri")
    suspend fun deleteFolder(treeUri: String)
}

@Database(
    entities = [
        FavoriteEntity::class, PlaylistEntity::class, PlaylistSongEntity::class,
        PlayEventEntity::class, ImportedSongEntity::class, ImportedFolderEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class IsaiDatabase : RoomDatabase() {
    abstract fun favorites(): FavoriteDao
    abstract fun playlists(): PlaylistDao
    abstract fun playEvents(): PlayEventDao
    abstract fun imports(): ImportDao

    companion object {
        fun build(context: Context): IsaiDatabase =
            Room.databaseBuilder(context.applicationContext, IsaiDatabase::class.java, "isai.db")
                // Upgrades must ship explicit migrations so user data is never wiped silently.
                .fallbackToDestructiveMigrationOnDowngrade(true)
                .build()
    }
}
