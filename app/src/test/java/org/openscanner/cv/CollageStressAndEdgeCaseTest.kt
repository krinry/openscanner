package org.openscanner.cv

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import io.mockk.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

/**
 * Empirical Adversarial & Stress Harness for Collage Studio:
 * 1. Collage layout geometries across all 6 configurations (overlap, gutters, bounds)
 * 2. Extreme aspect ratios (100:1, 1:100, 1:1, arbitrary)
 * 3. Empty / recycled / inverted bitmap handling in drawBitmapFitted
 * 4. inSampleSize power-of-two calculations & fuzzing
 */
class CollageStressAndEdgeCaseTest {

    private fun makeRect(l: Int, t: Int, r: Int, b: Int): Rect = Rect().apply {
        left = l
        top = t
        right = r
        bottom = b
    }

    // =========================================================================
    // 1. COLLAGE GEOMETRY STRESS: All 6 Layouts
    // =========================================================================

    @Test
    fun testAllSixLayoutsStandardA4GeometryStress() {
        val canvasW = 2480
        val canvasH = 3508
        val margin = (canvasW * 0.04f).toInt() // 99
        val spacing = (canvasW * 0.02f).toInt() // 49

        assertEquals(99, margin)
        assertEquals(49, spacing)

        val layouts = CollageLayout.values()
        assertEquals(6, layouts.size)

        for (layout in layouts) {
            val slots = CollageGenerator.calculateSlotRects(layout, canvasW, canvasH)
            assertEquals("Layout $layout must produce maxPages slots", layout.maxPages, slots.size)

            // Verify each slot is within canvas margins
            for (idx in slots.indices) {
                val slot = slots[idx]
                assertTrue("Slot $idx in $layout left (${slot.left}) must be >= margin ($margin)", slot.left >= margin)
                assertTrue("Slot $idx in $layout top (${slot.top}) must be >= margin ($margin)", slot.top >= margin)
                assertTrue("Slot $idx in $layout right (${slot.right}) must be <= canvasW - margin (${canvasW - margin})", slot.right <= canvasW - margin)
                assertTrue("Slot $idx in $layout bottom (${slot.bottom}) must be <= canvasH - margin (${canvasH - margin})", slot.bottom <= canvasH - margin)
                assertTrue("Slot $idx in $layout width must be positive", slot.right > slot.left)
                assertTrue("Slot $idx in $layout height must be positive", slot.bottom > slot.top)
            }

            // Pairwise non-overlap verification
            for (i in 0 until slots.size) {
                for (j in i + 1 until slots.size) {
                    val s1 = slots[i]
                    val s2 = slots[j]
                    val xOverlap = maxOf(s1.left, s2.left) < minOf(s1.right, s2.right)
                    val yOverlap = maxOf(s1.top, s2.top) < minOf(s1.bottom, s2.bottom)
                    assertFalse(
                        "Slots $i ($s1) and $j ($s2) in layout $layout must NOT overlap",
                        xOverlap && yOverlap
                    )
                }
            }
        }
    }

