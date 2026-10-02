package com.vaultgallery.app.media

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import androidx.room.withTransaction
import com.vaultgallery.app.data.AppDatabase
import com.vaultgallery.app.data.PhotoEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Mirrors MediaStore into Room in batches. User data (favorites, tags, captions, lock) is never overwritten. */
class MediaScanner(private val context: Context, private val db: AppDatabase) {

    suspend fun sync(onProgress: (done: Int, total: Int) -> Unit) = withContext(Dispatchers.IO) {
        val scope: CoroutineScope = this
        val stamp = System.currentTimeMillis()
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.SIZE, MediaStore.Images.Media.WIDTH, MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.DATE_ADDED, MediaStore.Images.Media.DATE_MODIFIED, MediaStore.Images.Media.DATE_TAKEN,
        )
        val completed = context.contentResolver.query(collection, projection, null, null, "${MediaStore.Images.Media._ID} DESC")?.use { c ->
            val total = c.count
            val iId = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val iName = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val iMime = c.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val iSize = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val iW = c.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val iH = c.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val iAdded = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val iMod = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val iTaken = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)

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
                    displayName = clean(c.getString(iName)),
                    mimeType = c.getString(iMime) ?: "image/*",
                    size = c.getLong(iSize), width = c.getInt(iW), height = c.getInt(iH),
                    dateAdded = added, dateModified = c.getLong(iMod) * 1000, dateTaken = taken,
                    lastSeen = stamp, createdAt = stamp, updatedAt = stamp,
                )
                if (batch.size >= BATCH) {
                    flush(batch, stamp); done += batch.size; batch.clear(); onProgress(done, total)
                }
            }
            if (batch.isNotEmpty()) { flush(batch, stamp); done += batch.size; onProgress(done, total) }
            true
        } ?: false
        // Only mark things missing after a complete, successful pass.
        if (completed) db.photos().markMissing(stamp)
    }

    private suspend fun flush(batch: List<PhotoEntity>, stamp: Long) {
        db.withTransaction {
            val dao = db.photos()
            val results = dao.insertAll(batch)
            batch.forEachIndexed { i, p ->
                if (results[i] == -1L) dao.updateMeta(p.mediaStoreId, p.displayName, p.size, p.width, p.height, p.dateModified, stamp)
            }
        }
    }

    // Strip control characters and cap length so odd file names can't break UI or later exports.
    private fun clean(name: String?): String =
        (name ?: "photo").filter { !it.isISOControl() }.take(255).ifBlank { "photo" }

    private companion object { const val BATCH = 400 }
}
