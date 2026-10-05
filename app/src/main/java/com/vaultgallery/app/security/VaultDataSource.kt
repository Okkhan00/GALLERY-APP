package com.vaultgallery.app.security

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import java.io.IOException

/**
 * Media3 data source that decrypts a vault file on the fly with random access, so vault videos play (and seek)
 * directly from encrypted storage. No decrypted copy is ever written to disk or MediaStore.
 */
@UnstableApi
class VaultDataSource(private val openReader: () -> VaultReader?) : BaseDataSource(/* isNetwork = */ false) {
    private var reader: VaultReader? = null
    private var uri: Uri? = null
    private var position = 0L
    private var remaining = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        uri = dataSpec.uri
        val r = openReader() ?: throw IOException("Vault item unavailable")
        reader = r
        if (dataSpec.position > r.size) throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
        position = dataSpec.position
        remaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) minOf(dataSpec.length, r.size - position) else r.size - position
        opened = true
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val n = reader?.readAt(position, buffer, offset, minOf(length.toLong(), remaining).toInt()) ?: return C.RESULT_END_OF_INPUT
        if (n <= 0) return C.RESULT_END_OF_INPUT
        position += n
        remaining -= n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        try { reader?.close() } finally {
            reader = null
            uri = null
            if (opened) { opened = false; transferEnded() }
        }
    }
}