    @Test
    fun testLayoutSpecificGuttersAndDimensions() {
        val canvasW = 2480
        val canvasH = 3508
        val spacing = 49

        // 1. TWO_VERTICAL
        val twoVert = CollageGenerator.calculateSlotRects(CollageLayout.TWO_VERTICAL, canvasW, canvasH)
        assertEquals(2, twoVert.size)
        assertEquals(spacing, twoVert[1].top - twoVert[0].bottom)
        assertEquals(twoVert[0].left, twoVert[1].left)
        assertEquals(twoVert[0].right, twoVert[1].right)
        assertEquals(twoVert[0].bottom - twoVert[0].top, twoVert[1].bottom - twoVert[1].top)

        // 2. TWO_HORIZONTAL
        val twoHoriz = CollageGenerator.calculateSlotRects(CollageLayout.TWO_HORIZONTAL, canvasW, canvasH)
        assertEquals(2, twoHoriz.size)
        assertEquals(spacing, twoHoriz[1].left - twoHoriz[0].right)
        assertEquals(twoHoriz[0].top, twoHoriz[1].top)
        assertEquals(twoHoriz[0].bottom, twoHoriz[1].bottom)
        assertEquals(twoHoriz[0].right - twoHoriz[0].left, twoHoriz[1].right - twoHoriz[1].left)

        // 3. THREE_VERTICAL
        val threeVert = CollageGenerator.calculateSlotRects(CollageLayout.THREE_VERTICAL, canvasW, canvasH)
        assertEquals(3, threeVert.size)
        assertEquals(spacing, threeVert[1].top - threeVert[0].bottom)
        assertEquals(spacing, threeVert[2].top - threeVert[1].bottom)
        for (s in threeVert) {
            assertEquals(99, s.left)
            assertEquals(2381, s.right)
        }

        // 4. THREE_TOP_TWO_BOTTOM
        val threeTopTwoBottom = CollageGenerator.calculateSlotRects(CollageLayout.THREE_TOP_TWO_BOTTOM, canvasW, canvasH)
        assertEquals(3, threeTopTwoBottom.size)
        // Top slot is full width
        assertEquals(99, threeTopTwoBottom[0].left)
        assertEquals(2381, threeTopTwoBottom[0].right)
        // Gutter between top and bottom row
        assertEquals(spacing, threeTopTwoBottom[1].top - threeTopTwoBottom[0].bottom)
        assertEquals(spacing, threeTopTwoBottom[2].top - threeTopTwoBottom[0].bottom)
        // Gutter between bottom two slots
        assertEquals(spacing, threeTopTwoBottom[2].left - threeTopTwoBottom[1].right)
        // Bottom two slots have identical vertical span
        assertEquals(threeTopTwoBottom[1].top, threeTopTwoBottom[2].top)
        assertEquals(threeTopTwoBottom[1].bottom, threeTopTwoBottom[2].bottom)

        // 5. FOUR_GRID
        val fourGrid = CollageGenerator.calculateSlotRects(CollageLayout.FOUR_GRID, canvasW, canvasH)
        assertEquals(4, fourGrid.size)
        assertEquals(spacing, fourGrid[1].left - fourGrid[0].right)
        assertEquals(spacing, fourGrid[3].left - fourGrid[2].right)
        assertEquals(spacing, fourGrid[2].top - fourGrid[0].bottom)
        assertEquals(spacing, fourGrid[3].top - fourGrid[1].bottom)

        // 6. SIX_GRID
        val sixGrid = CollageGenerator.calculateSlotRects(CollageLayout.SIX_GRID, canvasW, canvasH)
        assertEquals(6, sixGrid.size)
        // Horizontal gutters for 3 rows
        assertEquals(spacing, sixGrid[1].left - sixGrid[0].right)
        assertEquals(spacing, sixGrid[3].left - sixGrid[2].right)
        assertEquals(spacing, sixGrid[5].left - sixGrid[4].right)
        // Vertical gutters between rows
        assertEquals(spacing, sixGrid[2].top - sixGrid[0].bottom)
        assertEquals(spacing, sixGrid[3].top - sixGrid[1].bottom)
        assertEquals(spacing, sixGrid[4].top - sixGrid[2].bottom)
        assertEquals(spacing, sixGrid[5].top - sixGrid[3].bottom)
    }

    @Test
    fun testNonStandardCanvasDimensionsStress() {
        val testDimensions = listOf(
            Pair(1000, 1000),   // Square canvas
            Pair(1200, 1800),   // 2:3 ratio
            Pair(3000, 2000),   // Landscape canvas
            Pair(500, 1000),    // Tall narrow canvas
            Pair(4000, 6000)    // High-res canvas
        )

        for ((w, h) in testDimensions) {
            val margin = (w * 0.04f).toInt()
            val spacing = (w * 0.02f).toInt()

            for (layout in CollageLayout.values()) {
                val slots = CollageGenerator.calculateSlotRects(layout, w, h)
                assertEquals(layout.maxPages, slots.size)

                for (idx in slots.indices) {
                    val s = slots[idx]
                    assertTrue(s.left >= margin)
                    assertTrue(s.top >= margin)
                    assertTrue(s.right <= w - margin)
                    assertTrue(s.bottom <= h - margin)
                    assertTrue("Slot width in $layout ($w x $h) must be positive", s.right > s.left)
                    assertTrue("Slot height in $layout ($w x $h) must be positive", s.bottom > s.top)
                }

                // Check no overlaps
                for (i in 0 until slots.size) {
                    for (j in i + 1 until slots.size) {
                        val s1 = slots[i]
                        val s2 = slots[j]
                        val xOverlap = maxOf(s1.left, s2.left) < minOf(s1.right, s2.right)
                        val yOverlap = maxOf(s1.top, s2.top) < minOf(s1.bottom, s2.bottom)
                        assertFalse("Overlap detected in $layout at canvas $w x $h", xOverlap && yOverlap)
                    }
                }
            }
        }
    }

