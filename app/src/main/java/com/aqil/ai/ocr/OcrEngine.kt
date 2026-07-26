package com.aqil.ai.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One piece of recognised text plus where to tap it (screen pixels). */
data class OcrItem(val text: String, val x: Int, val y: Int)

/**
 * Offline text recognition (OCR) via ML Kit. The Latin model is bundled in the APK,
 * so this works with no network and no extra download.
 */
object OcrEngine {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /** OCR an image the user picked from their gallery. */
    suspend fun fromUri(context: Context, uri: Uri): String {
        val image = InputImage.fromFilePath(context, uri)
        return recognizeText(image)
    }

    /** OCR a bitmap (e.g. a screenshot of the current display) into plain text. */
    suspend fun fromBitmap(bitmap: Bitmap): String {
        val image = InputImage.fromBitmap(bitmap, 0)
        return recognizeText(image)
    }

    /** OCR a bitmap into positioned items so the model can tap them by coordinate. */
    suspend fun itemsFromBitmap(bitmap: Bitmap): List<OcrItem> {
        val image = InputImage.fromBitmap(bitmap, 0)
        return suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    val items = ArrayList<OcrItem>()
                    for (block in result.textBlocks) {
                        for (line in block.lines) {
                            val r = line.boundingBox ?: continue
                            val t = line.text.trim()
                            if (t.isNotEmpty()) items += OcrItem(t, r.centerX(), r.centerY())
                        }
                    }
                    cont.resume(items)
                }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
    }

    private suspend fun recognizeText(image: InputImage): String =
        suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { cont.resume(it.text) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
}
