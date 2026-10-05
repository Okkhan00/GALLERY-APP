package com.vaultgallery.app.security

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.vaultgallery.app.data.VaultItemEntity
import com.vaultgallery.app.util.ImageIO
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.UUID

/** Result of moving one item's bytes into the vault; the caller inserts the database row. */
class StagedVaultFile(val base: String, val wrappedKey: String, val size: Long, val sha256: String, val hasThumb: Boolean)

/**
 * All vault files live in the app-private directory (no other app can read them, they are not in MediaStore and have
 * random names). Nothing here ever logs a path or a key. Names always come from the database and are validated.
 */
class VaultStore(context: Context) {
    private val app = context.applicationContext
    private val dir = File(app.filesDir, "vault").apply { mkdirs() }

    fun newBase(): String = UUID.randomUUID().toString().replace("-", "")
    fun mediaName(base: String) = "$base.v"
    fun thumbName(base: String) = "$base.t"

    fun file(name: String): File {
        require(NAME.matches(name)) { "invalid vault file name" }
        return File(dir, name)
    }

    fun openReader(item: VaultItemEntity): VaultReader? = try {
        VaultReader(RandomAccessFile(file(item.fileName), "r"), VaultKeys.unwrap(item.wrappedKey), item.size, VaultCrypto.STREAM_MEDIA)
    } catch (e: Exception) { null }

    // ---- hide: copy -> encrypt -> verify. The caller deletes the original only after this succeeds. ----
    fun stage(source: Uri, isVideo: Boolean, ensureActive: () -> Unit): StagedVaultFile {
        val base = newBase()
        val key = VaultCrypto.newKey()
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            val media = file(mediaName(base))
            val input = app.contentResolver.openInputStream(source) ?: throw IOException("cannot open source")
            val size = input.use { ins ->
                val fos = FileOutputStream(media)
                try {
                    val bos = BufferedOutputStream(fos, 64 * 1024)
                    val n = VaultCrypto.encrypt(ins, bos, key, VaultCrypto.STREAM_MEDIA, digest, ensureActive)
                    bos.flush()
                    fos.fd.sync()   // durable before the original is ever removed
                    n
                } finally { fos.close() }
            }
            val sha = VaultCrypto.hex(digest.digest())

            // Verify by decrypting everything back and comparing the hash. Only then is the copy trusted.
            val check = MessageDigest.getInstance("SHA-256")
            VaultReader(RandomAccessFile(media, "r"), key.copyOf(), size, VaultCrypto.STREAM_MEDIA).use { r ->
                r.forEachChunk(ensureActive) { check.update(it) }
            }
            if (VaultCrypto.hex(check.digest()) != sha) throw IOException("verification failed")

            val hasThumb = writeThumb(source, isVideo, base, key)
            return StagedVaultFile(base, VaultKeys.wrap(key), size, sha, hasThumb)
        } catch (t: Throwable) {
            delete(mediaName(base), thumbName(base))
            throw t
        } finally {
            key.fill(0)
        }
    }

    private fun writeThumb(source: Uri, isVideo: Boolean, base: String, key: ByteArray): Boolean {
        try {
            val raw = (if (isVideo) videoFrame(source) else ImageIO.load(app, source, THUMB_PX)) ?: return false
            val scale = THUMB_PX.toFloat() / maxOf(raw.width, raw.height)
            val bmp = if (scale < 1f) Bitmap.createScaledBitmap(raw, (raw.width * scale).toInt().coerceAtLeast(1), (raw.height * scale).toInt().coerceAtLeast(1), true) else raw
            val jpeg = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 82, it) }.toByteArray()
            FileOutputStream(file(thumbName(base))).use {
                VaultCrypto.encrypt(ByteArrayInputStream(jpeg), it, key, VaultCrypto.STREAM_THUMB, null)
            }
            return true
        } catch (e: Exception) {
            delete(thumbName(base))
            return false
        }
    }

    private fun videoFrame(source: Uri): Bitmap? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(app, source)
            r.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: r.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (e: Exception) { null } finally { r.release() }
    }

    // ---- reading for the UI ----
    fun loadThumb(item: VaultItemEntity): Bitmap? {
        if (item.thumbName.isEmpty()) return null
        return try {
            val f = file(item.thumbName)
            if (!f.exists()) return null
            val plain = VaultCrypto.plainLength(f.length())
            VaultReader(RandomAccessFile(f, "r"), VaultKeys.unwrap(item.wrappedKey), plain, VaultCrypto.STREAM_THUMB).use { r ->
                val bytes = ByteArray(plain.toInt())
                r.readAt(0, bytes, 0, bytes.size)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (e: Exception) { null }
    }

    /** Bounded decode (never the full-resolution bitmap) with EXIF rotation applied. */
    fun decodeImage(item: VaultItemEntity, maxSide: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            openReader(item)?.use { BitmapFactory.decodeStream(it.asInputStream(), null, bounds) } ?: return null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = openReader(item)?.use { BitmapFactory.decodeStream(it.asInputStream(), null, opts) } ?: return null
            val degrees = openReader(item)?.use { rotationOf(it) } ?: 0
            if (degrees == 0) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
        } catch (e: OutOfMemoryError) { null } catch (e: Exception) { null }
    }

    private fun rotationOf(r: VaultReader): Int = try {
        when (ExifInterface(r.asInputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (e: Exception) { 0 }

    // ---- unhide: decrypt back into MediaStore (app-owned insert, no consent dialog needed) ----
    fun export(item: VaultItemEntity, ensureActive: () -> Unit = {}): Uri {
        val resolver = app.contentResolver
        val video = item.isVideo
        val collection = if (video) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, item.displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, item.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, (if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES) + "/VaultGallery")
            put(MediaStore.MediaColumns.DATE_TAKEN, item.dateTaken)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("insert failed")
        try {
            val reader = openReader(item) ?: throw IOException("vault item unreadable")
            reader.use { r ->
                val out = resolver.openOutputStream(uri) ?: throw IOException("cannot write")
                out.use { o -> r.forEachChunk(ensureActive) { o.write(it) } }
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }
        return uri
    }

    fun delete(vararg names: String) {
        names.filter { it.isNotEmpty() && NAME.matches(it) }.forEach { runCatching { File(dir, it).delete() } }
    }

    /** Removes files that no database row refers to (e.g. after a crash mid-hide). Young files are kept: they may be in progress. */
    fun sweepOrphans(known: Set<String>, minAgeMs: Long = 10 * 60_000L) {
        val now = System.currentTimeMillis()
        dir.listFiles()?.forEach { f -> if (f.name !in known && now - f.lastModified() > minAgeMs) f.delete() }
    }

    private companion object {
        val NAME = Regex("^[0-9a-f]{32}\\.[vt]$")
        const val THUMB_PX = 384
    }
}
