package com.vaultgallery.app.security

import android.util.Base64
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Chunked AES-256-GCM so large videos can be encrypted/decrypted as a stream and still be seeked.
 *
 * File layout: a sequence of records, each = AES-GCM(ciphertext of up to CHUNK plaintext bytes) + 16-byte tag.
 * Every file has its own random 256-bit data key. The nonce is [stream id (4 bytes)][chunk index (8 bytes)], which is
 * unique per (key, chunk) because the key is never reused across files; the stream id separates the media file from its
 * thumbnail. AAD carries a "last chunk" flag so truncating or re-ordering chunks fails authentication.
 * Pure JVM (no Android Keystore here), so it is unit-tested.
 */
object VaultCrypto {
    const val CHUNK = 64 * 1024
    const val TAG = 16
    const val RECORD = CHUNK + TAG
    const val STREAM_MEDIA = 0
    const val STREAM_THUMB = 1

    fun newKey(): ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }

    fun chunkCount(size: Long): Long = if (size <= 0) 1 else (size + CHUNK - 1) / CHUNK

    /** Plaintext length recovered from an encrypted file's length (used for thumbnails, whose size isn't stored). */
    fun plainLength(encryptedLength: Long): Long {
        val chunks = (encryptedLength + RECORD - 1) / RECORD
        return (encryptedLength - chunks * TAG).coerceAtLeast(0)
    }

    private fun cipher(mode: Int, key: ByteArray, stream: Int, index: Long, last: Boolean): Cipher {
        val iv = ByteBuffer.allocate(12).putInt(stream).putLong(index).array()
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        c.updateAAD(byteArrayOf(if (last) 1 else 0))
        return c
    }

    /** Encrypts [input] into [out]. Returns the plaintext byte count; [digest] (if any) is fed the plaintext. */
    fun encrypt(
        input: InputStream, out: OutputStream, key: ByteArray, stream: Int,
        digest: MessageDigest?, ensureActive: () -> Unit = {},
    ): Long {
        var total = 0L
        var index = 0L
        var cur = readFully(input, CHUNK)
        while (true) {
            ensureActive()
            val next = if (cur.size == CHUNK) readFully(input, CHUNK) else ByteArray(0)
            val last = next.isEmpty()
            digest?.update(cur)
            out.write(cipher(Cipher.ENCRYPT_MODE, key, stream, index, last).doFinal(cur))
            total += cur.size
            if (last) break
            cur = next
            index++
        }
        return total
    }

    fun decryptChunk(key: ByteArray, stream: Int, index: Long, last: Boolean, record: ByteArray, length: Int): ByteArray =
        cipher(Cipher.DECRYPT_MODE, key, stream, index, last).doFinal(record, 0, length)

    private fun readFully(input: InputStream, n: Int): ByteArray {
        val buf = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = input.read(buf, off, n - off)
            if (r < 0) break
            off += r
        }
        return if (off == n) buf else buf.copyOf(off)
    }

    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}

/** Random-access, thread-safe decrypting reader over one encrypted file. Used by the video data source and image decoding. */
class VaultReader(
    private val file: RandomAccessFile, private val key: ByteArray, val size: Long,
    private val stream: Int = VaultCrypto.STREAM_MEDIA,
) : Closeable {
    private val chunks = VaultCrypto.chunkCount(size)
    private var cachedIndex = -1L
    private var cached = ByteArray(0)
    private val record = ByteArray(VaultCrypto.RECORD)

    @Synchronized
    fun readAt(position: Long, buf: ByteArray, off: Int, len: Int): Int {
        if (position >= size) return -1
        var pos = position
        var done = 0
        while (done < len && pos < size) {
            val idx = pos / VaultCrypto.CHUNK
            val data = chunk(idx)
            val inOff = (pos - idx * VaultCrypto.CHUNK).toInt()
            val n = minOf(len - done, data.size - inOff)
            System.arraycopy(data, inOff, buf, off + done, n)
            done += n
            pos += n
        }
        return done
    }

    @Synchronized
    fun forEachChunk(ensureActive: () -> Unit = {}, block: (ByteArray) -> Unit) {
        var i = 0L
        while (i < chunks) { ensureActive(); block(chunk(i)); i++ }
    }

    private fun chunk(idx: Long): ByteArray {
        if (idx == cachedIndex) return cached
        val last = idx == chunks - 1
        val plainLen = if (last) (size - idx * VaultCrypto.CHUNK).toInt() else VaultCrypto.CHUNK
        val recLen = plainLen + VaultCrypto.TAG
        file.seek(idx * VaultCrypto.RECORD)
        file.readFully(record, 0, recLen)
        cached = VaultCrypto.decryptChunk(key, stream, idx, last, record, recLen)
        cachedIndex = idx
        return cached
    }

    fun asInputStream(): InputStream = object : InputStream() {
        private var pos = 0L
        override fun read(): Int {
            val b = ByteArray(1)
            return if (readAt(pos, b, 0, 1) <= 0) -1 else { pos++; b[0].toInt() and 0xFF }
        }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = readAt(pos, b, off, len)
            if (n > 0) pos += n
            return n
        }
        override fun skip(n: Long): Long { val k = minOf(n, size - pos).coerceAtLeast(0); pos += k; return k }
        override fun available(): Int = minOf(size - pos, Int.MAX_VALUE.toLong()).toInt()
    }

    override fun close() {
        try { file.close() } finally { key.fill(0) }
    }
}

/** Seals each per-file data key with a non-exportable Android Keystore key (existing KeystoreCrypto). */
object VaultKeys {
    private const val ALIAS = "vault_media_key"
    fun wrap(key: ByteArray): String = Base64.encodeToString(KeystoreCrypto.encrypt(ALIAS, key), Base64.NO_WRAP)
    fun unwrap(wrapped: String): ByteArray = KeystoreCrypto.decrypt(ALIAS, Base64.decode(wrapped, Base64.NO_WRAP))
}
