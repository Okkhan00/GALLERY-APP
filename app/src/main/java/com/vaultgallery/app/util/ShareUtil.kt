package com.vaultgallery.app.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.vaultgallery.app.security.AppLockManager

/** Native share sheet with content:// URIs and a temporary read grant. Never a raw file path, never loads files into memory. */
fun Context.shareMedia(uris: List<Uri>, mimeTypes: List<String>, title: String) {
    if (uris.isEmpty()) return
    val mime = when {
        mimeTypes.isNotEmpty() && mimeTypes.distinct().size == 1 -> mimeTypes.first()
        mimeTypes.isNotEmpty() && mimeTypes.all { it.startsWith("image/") } -> "image/*"
        mimeTypes.isNotEmpty() && mimeTypes.all { it.startsWith("video/") } -> "video/*"
        else -> "*/*"
    }
    val send = if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
    else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
    send.type = mime
    send.clipData = ClipData.newRawUri(null, uris[0]).also { clip -> uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) } }
    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    AppLockManager.skipNextLock = true
    startActivity(Intent.createChooser(send, title))
}
