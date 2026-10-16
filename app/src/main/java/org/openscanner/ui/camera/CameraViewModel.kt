package org.openscanner.ui.camera

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.openscanner.camera.CaptureMode
import org.openscanner.cv.DocumentQuadrilateral
import java.io.File
import java.io.FileOutputStream

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val _currentMode = MutableStateFlow(CaptureMode.SINGLE)
    val currentMode: StateFlow<CaptureMode> = _currentMode

    private val _detectedQuad = MutableStateFlow<DocumentQuadrilateral?>(null)
    val detectedQuad: StateFlow<DocumentQuadrilateral?> = _detectedQuad

    private val _previewDimens = MutableStateFlow(Pair(0, 0))
    val previewDimens: StateFlow<Pair<Int, Int>> = _previewDimens

    private val _flashMode = MutableStateFlow(ImageCapture.FLASH_MODE_OFF)
    val flashMode: StateFlow<Int> = _flashMode

    private val _batchCount = MutableStateFlow(0)
    val batchCount: StateFlow<Int> = _batchCount

    fun setMode(mode: CaptureMode) {
        _currentMode.value = mode
    }

    fun onDocumentDetected(quad: DocumentQuadrilateral, width: Int, height: Int) {
        _detectedQuad.value = quad
        _previewDimens.value = Pair(width, height)
    }

    fun toggleFlash() {
        _flashMode.value = when (_flashMode.value) {
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
            else -> ImageCapture.FLASH_MODE_OFF
        }
    }

    fun capturePhoto(
        imageCapture: ImageCapture,
        onPhotoCaptured: (savedPath: String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val context = getApplication<Application>()
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    val rotationDegrees = image.imageInfo.rotationDegrees

                    var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (rotationDegrees != 0) {
                        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    }

                    // Save raw photo to cache file
                    val cacheDir = File(context.cacheDir, "temp_scans").apply { if (!exists()) mkdirs() }
                    val tempFile = File(cacheDir, "temp_capture_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(tempFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                    }

                    image.close()

                    if (_currentMode.value == CaptureMode.BATCH) {
                        _batchCount.value += 1
                    }

                    onPhotoCaptured(tempFile.absolutePath)
                }

                override fun onError(exception: ImageCaptureException) {
                    onError(exception)
                }
            }
        )
    }
}
