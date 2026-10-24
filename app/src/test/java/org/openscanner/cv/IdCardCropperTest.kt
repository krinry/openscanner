package org.openscanner.cv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Unit tests for IdCardCropper ISO/IEC 7810 ID-1 standard aspect ratio math
 * and CameraX FILL_CENTER screen-to-bitmap projection.
 */
class IdCardCropperTest {

    @Test
    fun testIdCardAspectRatioConstant() {
        assertEquals(1.586f, IdCardCropper.ID1_ASPECT_RATIO, 0.001f)
    }

    @Test
    fun testCardWidthRatioConstant() {
        assertEquals(0.85f, IdCardCropper.CARD_WIDTH_RATIO, 0.001f)
    }

    @Test
    fun testCalculateCropRectStandardScreen() {
        val bitmapWidth = 3000
        val bitmapHeight = 4000
        val viewWidth = 1080
        val viewHeight = 2400

        val rect = IdCardCropper.calculateCropRect(bitmapWidth, bitmapHeight, viewWidth, viewHeight)
        val rectW = rect.right - rect.left
        val rectH = rect.bottom - rect.top

        // Verify crop rectangle coordinates are within bitmap bounds
        assertTrue("cropX >= 0", rect.left >= 0)
        assertTrue("cropY >= 0", rect.top >= 0)
        assertTrue("cropRight <= bitmapWidth", rect.right <= bitmapWidth)
        assertTrue("cropBottom <= bitmapHeight", rect.bottom <= bitmapHeight)

        // Verify exact values calculated from CameraX FILL_CENTER projection
        assertEquals(735, rect.left)
        assertEquals(1517, rect.top)
        assertEquals(1530, rectW)
        assertEquals(964, rectH)

        // Verify aspect ratio matches ID-1 within integer rounding
        val cropAspect = rectW.toFloat() / rectH.toFloat()
        assertTrue(abs(cropAspect - IdCardCropper.ID1_ASPECT_RATIO) < 0.01f)
    }

    @Test
    fun testCalculateCropRectFallbackZeroDimensions() {
        val rect = IdCardCropper.calculateCropRect(
            bitmapWidth = 1000,
            bitmapHeight = 800,
            viewWidth = 0,
            viewHeight = 0
        )
        val rectW = rect.right - rect.left
        val rectH = rect.bottom - rect.top

        assertTrue(rectW > 0)
        assertTrue(rectH > 0)
        assertEquals(850, rectW) // 1000 * 0.85
        assertEquals(536, rectH) // round(850 / 1.586) = 536
        assertTrue(rect.left >= 0)
        assertTrue(rect.top >= 0)
        assertTrue(rect.right <= 1000)
        assertTrue(rect.bottom <= 800)
    }

    @Test
    fun testCalculateCropRectClamping() {
        // Extreme screen aspect ratio
        val rect = IdCardCropper.calculateCropRect(
            bitmapWidth = 1920,
            bitmapHeight = 1080,
            viewWidth = 5000,
            viewHeight = 500
        )
        val rectW = rect.right - rect.left
        val rectH = rect.bottom - rect.top

        assertTrue(rect.left >= 0)
        assertTrue(rect.top >= 0)
        assertTrue(rect.right <= 1920)
        assertTrue(rect.bottom <= 1080)
        assertTrue(rectW > 0)
        assertTrue(rectH > 0)
    }
}
