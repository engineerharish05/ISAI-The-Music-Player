package com.rishinnovations.isai.data.library

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Size
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.key.Keyer
import coil.request.Options
import coil.size.Dimension
import com.rishinnovations.isai.container
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.drawable.BitmapDrawable
import java.io.File
import java.io.FileNotFoundException

/** Loads cover art for a song, preferring the system thumbnail and falling back to embedded art. */
object ArtworkLoader {
    fun load(context: Context, uri: Uri, sizePx: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= 29) {
            val thumb = runCatching { context.contentResolver.loadThumbnail(uri, Size(sizePx, sizePx), null) }.getOrNull()
            if (thumb != null) return thumb
        }
        return embedded(context, uri, sizePx)
    }

    private fun embedded(context: Context, uri: Uri, sizePx: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val bytes = retriever.embeddedPicture ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= sizePx && bounds.outHeight / (sample * 2) >= sizePx) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}

/** Model type handed to Coil so artwork goes through [ArtworkLoader]. */
data class ArtworkModel(val songId: Long, val uriString: String)

class ArtworkKeyer : Keyer<ArtworkModel> {
    override fun key(data: ArtworkModel, options: Options): String = "art:${data.songId}:${data.uriString}"
}

class ArtworkFetcher(private val data: ArtworkModel, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val width = (options.size.width as? Dimension.Pixels)?.px ?: 512
        val bitmap = withContext(Dispatchers.IO) { ArtworkLoader.load(options.context, Uri.parse(data.uriString), width.coerceIn(96, 1024)) }
            ?: return null
        return DrawableResult(BitmapDrawable(options.context.resources, bitmap), isSampled = true, dataSource = DataSource.DISK)
    }

    class Factory : Fetcher.Factory<ArtworkModel> {
        override fun create(data: ArtworkModel, options: Options, imageLoader: ImageLoader): Fetcher = ArtworkFetcher(data, options)
    }
}

/** Small on-disk JPEG cache used to hand artwork to the media notification, widget and Android Auto. */
object ArtworkCache {
    private const val MAX_BYTES = 40L * 1024 * 1024

    private fun dir(context: Context) = File(context.cacheDir, "art").apply { mkdirs() }

    fun fileFor(context: Context, songId: Long): File? {
        val file = File(dir(context), "$songId.jpg")
        if (file.exists() && file.length() > 0) {
            file.setLastModified(System.currentTimeMillis())
            return file
        }
        val song = context.container.library.snapshot().byId[songId] ?: return null
        val bitmap = ArtworkLoader.load(context, song.uri, 512) ?: return null
        return try {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            trim(context)
            file
        } catch (_: Exception) {
            file.delete()
            null
        }
    }

    /** Returns the cached file only if it already exists; never does decoding work. */
    fun cachedFile(context: Context, songId: Long): File? = File(dir(context), "$songId.jpg").takeIf { it.exists() && it.length() > 0 }

    fun sizeBytes(context: Context): Long = dir(context).listFiles()?.sumOf { it.length() } ?: 0L

    fun clear(context: Context) {
        dir(context).listFiles()?.forEach { it.delete() }
    }

    private fun trim(context: Context) {
        val files = dir(context).listFiles() ?: return
        var total = files.sumOf { it.length() }
        if (total <= MAX_BYTES) return
        for (file in files.sortedBy { it.lastModified() }) {
            total -= file.length()
            file.delete()
            if (total <= MAX_BYTES * 3 / 4) break
        }
    }
}

/** Serves cached cover art as content://com.rishinnovations.isai.art/<songId>. Read-only. */
class ArtworkProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = "image/jpeg"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val id = uri.lastPathSegment?.toLongOrNull() ?: throw FileNotFoundException("Bad artwork uri")
        val ctx = context ?: throw FileNotFoundException("No context")
        val file = ArtworkCache.fileFor(ctx, id) ?: throw FileNotFoundException("No artwork for $id")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        /** Set from the real application id at startup so two editions can be installed together. */
        @Volatile var AUTHORITY: String = "com.rishinnovations.isai.art"
        fun uriFor(songId: Long): Uri = Uri.parse("content://$AUTHORITY/$songId")
    }
}
