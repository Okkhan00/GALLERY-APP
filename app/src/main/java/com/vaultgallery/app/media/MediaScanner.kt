package com.vaultgallery.app.media

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.room.withTransaction
import com.vaultgallery.app.data.AppDatabase
import com.vaultgallery.app.data.MediaType
import com.vaultgallery.app.data.PhotoEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Mirrors MediaStore (images AND videos) into Room in batches. User data (favorites, tags, captions, lock,
 * saved playback position) is never overwritten. Videos already on the phone are discovered automatically.
 * Only metadata is read here: no pixels are decoded and no video file is opened.
 */
class MediaScanner(private val context: Context, private val db: AppDatabase) {

    suspend fun sync(onProgress: (done: Int, total: Int) -> Unit) = withContext(Dispatchers.IO) {
        val scope: CoroutineScope = this
        val stamp = System.currentTimeMillis()

        val collections = listOf(
            MediaType.IMAGE to MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaType.VIDEO to MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        )
        // Progress is reported across both passes combined.
        val totals = IntArray(collections.size) { i -> count(collections[i].second) }
        val grand = totals.sum()
        var doneSoFar = 0

        collections.forEachIndexed { i, (type, collection) ->
            val completed = scanOne(scope, collection, type, stamp, totals[i]) { done ->
                onProgress(doneSoFar + done, grand)
            }
            doneSoFar += totals[i]
            // Only flag rows missing after a complete, successful pass of THAT type. If the video permission is
            // not granted the query returns nothing/throws, and existing video rows are left untouched.
            if (completed) db.photos().markMissing(type, stamp)
        }
    }

    private fun count(collection: Uri): Int = runCatching {
        context.contentResolver.query(collection, arrayOf(MediaStore.MediaColumns._ID), null, null, null)?.use { it.count } ?: 0
    }.getOrDefault(0)

    private suspend fun scanOne(
        scope: CoroutineScope, collection: Uri, type: Int, stamp: Long, total: Int, onDone: (Int) -> Unit,
    ): Boolean {
        val isVideo = type == MediaType.VIDEO
        val projection = arrayOf(
            MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.WIDTH, MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.DATE_MODIFIED, MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DURATION, MediaStore.MediaColumns.BUCKET_DISPLAY_NAME, MediaStore.MediaColumns.ORIENTATION,
        )
        val cursor = try {
            context.contentResolver.query(collection, projection, null, null, "${MediaStore.MediaColumns._ID} DESC")
        } catch (e: SecurityException) {
            null // permission for this media type not granted: skip quietly, never crash
        } catch (e: IllegalArgumentException) {
            null
        } ?: return false

        return cursor.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val iName = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val iMime = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val iSize = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val iW = c.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
            val iH = c.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
            val iAdded = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val iMod = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val iTaken = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
            val iDur = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
            val iBucket = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val iOrient = c.getColumnIndexOrThrow(MediaStore.MediaColumns.ORIENTATION)

            var done = 0
            val batch = ArrayList<PhotoEntity>(BATCH)
            while (c.moveToNext()) {
                scope.ensureActive()
                val mediaId = c.getLong(iId)
                val added = c.getLong(iAdded) * 1000
                val taken = c.getLong(iTaken).takeIf { it > 0 } ?: added
                batch += PhotoEntity(
                    mediaStoreId = mediaId,
                    contentUri = ContentUris.withAppendedId(collection, mediaId).toString(),
                    displayName = clean(c.getString(iName), isVideo),
                    mimeType = c.getString(iMime) ?: if (isVideo) "video/*" else "image/*",
                    size = c.getLong(iSize), width = c.getInt(iW), height = c.getInt(iH),
                    dateAdded = added, dateModified = c.getLong(iMod) * 1000, dateTaken = taken,
                    lastSeen = stamp, createdAt = stamp, updatedAt = stamp,
                    mediaType = type,
                    durationMs = if (isVideo) c.getLong(iDur).coerceAtLeast(0) else 0,
                    bucket = (c.getString(iBucket) ?: "").filter { !it.isISOControl() }.take(255),
                    orientation = c.getInt(iOrient),
                )
                if (batch.size >= BATCH) {
                    flush(batch, stamp); done += batch.size; batch.clear(); onDone(done)
                }
            }
            if (batch.isNotEmpty()) { flush(batch, stamp); done += batch.size; onDone(done) }
            true
        }
    }

    private suspend fun flush(batch: List<PhotoEntity>, stamp: Long) {
        db.withTransaction {
            val dao = db.photos()
            val results = dao.insertAll(batch)
            batch.forEachIndexed { i, p ->
                if (results[i] == -1L) {
                    dao.updateMeta(
                        p.mediaStoreId, p.mediaType, p.displayName, p.size, p.width, p.height, p.dateModified,
                        p.durationMs, p.bucket, p.orientation, stamp,
                    )
                }
            }
        }
    }

    // Strip control characters and cap length so odd file names can't break UI or later exports.
    private fun clean(name: String?, isVideo: Boolean): String =
        (name ?: if (isVideo) "video" else "photo").filter { !it.isISOControl() }.take(255).ifBlank { if (isVideo) "video" else "photo" }

    private companion object { const val BATCH = 400 }
}
