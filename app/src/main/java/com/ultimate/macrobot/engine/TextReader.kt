package com.ultimate.macrobot.engine

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/** On-device OCR (Google ML Kit, bundled Latin-script model). Call from a background thread. */
object TextReader {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    fun read(bitmap: Bitmap): List<List<OcrWord>> = try {
        val result = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)))
        result.textBlocks.flatMap { it.lines }.map { line ->
            line.elements.mapNotNull { el ->
                el.boundingBox?.let { OcrWord(el.text, it.left, it.top, it.right, it.bottom) }
            }
        }.filter { it.isNotEmpty() }
    } catch (e: Exception) {
        emptyList()
    }
}
