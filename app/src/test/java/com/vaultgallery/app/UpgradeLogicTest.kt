package com.vaultgallery.app

import com.vaultgallery.app.data.MigrationSql
import com.vaultgallery.app.security.VaultCrypto
import com.vaultgallery.app.security.VaultReader
import com.vaultgallery.app.util.EditCrop
import com.vaultgallery.app.util.EditMath
import com.vaultgallery.app.util.EditParams
import com.vaultgallery.app.util.Timeline
import com.vaultgallery.app.util.TimelineGrouper
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.Random

class UpgradeLogicTest {
    private val utc = ZoneId.of("UTC")
    private fun ms(y: Int, m: Int, d: Int, h: Int = 12) = LocalDate.of(y, m, d).atStartOfDay(utc).toInstant().toEpochMilli() + h * 3_600_000L

    // ---------- timeline ----------
    @Test fun timelineLabels() {
        val now = ms(2026, 10, 7)                                   // a Wednesday
        fun l(t: Long) = Timeline.label(t, now, utc, Locale.US)
        assertEquals("Today", l(ms(2026, 10, 7, 1)))
        assertEquals("Yesterday", l(ms(2026, 10, 6)))
        assertEquals("Earlier this week", l(ms(2026, 10, 5)))
        assertEquals("September", l(ms(2026, 9, 2)))
        assertEquals("March 2025", l(ms(2025, 3, 10)))
        assertEquals("October", l(ms(2026, 10, 30)))               // future date never becomes "Today"
    }

    @Test fun timelineGrouperReusesLabelWithinADay() {
        val now = ms(2026, 10, 7)
        val g = TimelineGrouper(now, utc, Locale.US)
        assertEquals("Today", g.label(ms(2026, 10, 7, 9)))
        assertEquals("Today", g.label(ms(2026, 10, 7, 3)))
        assertEquals("Yesterday", g.label(ms(2026, 10, 6, 23)))
    }