    // =========================================================================
    // 2. EXTREME ASPECT RATIOS: fitCenter Math Verification
    // =========================================================================

    private fun computeFitCenter(slotW: Float, slotH: Float, bmpW: Float, bmpH: Float): Pair<Float, Float> {
        val scale = minOf(slotW / bmpW, slotH / bmpH)
        val drawW = bmpW * scale
        val drawH = bmpH * scale
        return Pair(drawW, drawH)
    }

    @Test
    fun testFitCenterExtremeAspectRatios() {
        val slotWidths = listOf(1116f, 2282f, 500f, 1000f)
        val slotHeights = listOf(1070f, 1630f, 3310f, 1000f)

        // Extreme ratios: 100:1, 1:100, 1:1, 500:1, 1:500, 16:9, 9:16
        val aspectRatios = listOf(
            Pair(10000f, 100f),    // 100:1 Ultra-wide panorama
            Pair(100f, 10000f),    // 1:100 Ultra-tall receipt/strip
            Pair(1000f, 1000f),    // 1:1 Square
            Pair(50000f, 100f),    // 500:1 Super extreme wide
            Pair(100f, 50000f),    // 1:500 Super extreme tall
            Pair(1920f, 1080f),    // 16:9 Landscape
            Pair(1080f, 1920f),    // 9:16 Portrait
            Pair(1f, 1000f),       // 1:1000 Micro width
            Pair(1000f, 1f)        // 1000:1 Micro height
        )

        for (sw in slotWidths) {
            for (sh in slotHeights) {
                for ((bw, bh) in aspectRatios) {
                    val (dw, dh) = computeFitCenter(sw, sh, bw, bh)

                    // 1. Drawn size never exceeds slot dimensions
                    assertTrue("DrawW ($dw) must be <= slotW ($sw)", dw <= sw + 0.001f)
                    assertTrue("DrawH ($dh) must be <= slotH ($sh)", dh <= sh + 0.001f)
                    assertTrue("DrawW must be positive", dw > 0f)
                    assertTrue("DrawH must be positive", dh > 0f)

                    // 2. At least one dimension meets the slot boundary (fitCenter property)
                    val hitsWidth = abs(dw - sw) < 0.01f
                    val hitsHeight = abs(dh - sh) < 0.01f
                    assertTrue("At least one dimension must reach the slot edge (hitsW=$hitsWidth, hitsH=$hitsHeight)", hitsWidth || hitsHeight)

                    // 3. Aspect ratio is perfectly preserved
                    val originalAspect = bw / bh
                    val drawnAspect = dw / dh
                    assertEquals("Aspect ratio must be preserved", originalAspect, drawnAspect, 0.001f)

                    // 4. Centering offsets are non-negative and symmetrical
                    val offsetLeft = (sw - dw) / 2f
                    val offsetTop = (sh - dh) / 2f
                    assertTrue("Left offset must be non-negative", offsetLeft >= -0.001f)
                    assertTrue("Top offset must be non-negative", offsetTop >= -0.001f)

                    val rightRemaining = sw - (offsetLeft + dw)
                    val bottomRemaining = sh - (offsetTop + dh)
                    assertEquals("Horizontal centering must be symmetrical", offsetLeft, rightRemaining, 0.001f)
                    assertEquals("Vertical centering must be symmetrical", offsetTop, bottomRemaining, 0.001f)
                }
            }
        }
    }

    // =========================================================================
    // 3. EMPTY / RECYCLED / INVALID BITMAPS: drawBitmapFitted Graceful Handling
    // =========================================================================

