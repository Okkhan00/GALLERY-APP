package com.vaultgallery.app

import androidx.media3.common.PlaybackException
import com.vaultgallery.app.data.MigrationSql
import com.vaultgallery.app.util.PLAYBACK_SPEEDS
import com.vaultgallery.app.util.PlayerFailure
import com.vaultgallery.app.util.classifyPlaybackError
import com.vaultgallery.app.util.formatDuration
import com.vaultgallery.app.util.message
import com.vaultgallery.app.util.resolutionBadge
import com.vaultgallery.app.util.speedLabel
import com.vaultgallery.app.util.videoFormatLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoLogicTest {
    @Test fun durationFormatting() {
        assertEquals("00:08", formatDuration(8_000))
        assertEquals("01:24", formatDuration(84_000))
        assertEquals("12:35", formatDuration(755_000))
        assertEquals("1:05:42", formatDuration(3_942_000))
        assertEquals("00:00", formatDuration(-5))
        assertEquals("00:00", formatDuration(999))
    }

    @Test fun resolutionBadges() {
        assertEquals("4K", resolutionBadge(3840, 2160))
        assertEquals("4K", resolutionBadge(2160, 3840))       // portrait 4K
        assertEquals("1080p", resolutionBadge(1920, 1080))
        assertEquals("1080p", resolutionBadge(1080, 1920))
        assertEquals("720p", resolutionBadge(1280, 720))
        assertNull(resolutionBadge(640, 360))
        assertNull(resolutionBadge(0, 0))
    }

    @Test fun formatLabels() {
        assertEquals("MP4", videoFormatLabel("video/mp4", "a.mp4"))
        assertEquals("MKV", videoFormatLabel("video/x-matroska", "a.mkv"))
        assertEquals("WEBM", videoFormatLabel("video/webm", "a.webm"))
        assertEquals("MOV", videoFormatLabel("video/quicktime", "a.mov"))
        assertEquals("3GP", videoFormatLabel("video/3gpp", "a.3gp"))
        assertEquals("XYZ", videoFormatLabel("video/unknown", "clip.xyz"))
    }

    @Test fun playbackErrorsAreClassified() {
        assertEquals(PlayerFailure.UNSUPPORTED_FORMAT, classifyPlaybackError(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED))
        assertEquals(PlayerFailure.UNSUPPORTED_FORMAT, classifyPlaybackError(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED))
        assertEquals(PlayerFailure.UNSUPPORTED_FORMAT, classifyPlaybackError(PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED))
        assertEquals(PlayerFailure.MISSING_FILE, classifyPlaybackError(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND))
        assertEquals(PlayerFailure.NO_PERMISSION, classifyPlaybackError(PlaybackException.ERROR_CODE_IO_NO_PERMISSION))
        assertEquals(PlayerFailure.CORRUPT, classifyPlaybackError(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED))
        assertEquals(PlayerFailure.OTHER, classifyPlaybackError(PlaybackException.ERROR_CODE_UNSPECIFIED))
    }

    @Test fun errorMessagesAreFriendlyAndNeverTechnical() {
        PlayerFailure.values().forEach {
            val m = it.message()
            assertTrue(m.isNotBlank())
            assertFalse(m.contains("Exception", ignoreCase = true))
        }
        assertEquals("Format not supported on this device.", PlayerFailure.UNSUPPORTED_FORMAT.message())
    }

    @Test fun speedsMatchSpec() {
        assertEquals(listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f), PLAYBACK_SPEEDS)
        assertEquals("1.0x", speedLabel(1.0f))
        assertEquals("0.75x", speedLabel(0.75f))
        assertEquals("2.0x", speedLabel(2.0f))
    }

    @Test fun migrationIsAdditiveAndNeverWipesData() {
        val sql = MigrationSql.V1_TO_V2.joinToString("\n").uppercase()
        assertFalse(sql.contains("DROP TABLE"))
        assertFalse(sql.contains("DELETE FROM"))
        assertFalse(sql.contains("UPDATE "))
        // Every new column is NOT NULL with a default, so existing rows stay valid (and read as IMAGE = 0).
        listOf("MEDIATYPE", "DURATIONMS", "BUCKET", "ORIENTATION", "LASTPOSITIONMS").forEach { col ->
            assertTrue("missing $col", sql.contains("ADD COLUMN $col"))
        }
        assertTrue(MigrationSql.V1_TO_V2.filter { it.startsWith("ALTER TABLE") }.all { it.contains("NOT NULL DEFAULT") })
    }
}
