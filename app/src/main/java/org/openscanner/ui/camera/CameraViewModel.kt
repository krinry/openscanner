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
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openscanner.OpenScannerApp
import org.openscanner.camera.BatchWarpProcessor
import org.openscanner.camera.CaptureMode
import org.openscanner.data.model.EnhanceMode
import org.openscanner.cv.DocumentQuadrilateral
import org.openscanner.cv.PointF
import org.openscanner.data.repository.ScannerRepository
import org.openscanner.data.repository.SettingsManager
import android.media.MediaActionSound
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BatchCaptureItem(
    val id: Long = System.nanoTime(),
    val rawFilePath: String,
    val thumbnailBitmap: Bitmap,
    var rotationDegrees: Int = 0,
    var cropQuad: DocumentQuadrilateral? = null
)

enum class QualityMode(val label: String, val description: String) {
    SD("SD", "Standard 1080p, faster capture"),
    HD("HD", "High Definition balanced"),
    UHD("UHD", "Maximum sensor resolution")
}

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val _currentMode = MutableStateFlow(CaptureMode.DOCUMENT)
    val currentMode: StateFlow<CaptureMode> = _currentMode

    private val _detectedQuad = MutableStateFlow<DocumentQuadrilateral?>(null)
    val detectedQuad: StateFlow<DocumentQuadrilateral?> = _detectedQuad

    private val _previewDimens = MutableStateFlow(Pair(0, 0))
    val previewDimens: StateFlow<Pair<Int, Int>> = _previewDimens

    private val _flashMode = MutableStateFlow(ImageCapture.FLASH_MODE_OFF)
    val flashMode: StateFlow<Int> = _flashMode

    private val _qualityMode = MutableStateFlow(QualityMode.HD)
    val qualityMode: StateFlow<QualityMode> = _qualityMode

    private val _selectedFilter = MutableStateFlow(1) // 1 = Magic Color (default)
    val selectedFilter: StateFlow<Int> = _selectedFilter

    private val settingsManager = SettingsManager.getInstance(application)

    private val _cropAfterScan = MutableStateFlow(false)
    val cropAfterScan: StateFlow<Boolean> = _cropAfterScan

    private val _autoCropEnabled = MutableStateFlow(settingsManager.autoCropEnabled.value)
    val autoCropEnabled: StateFlow<Boolean> = _autoCropEnabled

    private val _batchCount = MutableStateFlow(0)
    val batchCount: StateFlow<Int> = _batchCount

    private val _batchQueue = MutableStateFlow<List<BatchCaptureItem>>(emptyList())
    val batchQueue: StateFlow<List<BatchCaptureItem>> = _batchQueue.asStateFlow()

    fun setMode(mode: CaptureMode) {
        _currentMode.value = mode
        _detectedQuad.value = null
    }

    fun onDocumentDetected(quad: DocumentQuadrilateral, width: Int, height: Int) {
        val prev = _detectedQuad.value
        val smoothed = if (prev == null || _previewDimens.value != Pair(width, height)) {
            quad
        } else {
            val alpha = 0.35f
            fun lerp(p1: PointF, p2: PointF): PointF =
                PointF(p1.x + alpha * (p2.x - p1.x), p1.y + alpha * (p2.y - p1.y))

            DocumentQuadrilateral(
                topLeft = lerp(prev.topLeft, quad.topLeft),
                topRight = lerp(prev.topRight, quad.topRight),
                bottomRight = lerp(prev.bottomRight, quad.bottomRight),
                bottomLeft = lerp(prev.bottomLeft, quad.bottomLeft)
            )
        }
        _detectedQuad.value = smoothed
        _previewDimens.value = Pair(width, height)
    }

    fun toggleFlash() {
        _flashMode.value = when (_flashMode.value) {
            ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
            ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
            else -> ImageCapture.FLASH_MODE_OFF
        }
    }

    fun cycleQuality(): QualityMode {
        val next = when (_qualityMode.value) {
            QualityMode.SD -> QualityMode.HD
            QualityMode.HD -> QualityMode.UHD
            QualityMode.UHD -> QualityMode.SD
        }
        _qualityMode.value = next
        return next
    }

    fun setQualityMode(mode: QualityMode) {
        _qualityMode.value = mode
    }

    fun setSelectedFilter(filterId: Int) {
        _selectedFilter.value = filterId
    }

    fun setCropAfterScan(enabled: Boolean) {
        _cropAfterScan.value = enabled
    }

    fun setAutoCropEnabled(enabled: Boolean) {
        _autoCropEnabled.value = enabled
        settingsManager.setAutoCropEnabled(enabled)
    }

    fun addBatchItem(item: BatchCaptureItem) {
        val updated = _batchQueue.value + item
        _batchQueue.value = updated
        _batchCount.value = updated.size
    }

    fun captureBatchPhoto(
        imageCapture: ImageCapture,
        onSuccess: (BatchCaptureItem) -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        val context = getApplication<Application>()
        if (settingsManager.shutterSoundEnabled.value) {
            try {
                MediaActionSound().play(MediaActionSound.SHUTTER_CLICK)
            } catch (ignored: Exception) {}
        }
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            val buffer = image.planes[0].buffer
                            val bytes = ByteArray(buffer.remaining())
                            buffer.get(bytes)
                            val rotation = image.imageInfo.rotationDegrees

                            // Decode lightweight thumbnail for live filmstrip queue
                            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
                            var sampleSize = 1
                            while (boundsOpts.outWidth / (sampleSize * 2) >= 320 && boundsOpts.outHeight / (sampleSize * 2) >= 320) {
                                sampleSize *= 2
                            }
                            val thumbOpts = BitmapFactory.Options().apply {
                                inSampleSize = sampleSize
                                inPreferredConfig = Bitmap.Config.ARGB_8888
                            }
                            var thumbBmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, thumbOpts)
                            if (rotation != 0 && thumbBmp != null) {
                                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                                val rotatedThumb = Bitmap.createBitmap(thumbBmp, 0, 0, thumbBmp.width, thumbBmp.height, matrix, true)
                                if (rotatedThumb != thumbBmp) {
                                    thumbBmp.recycle()
                                    thumbBmp = rotatedThumb
                                }
                            }

                            // Write raw JPEG to cacheDir/temp_scans/burst_...jpg
                            val cacheDir = File(context.cacheDir, "temp_scans").apply { if (!exists()) mkdirs() }
                            val burstFile = File(cacheDir, "burst_${System.currentTimeMillis()}_${_batchQueue.value.size + 1}.jpg")

                            if (rotation == 0) {
                                FileOutputStream(burstFile).use { it.write(bytes) }
                            } else {
                                val fullBmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                if (fullBmp != null) {
                                    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                                    val rotatedFull = Bitmap.createBitmap(fullBmp, 0, 0, fullBmp.width, fullBmp.height, matrix, true)
                                    FileOutputStream(burstFile).use { out ->
                                        rotatedFull.compress(Bitmap.CompressFormat.JPEG, 92, out)
                                    }
                                    if (rotatedFull != fullBmp) fullBmp.recycle()
                                    rotatedFull.recycle()
                                } else {
                                    FileOutputStream(burstFile).use { it.write(bytes) }
                                }
                            }

                            image.close()

                            val newItem = BatchCaptureItem(
                                id = System.nanoTime(),
                                rawFilePath = burstFile.absolutePath,
                                thumbnailBitmap = thumbBmp ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
                                rotationDegrees = 0,
                                cropQuad = null
                            )

                            withContext(Dispatchers.Main) {
                                val updated = _batchQueue.value + newItem
                                _batchQueue.value = updated
                                _batchCount.value = updated.size
                                onSuccess(newItem)
                            }
                        } catch (e: Exception) {
                            image.close()
                            withContext(Dispatchers.Main) {
                                onError(e)
                            }
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    onError(exception)
                }
            }
        )
    }

    fun removeBatchItem(id: Long) {
        val current = _batchQueue.value
        val itemToRemove = current.firstOrNull { it.id == id }
        if (itemToRemove != null) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    File(itemToRemove.rawFilePath).delete()
                } catch (e: Exception) { /* Ignore */ }
            }
            val updated = current.filter { it.id != id }
            _batchQueue.value = updated
            _batchCount.value = updated.size
        }
    }

    fun rotateBatchItem(id: Long, clockwise: Boolean = true) {
        val delta = if (clockwise) 90 else -90
        _batchQueue.value = _batchQueue.value.map { item ->
            if (item.id == id) {
                val newAngle = (item.rotationDegrees + delta + 360) % 360
                val matrix = Matrix().apply { postRotate(delta.toFloat()) }
                val rotatedThumb = Bitmap.createBitmap(
                    item.thumbnailBitmap,
                    0, 0,
                    item.thumbnailBitmap.width,
                    item.thumbnailBitmap.height,
                    matrix,
                    true
                )
                item.copy(
                    rotationDegrees = newAngle,
                    thumbnailBitmap = rotatedThumb
                )
            } else {
                item
            }
        }
    }

    fun updateBatchItemCrop(id: Long, quad: DocumentQuadrilateral) {
        _batchQueue.value = _batchQueue.value.map { item ->
            if (item.id == id) {
                item.copy(cropQuad = quad)
            } else {
                item
            }
        }
    }

    fun clearBatch() {
        val current = _batchQueue.value
        viewModelScope.launch(Dispatchers.IO) {
            for (item in current) {
                try {
                    File(item.rawFilePath).delete()
                } catch (e: Exception) { /* Ignore */ }
            }
        }
        _batchQueue.value = emptyList()
        _batchCount.value = 0
    }

    fun capturePhoto(
        imageCapture: ImageCapture,
        onPhotoCaptured: (savedPath: String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val context = getApplication<Application>()
        if (settingsManager.shutterSoundEnabled.value) {
            try {
                MediaActionSound().play(MediaActionSound.SHUTTER_CLICK)
            } catch (ignored: Exception) {}
        }
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    val rotationDegrees = image.imageInfo.rotationDegrees

                    var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (rotationDegrees != 0 && bitmap != null) {
                        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    }

                    // Save raw photo to cache file
                    val cacheDir = File(context.cacheDir, "temp_scans").apply { if (!exists()) mkdirs() }
                    val tempFile = File(cacheDir, "temp_capture_${System.currentTimeMillis()}.jpg")
                    if (bitmap != null) {
                        FileOutputStream(tempFile).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                        }
                    } else {
                        FileOutputStream(tempFile).use { out ->
                            out.write(bytes)
                        }
                    }

                    image.close()

                    if (_currentMode.value == CaptureMode.DOCUMENT) {
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

    fun addDocumentItemFromFile(
        filePath: String,
        onSuccess: (BatchCaptureItem) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(filePath, boundsOpts)
                var sampleSize = 1
                while (boundsOpts.outWidth / (sampleSize * 2) >= 320 && boundsOpts.outHeight / (sampleSize * 2) >= 320) {
                    sampleSize *= 2
                }
                val thumbOpts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val thumbBmp = BitmapFactory.decodeFile(filePath, thumbOpts)

                val newItem = BatchCaptureItem(
                    id = System.nanoTime(),
                    rawFilePath = filePath,
                    thumbnailBitmap = thumbBmp ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
                    rotationDegrees = 0,
                    cropQuad = null
                )

                withContext(Dispatchers.Main) {
                    val updated = _batchQueue.value + newItem
                    _batchQueue.value = updated
                    _batchCount.value = updated.size
                    onSuccess(newItem)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun saveBatchAsDraft(docId: Long = -1L): Long = withContext(Dispatchers.IO) {
        val items = _batchQueue.value
        if (items.isEmpty()) return@withContext -1L
        val context = getApplication<Application>()
        val app = context as OpenScannerApp
        val repo = ScannerRepository(
            context = context,
            documentDao = app.database.documentDao(),
            pageDao = app.database.pageDao(),
            folderDao = app.database.folderDao()
        )
        val processor = BatchWarpProcessor(context, repo)
        val draftTitle = "Draft " + SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date())
        val savedId = processor.processBatch(
            targetDocId = if (docId > 0) docId else -1L,
            items = items,
            selectedEnhanceMode = EnhanceMode.fromId(_selectedFilter.value),
            pageFilters = emptyMap(),
            brightness = 0,
            contrast = 0,
            pageAdjustments = emptyMap(),
            customTitle = draftTitle,
            onProgress = { _: Int, _: Int -> }
        )
        // Mark tags with "draft"
        if (savedId > 0) {
            try {
                val doc = repo.getDocumentById(savedId)
                if (doc != null) {
                    repo.updateDocument(doc.copy(tags = "draft"))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        _batchQueue.value = emptyList()
        _batchCount.value = 0
        savedId
    }
}