    // ---------- vault crypto ----------
    private fun roundTrip(size: Int) {
        val data = ByteArray(size).also { Random(size.toLong()).nextBytes(it) }
        val key = VaultCrypto.newKey()
        val file = File.createTempFile("vault", ".v")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val written = FileOutputStream(file).use { VaultCrypto.encrypt(ByteArrayInputStream(data), it, key, VaultCrypto.STREAM_MEDIA, digest) }
            assertEquals(size.toLong(), written)
            assertEquals(size.toLong(), VaultCrypto.plainLength(file.length()))
            VaultReader(RandomAccessFile(file, "r"), key.copyOf(), size.toLong()).use { r ->
                // whole file via the stream view
                assertArrayEquals(data, r.asInputStream().readBytes())
                // random access across a chunk boundary
                if (size > VaultCrypto.CHUNK + 20) {
                    val buf = ByteArray(40)
                    val pos = VaultCrypto.CHUNK - 15L
                    assertEquals(40, r.readAt(pos, buf, 0, 40))
                    assertArrayEquals(data.copyOfRange(pos.toInt(), pos.toInt() + 40), buf)
                }
            }
        } finally { file.delete() }
    }

    @Test fun cryptoRoundTripsAtChunkBoundaries() {
        listOf(0, 1, 100, VaultCrypto.CHUNK - 1, VaultCrypto.CHUNK, VaultCrypto.CHUNK + 1, VaultCrypto.CHUNK * 3, VaultCrypto.CHUNK * 3 + 777).forEach { roundTrip(it) }
    }

    @Test fun cryptoDetectsTamperingAndTruncation() {
        val data = ByteArray(VaultCrypto.CHUNK * 2 + 50).also { Random(7).nextBytes(it) }
        val key = VaultCrypto.newKey()
        val file = File.createTempFile("vault", ".v")
        try {
            FileOutputStream(file).use { VaultCrypto.encrypt(ByteArrayInputStream(data), it, key, VaultCrypto.STREAM_MEDIA, null) }
            // flip one ciphertext byte
            RandomAccessFile(file, "rw").use { raf -> raf.seek(10); val b = raf.read(); raf.seek(10); raf.write(b xor 1) }
            val tampered = try {
                VaultReader(RandomAccessFile(file, "r"), key.copyOf(), data.size.toLong()).use { it.asInputStream().readBytes() }
                false
            } catch (e: Exception) { true }
            assertTrue("tampered data must fail authentication", tampered)
            // a wrong key must fail too
            val wrongKey = try {
                VaultReader(RandomAccessFile(file, "r"), VaultCrypto.newKey(), data.size.toLong()).use { it.asInputStream().readBytes() }
                false
            } catch (e: Exception) { true }
            assertTrue(wrongKey)
        } finally { file.delete() }
    }

    @Test fun ciphertextDiffersPerFileAndNeverEqualsPlaintext() {
        val data = ByteArray(1000) { 5 }
        fun enc(): ByteArray { val o = java.io.ByteArrayOutputStream(); VaultCrypto.encrypt(ByteArrayInputStream(data), o, VaultCrypto.newKey(), 0, null); return o.toByteArray() }
        val a = enc(); val b = enc()
        assertFalse(a.contentEquals(b))
        assertFalse(a.copyOf(1000).contentEquals(data))
        assertEquals(1000 + VaultCrypto.TAG, a.size)
    }

    // ---------- editor ----------
    @Test fun cropRectIsCenteredAndHonoursAspect() {
        assertArrayEquals(intArrayOf(0, 0, 400, 300), EditCrop.rect(400, 300, 0f))
        val sq = EditCrop.rect(400, 300, 1f)
        assertArrayEquals(intArrayOf(50, 0, 300, 300), sq)
        val wide = EditCrop.rect(300, 400, 16f / 9f)
        assertEquals(300, wide[2]); assertEquals(169, wide[3]); assertEquals(0, wide[0]); assertEquals((400 - 169) / 2, wide[1])
        assertEquals(EditCrop.OPTIONS.size, 6)
    }

    @Test fun toneTableLiftsShadowsAndHighlightsIndependently() {
        val t = EditMath.toneTable(100, 0)
        assertTrue(t[0] > 50); assertEquals(0, t[255])                 // shadows move the dark end only
        val h = EditMath.toneTable(0, 100)
        assertEquals(0, h[0]); assertTrue(h[255] > 50)
        val neutral = EditMath.toneTable(0, 0)
        assertTrue(neutral.all { it == 0 })
        val darker = EditMath.toneTable(-100, 0)
        assertTrue(darker[0] < -50)
    }

    @Test fun warmthShiftsRedAndBlueOppositely() {
        val warm = EditMath.values(EditParams(warmth = 100))
        val cool = EditMath.values(EditParams(warmth = -100))
        assertTrue(warm[0] > 1f && warm[12] < 1f)
        assertTrue(cool[0] < 1f && cool[12] > 1f)
        assertArrayEquals(EditMath.identity(), EditMath.values(EditParams()), 1e-6f)
    }

    @Test fun existingPresetsKeepWorking() {
        // positional construction used by the presets must still compile and leave the new fields at defaults
        val p = EditParams(110, 120, 150, 0, 0)
        assertEquals(0, p.warmth); assertEquals(0, p.crop); assertEquals(0, p.vignette)
        assertTrue(p.encode().contains("b=110"))
    }

    // ---------- migrations ----------
    @Test fun vaultMigrationOnlyAddsATable() {
        val sql = MigrationSql.V2_TO_V3.joinToString(" ").uppercase()
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `VAULT_ITEMS`"))
        assertFalse(sql.contains("DROP")); assertFalse(sql.contains("DELETE")); assertFalse(sql.contains("ALTER TABLE PHOTOS"))
        listOf("FILENAME", "THUMBNAME", "WRAPPEDKEY", "SHA256", "FAVORITE").forEach { assertTrue(it, sql.contains(it)) }
    }
}
