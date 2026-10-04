package com.ultimate.macrobot.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.File

/** Template matching (normalised cross-correlation) via OpenCV. */
object ImageMatcher {
    data class Match(val x: Int, val y: Int, val score: Double)

    private var loaded = false
    private val templateCache = HashMap<String, Mat>()

    @Synchronized
    fun ensureLoaded(): Boolean {
        if (!loaded) loaded = OpenCVLoader.initLocal()
        return loaded
    }

    /**
     * One screenshot, shared by all steps checked against it: grayscale is built at most once per
     * scale, and OCR runs at most once. Call [release] when done.
     */
    class Frame(val bitmap: Bitmap) {
        private val fullLazy = lazy {
            Mat().also {
                val rgba = Mat()
                Utils.bitmapToMat(bitmap, rgba)
                Imgproc.cvtColor(rgba, it, Imgproc.COLOR_RGBA2GRAY)
                rgba.release()
            }
        }
        private var half: Mat? = null

        /** Words on screen, found by OCR (computed on first use). */
        val text: List<List<OcrWord>> by lazy { TextReader.read(bitmap) }

        fun gray(scale: Double): Mat {
            val full = fullLazy.value
            if (scale >= 1.0) return full
            return half ?: Mat().also {
                Imgproc.resize(full, it, Size(), scale, scale, Imgproc.INTER_AREA)
                half = it
            }
        }

        fun release() {
            if (fullLazy.isInitialized()) fullLazy.value.release()
            half?.release()
        }
    }

    /** Best match centre in full-resolution screen coordinates, or null when below [threshold]. */
    fun find(frame: Frame, templateFile: File, threshold: Float): Match? {
        if (!ensureLoaded() || !templateFile.exists()) return null
        val tFull = template(templateFile, 1.0) ?: return null
        val scale = if (minOf(tFull.cols(), tFull.rows()) >= 24) 0.5 else 1.0
        val t = if (scale < 1.0) template(templateFile, scale) ?: return null else tFull
        val src = frame.gray(scale)
        if (t.cols() > src.cols() || t.rows() > src.rows()) return null

        val result = Mat()
        try {
            Imgproc.matchTemplate(src, t, result, Imgproc.TM_CCOEFF_NORMED)
            val mm = Core.minMaxLoc(result)
            if (mm.maxVal.isNaN() || mm.maxVal < threshold) return null
            val cx = ((mm.maxLoc.x + t.cols() / 2.0) / scale).toInt()
            val cy = ((mm.maxLoc.y + t.rows() / 2.0) / scale).toInt()
            return Match(cx, cy, mm.maxVal)
        } finally {
            result.release()
        }
    }

    @Synchronized
    private fun template(file: File, scale: Double): Mat? {
        val key = "${file.absolutePath}@${file.lastModified()}@$scale"
        templateCache[key]?.let { return it }
        val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        val rgba = Mat()
        Utils.bitmapToMat(bmp, rgba)
        val gray = Mat()
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
        rgba.release()
        val out = if (scale < 1.0) {
            Mat().also {
                Imgproc.resize(gray, it, Size(), scale, scale, Imgproc.INTER_AREA)
                gray.release()
            }
        } else {
            gray
        }
        templateCache[key] = out
        return out
    }
}
