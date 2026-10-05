package com.vaultgallery.app.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.ByteBuffer

/**
 * Lightweight video tools built on the framework MediaExtractor/MediaMuxer: trimming and removing audio copy the
 * encoded samples without re-encoding, so they are fast and lossless. The original file is never modified; results are
 * new files in Movies/VaultGallery (or Pictures/VaultGallery for frames).
 * Trim limit: a cut can only START on a keyframe, so the saved clip may begin slightly before the chosen start time.
 */
object VideoTools {
    class ToolException(message: String) : Exception(message)

    private fun cleanBase(name: String): String =
        name.substringBeforeLast('.', name).take(60).replace(Regex("[^A-Za-z0-9 _.-]"), "_").ifBlank { "video" }

    /** Writes a trimmed (and optionally silent) copy. [onProgress] is 0..1. Cancelling the coroutine removes the partial file. */
    suspend fun trim(
        context: Context, source: Uri, displayName: String, startMs: Long, endMs: Long, removeAudio: Boolean, onProgress: (Float) -> Unit,
    ): Uri = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "${cleanBase(displayName)}_${if (removeAudio) "mute_" else ""}trim_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/VaultGallery")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val out = resolver.insert(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) ?: throw IOException("insert failed")
        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        var started = false
        try {
            val ex = MediaExtractor().apply { setDataSource(context, source, null) }
            extractor = ex
            val rotation = rotationOf(context, source)
            resolver.openFileDescriptor(out, "rw")?.use { pfd ->
                muxer = MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                val map = HashMap<Int, Int>()
                var bufferSize = 2 shl 20
                var hasVideo = false
                for (i in 0 until ex.trackCount) {
                    val f = ex.getTrackFormat(i)
                    val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                    val isVideo = mime.startsWith("video/")
                    val isAudio = mime.startsWith("audio/")
                    if (!isVideo && !(isAudio && !removeAudio)) continue
                    ex.selectTrack(i)
                    map[i] = muxer!!.addTrack(f)
                    if (isVideo) hasVideo = true
                    if (f.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) bufferSize = maxOf(bufferSize, f.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
                }
                if (!hasVideo) throw ToolException("This video format can't be processed.")
                muxer!!.setOrientationHint(rotation)
                muxer!!.start(); started = true

                val startUs = startMs.coerceAtLeast(0) * 1000
                val endUs = if (endMs > 0) endMs * 1000 else Long.MAX_VALUE
                if (startUs > 0) ex.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                val baseUs = ex.sampleTime.coerceAtLeast(0)
                val buffer = ByteBuffer.allocate(bufferSize.coerceAtMost(16 shl 20))
                val info = MediaCodec.BufferInfo()
                val span = (if (endUs == Long.MAX_VALUE) 0L else endUs - baseUs).coerceAtLeast(1)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    buffer.clear()
                    val size = ex.readSampleData(buffer, 0)
                    if (size < 0) break
                    val t = ex.sampleTime
                    if (t > endUs) break
                    val track = map[ex.sampleTrackIndex]
                    if (track != null && t >= baseUs) {
                        info.set(0, size, t - baseUs, if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                        muxer!!.writeSampleData(track, buffer, info)
                    }
                    if (endUs != Long.MAX_VALUE) onProgress(((t - baseUs).toFloat() / span).coerceIn(0f, 1f))
                    if (!ex.advance()) break
                }
                muxer!!.stop(); started = false
            } ?: throw IOException("cannot open output")
            values.clear(); values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(out, values, null, null)
            onProgress(1f)
            out
        } catch (t: Throwable) {
            runCatching { if (started) muxer?.stop() }
            runCatching { resolver.delete(out, null, null) }
            throw if (t is ToolException || t is kotlinx.coroutines.CancellationException) t else ToolException("Couldn't save this video.")
        } finally {
            runCatching { muxer?.release() }
            runCatching { extractor?.release() }
        }
    }

    private fun rotationOf(context: Context, uri: Uri): Int {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, uri)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        } catch (e: Exception) { 0 } finally { r.release() }
    }

    /** Saves the frame at [positionMs] as a JPEG in Pictures/VaultGallery. */
    suspend fun saveFrame(context: Context, source: Uri, displayName: String, positionMs: Long): Uri = withContext(Dispatchers.IO) {
        val r = MediaMetadataRetriever()
        val frame: Bitmap? = try {
            r.setDataSource(context, source)
            r.getFrameAtTime(positionMs.coerceAtLeast(0) * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
        } catch (e: Exception) { null } finally { r.release() }
        val bitmap: Bitmap = frame ?: throw ToolException("Couldn't read a frame from this video.")
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "${cleanBase(displayName)}_frame_${formatDuration(positionMs).replace(':', '-')}_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/VaultGallery")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: throw ToolException("Couldn't save the frame.")
        try {
            resolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw ToolException("Couldn't save the frame.")
        }
        uri
    }
}
