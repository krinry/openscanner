package org.openscanner.camera

import android.app.Application
import android.graphics.Bitmap
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.openscanner.cv.DocumentQuadrilateral
import org.openscanner.cv.PointF
import org.openscanner.ui.camera.BatchCaptureItem
import org.openscanner.ui.camera.CameraViewModel

class CameraViewModelBatchTest {

    private lateinit var application: Application
    private lateinit var viewModel: CameraViewModel

    @Before
    fun setUp() {
        mockkStatic(Bitmap::class)
        val dummyBitmap = mockk<Bitmap>(relaxed = true)
        every { Bitmap.createBitmap(any<Bitmap>(), any(), any(), any(), any(), any(), any()) } returns dummyBitmap
        every { Bitmap.createBitmap(any<Int>(), any<Int>(), any()) } returns dummyBitmap

        application = mockk(relaxed = true)
        viewModel = CameraViewModel(application)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testInitialBatchQueueIsEmpty() {
        assertTrue(viewModel.batchQueue.value.isEmpty())
        assertEquals(0, viewModel.batchCount.value)
    }

    @Test
    fun testBatchItemStateAndRotationCycle() {
        val dummyBitmap = mockk<Bitmap>(relaxed = true)
        val item = BatchCaptureItem(
            id = 101L,
            rawFilePath = "/cache/temp_scans/burst_1.jpg",
            thumbnailBitmap = dummyBitmap,
            rotationDegrees = 0
        )

        assertEquals(0, item.rotationDegrees)
        assertEquals(101L, item.id)
        assertNull(item.cropQuad)

        // Rotate clockwise cycle
        val r90 = (item.rotationDegrees + 90) % 360
        assertEquals(90, r90)
        val r180 = (r90 + 90) % 360
        assertEquals(180, r180)
        val r270 = (r180 + 90) % 360
        assertEquals(270, r270)
        val r360 = (r270 + 90) % 360
        assertEquals(0, r360)

        // Rotate counter-clockwise cycle
        val ccw = (item.rotationDegrees - 90 + 360) % 360
        assertEquals(270, ccw)
    }

    @Test
    fun testAddAndRemoveBatchItem() {
        val dummyBitmap = mockk<Bitmap>(relaxed = true)
        val item1 = BatchCaptureItem(id = 101L, rawFilePath = "/cache/b1.jpg", thumbnailBitmap = dummyBitmap)
        val item2 = BatchCaptureItem(id = 102L, rawFilePath = "/cache/b2.jpg", thumbnailBitmap = dummyBitmap)

        viewModel.addBatchItem(item1)
        assertEquals(1, viewModel.batchQueue.value.size)
        assertEquals(1, viewModel.batchCount.value)

        viewModel.addBatchItem(item2)
        assertEquals(2, viewModel.batchQueue.value.size)
        assertEquals(2, viewModel.batchCount.value)

        viewModel.removeBatchItem(101L)
        assertEquals(1, viewModel.batchQueue.value.size)
        assertEquals(102L, viewModel.batchQueue.value[0].id)
        assertEquals(1, viewModel.batchCount.value)
    }

    @Test
    fun testUpdateBatchItemCropQuad() {
        val dummyBitmap = mockk<Bitmap>(relaxed = true)
        val item = BatchCaptureItem(
            id = 202L,
            rawFilePath = "/cache/burst_2.jpg",
            thumbnailBitmap = dummyBitmap
        )
        viewModel.addBatchItem(item)

        val quad = DocumentQuadrilateral(
            topLeft = PointF(5f, 5f),
            topRight = PointF(95f, 5f),
            bottomRight = PointF(95f, 95f),
            bottomLeft = PointF(5f, 95f)
        )

        viewModel.updateBatchItemCrop(202L, quad)
        assertEquals(quad, viewModel.batchQueue.value[0].cropQuad)
    }

    @Test
    fun testClearBatch() {
        val dummyBitmap = mockk<Bitmap>(relaxed = true)
        val item = BatchCaptureItem(id = 303L, rawFilePath = "/cache/b3.jpg", thumbnailBitmap = dummyBitmap)
        viewModel.addBatchItem(item)
        assertEquals(1, viewModel.batchCount.value)

        viewModel.clearBatch()
        assertTrue(viewModel.batchQueue.value.isEmpty())
        assertEquals(0, viewModel.batchCount.value)
    }
}
