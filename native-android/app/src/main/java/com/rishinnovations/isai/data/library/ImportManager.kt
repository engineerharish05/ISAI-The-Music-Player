package com.rishinnovations.isai.data.library

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.rishinnovations.isai.AppMessages
import com.rishinnovations.isai.data.db.ImportedFolderEntity
import com.rishinnovations.isai.data.db.ImportedSongEntity
import com.rishinnovations.isai.data.db.IsaiDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Imports audio picked by the user (individual files or whole folders) through the Storage Access Framework. */
class ImportManager(
    private val context: Context,
    private val db: IsaiDatabase,
    private val scope: CoroutineScope
) {
    private val _busy = MutableStateFlow<String?>(null)

    /** Human-readable progress text while an import is running, otherwise null. */
    val busy: StateFlow<String?> = _busy

    fun importFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        scope.launch {
            _busy.value = "Importing ${uris.size} file(s)…"
            val added = withContext(Dispatchers.IO) { addDocuments(uris.map { it to null }, null) }
            _busy.value = null
            AppMessages.post(if (added > 0) "Added $added song(s)" else "No new songs were added")
        }
    }

    fun importFolder(treeUri: Uri) {
        scope.launch {
            _busy.value = "Scanning folder…"
            val result = withContext(Dispatchers.IO) { syncTree(treeUri, remember = true) }
            _busy.value = null
            AppMessages.post(result)
        }
    }

    fun rescanFolders() {
        scope.launch {
            val folders = db.imports().folders()
            if (folders.isEmpty()) return@launch
            _busy.value = "Rescanning ${folders.size} folder(s)…"
            var added = 0
            var removed = 0
            withContext(Dispatchers.IO) {
                for (folder in folders) {
                    val before = db.imports().songsForTree(folder.treeUri).size
                    syncTree(Uri.parse(folder.treeUri), remember = false)
                    val after = db.imports().songsForTree(folder.treeUri).size
                    if (after >= before) added += after - before else removed += before - after
                }
            }
            _busy.value = null
            if (added > 0 || removed > 0) AppMessages.post("Folders updated: $added added, $removed removed")
        }
    }

    fun removeFolder(treeUri: String) {
        scope.launch {
            db.imports().deleteSongsForTree(treeUri)
            db.imports().deleteFolder(treeUri)
            runCatching {
                context.contentResolver.releasePersistableUriPermission(Uri.parse(treeUri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    private suspend fun syncTree(treeUri: Uri, remember: Boolean): String {
        runCatching { context.contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val found = try {
            walkTree(treeUri)
        } catch (e: Exception) {
            return "Couldn't read that folder. It may have been moved or access was revoked."
        }
        val key = treeUri.toString()
        if (remember) {
            val name = displayName(DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))) ?: "Folder"
            db.imports().upsertFolder(ImportedFolderEntity(key, name, System.currentTimeMillis()))
        }
        val added = addDocuments(found.map { it.first to it.second }, key)
        // Songs that disappeared from the folder are dropped from the library.
        val foundUris = found.map { it.first.toString() }.toSet()
        val stale = db.imports().songsForTree(key).filter { it.uri !in foundUris }.map { it.id }
        if (stale.isNotEmpty()) db.imports().deleteSongs(stale)
        return "Folder scanned: $added new song(s)" + if (stale.isNotEmpty()) ", ${stale.size} removed" else ""
    }

    private fun walkTree(treeUri: Uri): List<Pair<Uri, String?>> {
        val result = ArrayList<Pair<Uri, String?>>()
        val stack = ArrayDeque<String>()
        val visited = HashSet<String>()
        stack.add(DocumentsContract.getTreeDocumentId(treeUri))
        while (stack.isNotEmpty()) {
            val parentId = stack.removeLast()
            if (!visited.add(parentId)) continue
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            context.contentResolver.query(
                children,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE),
                null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0)
                    val name = c.getString(1).orEmpty()
                    val mime = c.getString(2).orEmpty()
                    when {
                        mime == DocumentsContract.Document.MIME_TYPE_DIR -> stack.add(id)
                        isAudio(mime, name) -> result += DocumentsContract.buildDocumentUriUsingTree(treeUri, id) to mime
                    }
                }
            }
        }
        return result
    }

    private fun isAudio(mime: String, name: String): Boolean {
        if (mime.startsWith("audio/") || mime == "application/ogg") return true
        if (mime == "application/octet-stream" || mime.isEmpty()) {
            return name.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS
        }
        return false
    }

    private suspend fun addDocuments(items: List<Pair<Uri, String?>>, treeKey: String?): Int {
        val existing = db.imports().allUris().toSet()
        var added = 0
        for ((uri, mimeHint) in items) {
            if (uri.toString() in existing) continue
            if (treeKey == null) runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val entity = readMetadata(uri, mimeHint, treeKey) ?: continue
            if (db.imports().insertSong(entity) != -1L) added++
        }
        return added
    }

    private fun displayName(uri: Uri): String? = if (uri.scheme == "file") uri.lastPathSegment else runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull()

    private fun readMetadata(uri: Uri, mimeHint: String?, treeKey: String?): ImportedSongEntity? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            if (duration <= 0L) return null
            val fileName = displayName(uri)?.substringBeforeLast('.')
            fun meta(key: Int) = retriever.extractMetadata(key)?.trim()?.takeIf { it.isNotEmpty() }
            ImportedSongEntity(
                uri = uri.toString(),
                title = meta(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: fileName ?: "Unknown title",
                artist = meta(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown artist",
                album = meta(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "Unknown album",
                genre = meta(MediaMetadataRetriever.METADATA_KEY_GENRE),
                durationMs = duration,
                trackNumber = meta(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)?.substringBefore('/')?.toIntOrNull() ?: 0,
                year = meta(MediaMetadataRetriever.METADATA_KEY_YEAR)?.take(4)?.toIntOrNull() ?: 0,
                dateAddedSec = System.currentTimeMillis() / 1000,
                sizeBytes = 0L,
                mimeType = mimeHint ?: context.contentResolver.getType(uri) ?: meta(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: "audio/*",
                treeUri = treeKey
            )
        } catch (_: Exception) {
            null // unreadable or not audio: skip without failing the whole import
        } finally {
            runCatching { retriever.release() }
        }
    }

    companion object {
        private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "flac", "ogg", "oga", "opus", "wav", "mka", "amr", "3gp", "mid", "midi")
    }
}
