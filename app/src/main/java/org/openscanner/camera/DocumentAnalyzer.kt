package org.openscanner.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.openscanner.cv.DocumentQuadrilateral
import org.openscanner.cv.NativeScannerEngine
import org.openscanner.cv.PointF
import java.nio.ByteBuffer

class DocumentAnalyzer(
    private val onDocumentDetected: (DocumentQuadrilateral, Int, Int) -> Unit
) : ImageAnalysis.Analyzer {

    private var lastAnalyzedTimestamp = 0L

    override fun analyze(image: ImageProxy) {
        val currentTimestamp = System.currentTimeMillis()
        // Throttle slightly to ~30-40 fps to conserve battery and CPU
        if (currentTimestamp - lastAnalyzedTimestamp < 25) {
            image.close()
            return
        }
        lastAnalyzedTimestamp = currentTimestamp

        val planes = image.planes
        if (planes.isEmpty()) {
            image.close()
            return
        }

        val yBuffer: ByteBuffer = planes[0].buffer
        val ySize = yBuffer.remaining()
        val yuvBytes = ByteArray(ySize)
        yBuffer.get(yuvBytes)

        val width = image.width
        val height = image.height

        val quad = NativeScannerEngine.detectDocumentBounds(yuvBytes, width, height)

        onDocumentDetected(quad, width, height)
        image.close()
    }
}
