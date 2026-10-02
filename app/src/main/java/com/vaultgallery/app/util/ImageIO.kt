package com.vaultgallery.app.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore

/** Bounded decoding (protects against huge/malicious images) and non-destructive "save as copy". */
object ImageIO {

    fun load(context: Context, uri: Uri, maxSide: Int): Bitmap? = runCatching {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = maxOf(info.size.width, info.size.height)
            var sample = 1
            while (longest / sample > maxSide) sample *= 2
            if (sample > 1) decoder.setTargetSampleSize(sample)
        }
    }.getOrNull()

    fun orient(src: Bitmap, p: EditParams): Bitmap {
        if (p.rotation == 0 && !p.flipH && !p.flipV) return src
        val m = Matrix()
        m.postScale(if (p.flipH) -1f else 1f, if (p.flipV) -1f else 1f)
        m.postRotate(p.rotation.toFloat())
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    fun render(src: Bitmap, p: EditParams): Bitmap {
        var b = orient(src, p)
        if (p.blur > 0) { // cheap down/up-scale blur; fine for previews and saved copies
            val f = 1f + p.blur * 1.5f
            val small = Bitmap.createScaledBitmap(b, (b.width / f).toInt().coerceAtLeast(1), (b.height / f).toInt().coerceAtLeast(1), true)
            b = Bitmap.createScaledBitmap(small, b.width, b.height, true)
        }
        val out = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(ColorMatrix(EditMath.values(p))) }
        Canvas(out).drawBitmap(b, 0f, 0f, paint)
        return out
    }

    /** Writes a NEW jpeg into Pictures/VaultGallery. The original file is never modified. */
    fun saveCopy(context: Context, source: Uri, originalName: String, p: EditParams): Uri {
        val bitmap = load(context, source, 4096) ?: error("decode failed")
        val edited = render(bitmap, p)
        val base = originalName.substringBeforeLast('.', originalName).take(60).replace(Regex("[^A-Za-z0-9 _.-]"), "_")
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "${base}_edit_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/VaultGallery")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("insert failed")
        try {
            resolver.openOutputStream(uri)!!.use { edited.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null); throw e
        }
        return uri
    }
}
