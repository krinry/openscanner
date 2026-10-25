package org.openscanner.camera

import android.app.Application
import androidx.camera.core.ImageCapture
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.openscanner.cv.DocumentQuadrilateral
import org.openscanner.cv.PointF
import org.openscanner.ui.camera.CameraViewModel

/**
 * Tier 1 & Tier 2: Tests for CameraViewModel state management, mode transitions, and flash toggling.
 */
class CameraViewModelTest {

    private lateinit var application: Application
    private lateinit var viewModel: CameraViewModel

    @Before
    fun setUp() {
        application = mockk(relaxed = true)
        viewModel = CameraViewModel(application)
    }

    @Test
    fun testInitialCameraState() {
        assertEquals(CaptureMode.DOCUMENT, viewModel.currentMode.value)
        assertEquals(0, viewModel.batchCount.value)
        assertEquals(ImageCapture.FLASH_MODE_OFF, viewModel.flashMode.value)
        assertNull(viewModel.detectedQuad.value)
        assertEquals(Pair(0, 0), viewModel.previewDimens.value)
    }

    @Test
    fun testSetModeTransitions() {
        viewModel.setMode(CaptureMode.ID_CARD)
        assertEquals(CaptureMode.ID_CARD, viewModel.currentMode.value)

        viewModel.setMode(CaptureMode.BOOK)
        assertEquals(CaptureMode.BOOK, viewModel.currentMode.value)

        viewModel.setMode(CaptureMode.QR_CODE)
        assertEquals(CaptureMode.QR_CODE, viewModel.currentMode.value)

        viewModel.setMode(CaptureMode.DOCUMENT)
        assertEquals(CaptureMode.DOCUMENT, viewModel.currentMode.value)
    }

    @Test
    fun testFlashToggleCycle() {
        // Starts OFF
        assertEquals(ImageCapture.FLASH_MODE_OFF, viewModel.flashMode.value)

        // OFF -> ON
        viewModel.toggleFlash()
        assertEquals(ImageCapture.FLASH_MODE_ON, viewModel.flashMode.value)

        // ON -> AUTO
        viewModel.toggleFlash()
        assertEquals(ImageCapture.FLASH_MODE_AUTO, viewModel.flashMode.value)

        // AUTO -> OFF
        viewModel.toggleFlash()
        assertEquals(ImageCapture.FLASH_MODE_OFF, viewModel.flashMode.value)
    }

    @Test
    fun testDocumentDetectionStateUpdate() {
        val quad = DocumentQuadrilateral(
            topLeft = PointF(10f, 10f),
            topRight = PointF(90f, 10f),
            bottomRight = PointF(90f, 90f),
            bottomLeft = PointF(10f, 90f)
        )

        viewModel.onDocumentDetected(quad, 1080, 1920)

        assertEquals(quad, viewModel.detectedQuad.value)
        assertEquals(Pair(1080, 1920), viewModel.previewDimens.value)
    }
}