    @Test
    fun testDrawBitmapFittedRecycledBitmapNoOp() {
        val mockCanvas = mockk<Canvas>(relaxed = true)
        val mockBitmap = mockk<Bitmap>(relaxed = true)
        every { mockBitmap.isRecycled } returns true
        every { mockBitmap.width } returns 1000
        every { mockBitmap.height } returns 1000

        val slot = makeRect(100, 100, 500, 500)
        CollageGenerator.drawBitmapFitted(mockCanvas, mockBitmap, slot)

        verify(exactly = 0) { mockCanvas.drawBitmap(any(), any(), any<RectF>(), any()) }
        verify(exactly = 0) { mockCanvas.drawRect(any<RectF>(), any()) }
    }

    @Test
    fun testDrawBitmapFittedZeroOrNegativeDimensionsNoOp() {
        val mockCanvas = mockk<Canvas>(relaxed = true)
        val mockBitmap = mockk<Bitmap>(relaxed = true)
        every { mockBitmap.isRecycled } returns false

        val slot = makeRect(100, 100, 500, 500)

        // Zero width
        every { mockBitmap.width } returns 0
        every { mockBitmap.height } returns 1000
        CollageGenerator.drawBitmapFitted(mockCanvas, mockBitmap, slot)
        verify(exactly = 0) { mockCanvas.drawBitmap(any(), any(), any<RectF>(), any()) }

        // Negative width
        every { mockBitmap.width } returns -100
        every { mockBitmap.height } returns 1000
        CollageGenerator.drawBitmapFitted(mockCanvas, mockBitmap, slot)
        verify(exactly = 0) { mockCanvas.drawBitmap(any(), any(), any<RectF>(), any()) }

        // Zero height
        every { mockBitmap.width } returns 1000
        every { mockBitmap.height } returns 0
        CollageGenerator.drawBitmapFitted(mockCanvas, mockBitmap, slot)
        verify(exactly = 0) { mockCanvas.drawBitmap(any(), any(), any<RectF>(), any()) }

        // Negative height
        every { mockBitmap.width } returns 1000
        every { mockBitmap.height } returns -50
        CollageGenerator.drawBitmapFitted(mockCanvas, mockBitmap, slot)
        verify(exactly = 0) { mockCanvas.drawBitmap(any(), any(), any<RectF>(), any()) }
    }

    @Test
    fun testDrawBitmapFittedZeroOrInvertedSlotRectNoOp() {
        val mockCanvas = mockk<Canvas>(relaxed = true)
        val mockBitmap = mockk<Bitmap>(relaxed = true)
        every { mockBitmap.isRecycled } returns false
        every { mockBitmap.width } returns 1000
        every { mockBitmap.height } returns 1000

        // Collapsed slot: width = 0
        val zeroWidthSlot = makeRect(100, 100, 100, 500)
        CollageGenerator.drawBitmapFitted(mockCanvas, mockBitmap, zeroWidthSlot)
        verify(exactly = 0) { mockCanvas.drawBitmap(any(), any(), any<RectF>(), any()) }

        // Collapsed slot: height = 0
        val zeroHeightSlot = makeRect(100, 100, 500, 100)
        CollageGenerator.drawBitmapFitted(mockCanvas, mockBitmap, zeroHeightSlot)
        verify(exactly = 0) { mockCanvas.drawBitmap(any(), any(), any<RectF>(), any()) }

        // Inverted slot: right < left
        val invertedWSlot = makeRect(500, 100, 100, 500)
        CollageGenerator.drawBitmapFitted(mockCanvas, mockBitmap, invertedWSlot)
        verify(exactly = 0) { mockCanvas.drawBitmap(any(), any(), any<RectF>(), any()) }

        // Inverted slot: bottom < top
        val invertedHSlot = makeRect(100, 500, 500, 100)
        CollageGenerator.drawBitmapFitted(mockCanvas, mockBitmap, invertedHSlot)
        verify(exactly = 0) { mockCanvas.drawBitmap(any(), any(), any<RectF>(), any()) }
    }

