package com.vaultgallery.app.util

data class EditParams(
    val brightness: Int = 100,
    val contrast: Int = 100,
    val saturate: Int = 100,
    val sepia: Int = 0,
    val blur: Int = 0,
    val rotation: Int = 0,
    val flipH: Boolean = false,
    val flipV: Boolean = false,
    // Added with the editor upgrade. Appended with defaults so every existing preset and call keeps working.
    val warmth: Int = 0,        // -100 cool .. 100 warm
    val highlights: Int = 0,    // -100 .. 100
    val shadows: Int = 0,       // -100 .. 100
    val sharpness: Int = 0,     // 0 .. 100
    val vignette: Int = 0,      // 0 .. 100
    val crop: Int = 0,          // index into EditCrop.OPTIONS (0 = original)
) {
    fun encode() = "b=$brightness;c=$contrast;s=$saturate;sp=$sepia;bl=$blur;r=$rotation;fh=$flipH;fv=$flipV;" +
        "w=$warmth;hi=$highlights;sh=$shadows;sr=$sharpness;vg=$vignette;cr=$crop"
}

/** Center-crop presets. Pure maths so it is unit-tested. */
object EditCrop {
    val OPTIONS: List<Pair<String, Float>> = listOf(
        "Original" to 0f, "1:1" to 1f, "4:3" to 4f / 3f, "3:4" to 3f / 4f, "16:9" to 16f / 9f, "9:16" to 9f / 16f,
    )

    /** Returns x, y, width, height of the centered crop with the given aspect ratio (w/h) inside w x h. */
    fun rect(w: Int, h: Int, aspect: Float): IntArray {
        if (aspect <= 0f || w <= 0 || h <= 0) return intArrayOf(0, 0, w, h)
        val cw: Int
        val ch: Int
        if (w.toFloat() / h > aspect) { ch = h; cw = Math.round(h * aspect).coerceIn(1, w) } else { cw = w; ch = Math.round(w / aspect).coerceIn(1, h) }
        return intArrayOf((w - cw) / 2, (h - ch) / 2, cw, ch)
    }
}

/** Same presets as the web editor. */
val EditPresets: Map<String, EditParams> = linkedMapOf(
    "Original" to EditParams(),
    "Vivid" to EditParams(110, 120, 150, 0, 0),
    "B&W" to EditParams(100, 110, 0, 0, 0),
    "Warm" to EditParams(105, 100, 110, 35, 0),
    "Cool" to EditParams(100, 105, 90, 0, 0),
    "Fade" to EditParams(115, 85, 70, 10, 0),
    "Drama" to EditParams(90, 150, 120, 0, 0),
    "Dreamy" to EditParams(110, 90, 80, 15, 1),
)

/** Pure-JVM 4x5 color matrix math (row = output channel R,G,B,A; columns = r,g,b,a,offset 0..255).
 *  Order mirrors the CSS filter chain in the web editor: brightness, contrast, saturate, sepia. */
object EditMath {
    fun identity() = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)

    fun values(p: EditParams): FloatArray {
        var m = identity()
        m = concat(brightness(p.brightness / 100f), m)
        m = concat(contrast(p.contrast / 100f), m)
        m = concat(saturation(p.saturate / 100f), m)
        m = concat(sepia(p.sepia / 100f), m)
        m = concat(warmth(p.warmth / 100f), m)
        return m
    }

    private fun brightness(b: Float) = floatArrayOf(b, 0f, 0f, 0f, 0f, 0f, b, 0f, 0f, 0f, 0f, 0f, b, 0f, 0f, 0f, 0f, 0f, 1f, 0f)

    private fun contrast(c: Float): FloatArray {
        val t = 128f * (1f - c)
        return floatArrayOf(c, 0f, 0f, 0f, t, 0f, c, 0f, 0f, t, 0f, 0f, c, 0f, t, 0f, 0f, 0f, 1f, 0f)
    }

    private fun saturation(s: Float): FloatArray {
        val r = 0.213f; val g = 0.715f; val b = 0.072f
        return floatArrayOf(
            r * (1 - s) + s, g * (1 - s), b * (1 - s), 0f, 0f,
            r * (1 - s), g * (1 - s) + s, b * (1 - s), 0f, 0f,
            r * (1 - s), g * (1 - s), b * (1 - s) + s, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    /** Warm = more red / less blue, cool = the opposite. */
    private fun warmth(w: Float) = floatArrayOf(1f + 0.18f * w, 0f, 0f, 0f, 0f, 0f, 1f + 0.02f * w, 0f, 0f, 0f, 0f, 0f, 1f - 0.18f * w, 0f, 0f, 0f, 0f, 0f, 1f, 0f)

    /** 256-entry brightness delta by luminance: shadows lift/darken the dark end, highlights the bright end. */
    fun toneTable(shadows: Int, highlights: Int): IntArray = IntArray(256) { l ->
        val x = l / 255f
        val dark = (1f - x) * (1f - x)
        val bright = x * x
        Math.round(shadows / 100f * 60f * dark + highlights / 100f * 60f * bright)
    }

    private fun sepia(a: Float): FloatArray {
        fun mix(i: Float, s: Float) = (1 - a) * i + a * s
        return floatArrayOf(
            mix(1f, .393f), mix(0f, .769f), mix(0f, .189f), 0f, 0f,
            mix(0f, .349f), mix(1f, .686f), mix(0f, .168f), 0f, 0f,
            mix(0f, .272f), mix(0f, .534f), mix(1f, .131f), 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    /** Returns a∘b: apply b first, then a. */
    fun concat(a: FloatArray, b: FloatArray): FloatArray {
        val out = FloatArray(20)
        for (i in 0 until 4) {
            for (j in 0 until 5) {
                var sum = 0f
                for (k in 0 until 4) sum += a[i * 5 + k] * b[k * 5 + j]
                if (j == 4) sum += a[i * 5 + 4]
                out[i * 5 + j] = sum
            }
        }
        return out
    }
}
