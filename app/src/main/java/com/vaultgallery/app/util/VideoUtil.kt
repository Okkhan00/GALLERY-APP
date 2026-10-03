package com.vaultgallery.app.util

import androidx.media3.common.PlaybackException
import com.vaultgallery.app.data.PhotoEntity
import java.util.Locale

/** 00:08, 01:24, 12:35, 1:05:42. Pure function so it is unit-tested. */
fun formatDuration(ms: Long): String {
    val totalSec = (ms.coerceAtLeast(0) / 1000)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%02d:%02d", m, s)
}

/** Displayed (rotation-corrected) width/height of a video. MediaStore reports the coded size plus an orientation. */
fun PhotoEntity.displayWidth(): Int = if (orientation == 90 || orientation == 270) height else width
fun PhotoEntity.displayHeight(): Int = if (orientation == 90 || orientation == 270) width else height

/** Short badge such as "4K" or "1080p"; null when the clip is small or the size is unknown. */
fun resolutionBadge(width: Int, height: Int): String? {
    val longSide = maxOf(width, height)
    val shortSide = minOf(width, height)
    return when {
        shortSide <= 0 -> null
        longSide >= 3800 -> "4K"
        shortSide >= 1440 -> "1440p"
        shortSide >= 1080 -> "1080p"
        shortSide >= 720 -> "720p"
        else -> null
    }
}

/** File format label derived from the MIME type, e.g. video/mp4 -> MP4, video/x-matroska -> MKV. */
fun videoFormatLabel(mime: String, fileName: String): String {
    val known = mapOf(
        "video/mp4" to "MP4", "video/3gpp" to "3GP", "video/3gpp2" to "3G2", "video/x-matroska" to "MKV",
        "video/webm" to "WEBM", "video/quicktime" to "MOV", "video/mp2ts" to "TS", "video/avi" to "AVI",
        "video/x-msvideo" to "AVI", "video/x-flv" to "FLV", "video/mpeg" to "MPEG",
    )
    known[mime.lowercase(Locale.US)]?.let { return it }
    val ext = fileName.substringAfterLast('.', "")
    return if (ext.isNotBlank() && ext.length <= 5) ext.uppercase(Locale.US) else mime.substringAfter('/').uppercase(Locale.US)
}

enum class PlayerFailure { UNSUPPORTED_FORMAT, MISSING_FILE, NO_PERMISSION, CORRUPT, OTHER }

/** Maps a Media3 error code to a user-facing category. Never exposes the raw exception text to users. */
fun classifyPlaybackError(errorCode: Int): PlayerFailure = when (errorCode) {
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
    PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED -> PlayerFailure.UNSUPPORTED_FORMAT
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
    PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE -> PlayerFailure.MISSING_FILE
    PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> PlayerFailure.NO_PERMISSION
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
    PlaybackException.ERROR_CODE_DECODING_FAILED -> PlayerFailure.CORRUPT
    else -> PlayerFailure.OTHER
}

fun PlayerFailure.message(): String = when (this) {
    PlayerFailure.UNSUPPORTED_FORMAT -> "Format not supported on this device."
    PlayerFailure.MISSING_FILE -> "This video can't be found. It may have been moved or deleted."
    PlayerFailure.NO_PERMISSION -> "Vault Gallery no longer has permission to open this video."
    PlayerFailure.CORRUPT -> "This video file looks damaged."
    PlayerFailure.OTHER -> "Unable to play this video."
}

val PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

fun speedLabel(speed: Float): String =
    if (speed == speed.toInt().toFloat()) "${speed.toInt()}.0x" else "${speed}x"