    @Test
    fun testDrawBitmapFittedValidCallsCanvas() {
        val mockCanvas = mockk<Canvas>(relaxed = true)
        val mockBitmap = mockk<Bitmap>(relaxed = true)
        every { mockBitmap.isRecycled } returns false
        every { mockBitmap.width } returns 1000
        every { mockBitmap.height } returns 800

        val slot = makeRect(100, 100, 500, 500)
        val borderPaint = mockk<Paint>(relaxed = true)

        CollageGenerator.drawBitmapFitted(mockCanvas, mockBitmap, slot, borderPaint)

        verify(exactly = 1) { mockCanvas.drawBitmap(mockBitmap, null, any<RectF>(), any()) }
        verify(exactly = 1) { mockCanvas.drawRect(any<RectF>(), borderPaint) }
    }

    // =========================================================================
    // 4. MEMORY DOWNSAMPLING: calculateInSampleSize Power-of-Two Math
    // =========================================================================

    @Test
    fun testCalculateInSampleSizeComprehensivePowersOfTwo() {
        // Test standard sensor/camera aspect ratios and typical collage slots
        val testCases = listOf(
            // rawW, rawH, reqW, reqH, expectedInSampleSize
            Tuple(4000, 3000, 1116, 1070, 2),  // half = 2000x1500 >= req
            Tuple(8000, 6000, 1116, 1070, 4),  // half = 4000x3000, /2 = 2000x1500 >= req
            Tuple(16000, 12000, 1116, 1070, 8),
            Tuple(1000, 800, 2282, 1630, 1),   // smaller than target
            Tuple(2282, 1630, 2282, 1630, 1),  // exact match
            Tuple(4564, 3260, 2282, 1630, 2),  // exact 2x
            Tuple(9128, 6520, 2282, 1630, 4),  // exact 4x
            Tuple(10000, 100, 1000, 1000, 1),  // asymmetric: height is small -> cannot downsample
            Tuple(100, 10000, 1000, 1000, 1)   // asymmetric: width is small -> cannot downsample
        )

        for (case in testCases) {
            val sampleSize = CollageGenerator.calculateInSampleSize(case.w, case.h, case.rw, case.rh)
            assertEquals(
                "calculateInSampleSize(${case.w}, ${case.h}, ${case.rw}, ${case.rh}) failed",
                case.expected,
                sampleSize
            )
            // Assert strict power of 2
            assertTrue("Sample size ($sampleSize) must be power of 2", (sampleSize and (sampleSize - 1)) == 0)
            assertTrue("Sample size must be >= 1", sampleSize >= 1)
        }
    }

    @Test
    fun testCalculateInSampleSizeFuzzingProperty() {
        val random = Random(42)

        for (iteration in 0 until 5000) {
            val rawW = random.nextInt(1, 20000)
            val rawH = random.nextInt(1, 20000)
            val reqW = random.nextInt(1, 4000)
            val reqH = random.nextInt(1, 4000)

            val inSampleSize = CollageGenerator.calculateInSampleSize(rawW, rawH, reqW, reqH)

            // 1. Must be >= 1
            assertTrue(inSampleSize >= 1)

            // 2. Must strictly be a power of two
            assertTrue(
                "inSampleSize ($inSampleSize) for ($rawW, $rawH, $reqW, $reqH) must be power of 2",
                (inSampleSize and (inSampleSize - 1)) == 0
            )

            // 3. If inSampleSize > 1, the halved dimensions must keep both width and height >= target
            if (inSampleSize > 1) {
                val halfW = rawW / 2
                val halfH = rawH / 2
                val prevSampleSize = inSampleSize / 2
                assertTrue(
                    "At previous power of two ($prevSampleSize), both dimensions must be >= requested",
                    (halfW / prevSampleSize) >= reqW && (halfH / prevSampleSize) >= reqH
                )
            }
        }
    }

    @Test
    fun testDecodeSampledBitmapNonExistentFileReturnsNull() {
        val nonExistentPath = "/path/to/non_existent_scan_image_12345.jpg"
        val bmp = CollageGenerator.decodeSampledBitmap(nonExistentPath, 1000, 1000)
        assertNull("Non-existent file must safely decode to null without crashing", bmp)
    }

    private data class Tuple(val w: Int, val h: Int, val rw: Int, val rh: Int, val expected: Int)
}
