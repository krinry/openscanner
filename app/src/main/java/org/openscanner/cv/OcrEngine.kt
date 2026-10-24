package org.openscanner.cv

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * On-device offline OCR engine powered by bundled Google ML Kit Latin Text Recognition.
 * Operates 100% offline without external network connectivity or Google Play Services.
 */
object OcrEngine {

    private const val TAG = "OcrEngine"

    // Mutex ensuring thread-safe, serialized access to ML Kit recognition to prevent OOM
    private val ocrMutex = Mutex()

    // Lazy initialization of bundled Latin text recognizer
    private val recognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Extracts text from the provided [bitmap] entirely on-device and offline.
     *
     * @param bitmap The image bitmap to extract text from.
     * @param rotationDegrees The clockwise rotation degrees (0, 90, 180, 270) if applicable.
     * @return Extracted plain text string, or empty string if no text is detected or on error.
     */
    suspend fun recognizeText(
        bitmap: Bitmap,
        rotationDegrees: Int = 0
    ): String = withContext(Dispatchers.Default) {
        if (bitmap.isRecycled) {
            Log.w(TAG, "Input bitmap is recycled; returning empty text.")
            return@withContext ""
        }

        ocrMutex.withLock {
            try {
                suspendCancellableCoroutine { continuation ->
                    try {
                        val inputImage = InputImage.fromBitmap(bitmap, rotationDegrees)

                        recognizer.process(inputImage)
                            .addOnSuccessListener { visionText ->
                                if (continuation.isActive) {
                                    val text = visionText.text.trim()
                                    continuation.resume(text)
                                }
                            }
                            .addOnFailureListener { exception ->
                                if (continuation.isActive) {
                                    Log.e(TAG, "ML Kit text recognition failed", exception)
                                    // Complete safely with empty string so caller doesn't crash
                                    continuation.resume("")
                                }
                            }
                            .addOnCanceledListener {
                                if (continuation.isActive) {
                                    continuation.cancel()
                                }
                            }
                    } catch (e: Throwable) {
                        if (continuation.isActive) {
                            if (e is CancellationException) {
                                continuation.cancel(e)
                            } else {
                                Log.e(TAG, "Failed to create InputImage or start OCR task", e)
                                continuation.resume("")
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during OCR execution", e)
                ""
            }
        }
    }

    /**
     * Releases ML Kit resources when no longer needed.
     */
    fun close() {
        try {
            recognizer.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing TextRecognizer", e)
        }
    }
}
