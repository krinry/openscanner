package org.openscanner.cv

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log

object NativeScannerEngine {
    private const val TAG = "NativeScannerEngine"
    private var isNativeLoaded = false

    init {
        try {
            System.loadLibrary("openscanner")
            isNativeLoaded = true
            Log.i(TAG, "Native library libopenscanner.so loaded successfully.")
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "Native library libopenscanner.so not loaded; falling back to pure Kotlin CV engine.", e)
            isNativeLoaded = false
        }
    }

    fun isAvailable(): Boolean = isNativeLoaded

    // Native declarations
    private external fun detectBounds(yuvData: ByteArray, width: Int, height: Int): FloatArray?
    private external fun warpPerspective(srcBitmap: Bitmap, dstBitmap: Bitmap, quadPoints: FloatArray)
    private external fun applyFilter(bitmap: Bitmap, filterMode: Int)
    private external fun adjustTones(bitmap: Bitmap, brightness: Int, contrast: Int)

    fun detectDocumentBounds(yuvData: ByteArray, width: Int, height: Int): DocumentQuadrilateral {
        if (isNativeLoaded) {
            try {
                val pts = detectBounds(yuvData, width, height)
                if (pts != null && pts.size >= 8) {
                    return DocumentQuadrilateral.fromFloatArray(pts)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in native detectBounds", e)
            }
        }
        return EdgeDetector.detectInYuv(yuvData, width, height)
    }

    fun detectDocumentBounds(bitmap: Bitmap): DocumentQuadrilateral {
        return EdgeDetector.detectInBitmap(bitmap)
    }

    fun warpDocument(srcBitmap: Bitmap, quad: DocumentQuadrilateral, targetWidth: Int, targetHeight: Int): Bitmap {
        val dstBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        if (isNativeLoaded) {
            try {
                warpPerspective(srcBitmap, dstBitmap, quad.toFloatArray())
                return dstBitmap
            } catch (e: Exception) {
                Log.e(TAG, "Error in native warpPerspective; falling back to Android Matrix", e)
            }
        }
        return PerspectiveTransformer.warp(srcBitmap, quad, targetWidth, targetHeight)
    }

    fun applyEnhancement(bitmap: Bitmap, modeId: Int) {
        if (isNativeLoaded) {
            try {
                applyFilter(bitmap, modeId)
                return
            } catch (e: Exception) {
                Log.e(TAG, "Error in native applyFilter", e)
            }
        }
        ImageEnhancer.applyFilterInPlace(bitmap, modeId)
    }

    fun adjustBrightnessContrast(bitmap: Bitmap, brightness: Int, contrast: Int) {
        if (isNativeLoaded) {
            try {
                adjustTones(bitmap, brightness, contrast)
                return
            } catch (e: Exception) {
                Log.e(TAG, "Error in native adjustTones", e)
            }
        }
        ImageEnhancer.adjustBrightnessContrastInPlace(bitmap, brightness, contrast)
    }
}
