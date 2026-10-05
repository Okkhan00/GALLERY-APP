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
import android.graphics.RadialGradient
import android.graphics.Shader
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

    fun crop(src: Bitmap, index: Int): Bitmap {
        val aspect = EditCrop.OPTIONS.getOrNull(index)?.second ?: 0f
        if (aspect <= 0f) return src
        val r = EditCrop.rect(src.width, src.height, aspect)
        return if (r[2] == src.width && r[3] == src.height) src else Bitmap.createBitmap(src, r[0], r[1], r[2], r[3])
    }

    /** Pipeline: orient -> crop -> blur -> colour matrix (incl. warmth) -> highlights/shadows -> sharpen -> vignette. */
    fun render(src: Bitmap, p: EditParams): Bitmap {
        var b = crop(orient(src, p), p.crop)
        if (p.blur > 0) { // cheap down/up-scale blur; fine for previews and saved copies
            val f = 1f + p.blur * 1.5f
            val small = Bitmap.createScaledBitmap(b, (b.width / f).toInt().coerceAtLeast(1), (b.height / f).toInt().coerceAtLeast(1), true)
            b = Bitmap.createScaledBitmap(small, b.width, b.height, true)
        }
        val out = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(ColorMatrix(EditMath.values(p))) }
        Canvas(out).drawBitmap(b, 0f, 0f, paint)
        if (p.shadows != 0 || p.highlights != 0) applyTone(out, EditMath.toneTable(p.shadows, p.highlights))
        if (p.sharpness > 0) applySharpen(out, p.sharpness)
        if (p.vignette > 0) applyVignette(out, p.vignette)
        return out
    }

    private fun clamp(v: Int) = if (v < 0) 0 else if (v > 255) 255 else v

    /** Row-by-row so only one row of pixels is held as an IntArray (no full-size pixel array on big images). */
    private fun applyTone(bmp: Bitmap, table: IntArray) {
        val w = bmp.width
        val row = IntArray(w)
        for (y in 0 until bmp.height) {
            bmp.getPixels(row, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                val c = row[x]
                val r = (c shr 16) and 255; val g = (c shr 8) and 255; val bl = c and 255
                val d = table[(77 * r + 150 * g + 29 * bl) shr 8]
                row[x] = (c and 0xFF000000.toInt()) or (clamp(r + d) shl 16) or (clamp(g + d) shl 8) or clamp(bl + d)
            }
            bmp.setPixels(row, 0, w, 0, y, w, 1)
        }
    }

    /** Unsharp mask: original + amount * (original - blurred). The blurred copy is a cheap down/up-scale. */
    private fun applySharpen(bmp: Bitmap, strength: Int) {
        val w = bmp.width; val h = bmp.height
        val small = Bitmap.createScaledBitmap(bmp, (w / 3).coerceAtLeast(1), (h / 3).coerceAtLeast(1), true)
        val blurred = Bitmap.createScaledBitmap(small, w, h, true)
        val amount = strength / 100f * 1.4f
        val a = IntArray(w); val b = IntArray(w)
        for (y in 0 until h) {
            bmp.getPixels(a, 0, w, 0, y, w, 1)
            blurred.getPixels(b, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                val p = a[x]; val q = b[x]
                val r = clamp(Math.round(((p shr 16) and 255) + amount * (((p shr 16) and 255) - ((q shr 16) and 255))))
                val g = clamp(Math.round(((p shr 8) and 255) + amount * (((p shr 8) and 255) - ((q shr 8) and 255))))
                val bl = clamp(Math.round((p and 255) + amount * ((p and 255) - (q and 255))))
                a[x] = (p and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or bl
            }
            bmp.setPixels(a, 0, w, 0, y, w, 1)
        }
        blurred.recycle(); small.recycle()
    }

    private fun applyVignette(bmp: Bitmap, strength: Int) {
        val w = bmp.width.toFloat(); val h = bmp.height.toFloat()
        val alpha = (strength / 100f * 210f).toInt().coerceIn(0, 255)
        val paint = Paint().apply {
            shader = RadialGradient(w / 2f, h / 2f, Math.hypot((w / 2f).toDouble(), (h / 2f).toDouble()).toFloat(), intArrayOf(0x00000000, alpha shl 24), floatArrayOf(0.45f, 1f), Shader.TileMode.CLAMP)
        }
        Canvas(bmp).drawRect(0f, 0f, w, h, paint)
    }

    /** Writes a NEW jpeg into Pictures/VaultGallery. The original file is never modified. */
    fun saveCopy(context: Context, source: Uri, originalName: String, p: EditParams): Uri {
        // Large photos can exhaust the heap; retry at smaller working sizes instead of crashing.
        for (maxSide in intArrayOf(3072, 2048, 1280)) {
            try { return saveCopyAt(context, source, originalName, p, maxSide) } catch (e: OutOfMemoryError) { /* try smaller */ }
        }
        error("not enough memory")
    }

    private fun saveCopyAt(context: Context, source: Uri, originalName: String, p: EditParams, maxSide: Int): Uri {
        val bitmap = load(context, source, maxSide) ?: error("decode failed")
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
