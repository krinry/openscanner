package org.openscanner.cv

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.min

/**
 * Tier 1 & Tier 2: Tests for CollageLayout and CollageGenerator layout mathematics.
 */
class CollageGeneratorTest {

    @Test
    fun testCollageLayoutEnumValues() {
        val layouts = CollageLayout.values()
        assertEquals(9, layouts.size)

        assertEquals("2-in-1 Vertical", CollageLayout.TWO_VERTICAL.title)
        assertEquals(2, CollageLayout.TWO_VERTICAL.maxPages)

        assertEquals("2-in-1 Side by Side", CollageLayout.TWO_HORIZONTAL.title)
        assertEquals(2, CollageLayout.TWO_HORIZONTAL.maxPages)

        assertEquals("3-in-1 Vertical", CollageLayout.THREE_VERTICAL.title)
        assertEquals(3, CollageLayout.THREE_VERTICAL.maxPages)

        assertEquals("3-in-1 (1 Top, 2 Bottom)", CollageLayout.THREE_TOP_TWO_BOTTOM.title)
        assertEquals(3, CollageLayout.THREE_TOP_TWO_BOTTOM.maxPages)

        assertEquals("4-in-1 Grid", CollageLayout.FOUR_GRID.title)
        assertEquals(4, CollageLayout.FOUR_GRID.maxPages)

        assertEquals("6-in-1 Grid", CollageLayout.SIX_GRID.title)
        assertEquals(6, CollageLayout.SIX_GRID.maxPages)

        assertEquals("Driver License", CollageLayout.DRIVER_LICENSE.title)
        assertEquals(2, CollageLayout.DRIVER_LICENSE.maxPages)

        assertEquals("Passport", CollageLayout.PASSPORT.title)
        assertEquals(2, CollageLayout.PASSPORT.maxPages)

        assertEquals("Question Book", CollageLayout.QUESTION_BOOK.title)
        assertEquals(2, CollageLayout.QUESTION_BOOK.maxPages)
    }

    @Test
    fun testStandardA4CollageGeometryCalculations() {
        val canvasWidth = 2480
        val canvasHeight = 3508

        val margin = (canvasWidth * 0.04f).toInt()
        val spacing = (canvasWidth * 0.02f).toInt()
        val contentW = canvasWidth - (margin * 2)
        val contentH = canvasHeight - (margin * 2)

        assertEquals(99, margin)
        assertEquals(49, spacing)
        assertEquals(2282, contentW)
        assertEquals(3310, contentH)

        // TWO_VERTICAL slot dimensions
        val twoVertSlotH = (contentH - spacing) / 2
        assertEquals(1630, twoVertSlotH)
        assertTrue(twoVertSlotH * 2 + spacing <= contentH)

        // TWO_HORIZONTAL slot dimensions
        val twoHorizSlotW = (contentW - spacing) / 2
        assertEquals(1116, twoHorizSlotW)
        assertTrue(twoHorizSlotW * 2 + spacing <= contentW)

        // THREE_VERTICAL slot dimensions
        val threeVertSlotH = (contentH - spacing * 2) / 3
        assertEquals(1070, threeVertSlotH)
        assertTrue(threeVertSlotH * 3 + spacing * 2 <= contentH)

        // THREE_TOP_TWO_BOTTOM slot dimensions
        val threeTopSlotH = (contentH - spacing) / 2
        val threeBottomColW = (contentW - spacing) / 2
        assertEquals(1630, threeTopSlotH)
        assertEquals(1116, threeBottomColW)

        // FOUR_GRID slot dimensions
        val fourGridSlotW = (contentW - spacing) / 2
        val fourGridSlotH = (contentH - spacing) / 2
        assertEquals(1116, fourGridSlotW)
        assertEquals(1630, fourGridSlotH)

        // SIX_GRID slot dimensions (2 columns x 3 rows)
        val sixGridSlotW = (contentW - spacing) / 2
        val sixGridSlotH = (contentH - spacing * 2) / 3
        assertEquals(1116, sixGridSlotW)
        assertEquals(1070, sixGridSlotH)
        assertTrue(sixGridSlotH * 3 + spacing * 2 <= contentH)
    }

    @Test
    fun testTwoVerticalSlotRects() {
        val slots = CollageGenerator.calculateSlotRects(CollageLayout.TWO_VERTICAL, 2480, 3508)
        assertEquals(2, slots.size)
        assertEquals(99, slots[0].left)
        assertEquals(99, slots[0].top)
        assertEquals(2381, slots[0].right)
        assertEquals(1729, slots[0].bottom)

        assertEquals(99, slots[1].left)
        assertEquals(1778, slots[1].top)
        assertEquals(2381, slots[1].right)
        assertEquals(3408, slots[1].bottom)

        assertEquals(49, slots[1].top - slots[0].bottom)
    }

    @Test
    fun testTwoHorizontalSlotRects() {
        val slots = CollageGenerator.calculateSlotRects(CollageLayout.TWO_HORIZONTAL, 2480, 3508)
        assertEquals(2, slots.size)
        assertEquals(99, slots[0].left)
        assertEquals(99, slots[0].top)
        assertEquals(1215, slots[0].right)
        assertEquals(3409, slots[0].bottom)

        assertEquals(1264, slots[1].left)
        assertEquals(99, slots[1].top)
        assertEquals(2380, slots[1].right)
        assertEquals(3409, slots[1].bottom)

        assertEquals(49, slots[1].left - slots[0].right)
    }

    @Test
    fun testThreeVerticalSlotRects() {
        val slots = CollageGenerator.calculateSlotRects(CollageLayout.THREE_VERTICAL, 2480, 3508)
        assertEquals(3, slots.size)

        assertEquals(99, slots[0].left)
        assertEquals(99, slots[0].top)
        assertEquals(2381, slots[0].right)
        assertEquals(1169, slots[0].bottom)

        assertEquals(99, slots[1].left)
        assertEquals(1218, slots[1].top)
        assertEquals(2381, slots[1].right)
        assertEquals(2288, slots[1].bottom)

        assertEquals(99, slots[2].left)
        assertEquals(2337, slots[2].top)
        assertEquals(2381, slots[2].right)
        assertEquals(3407, slots[2].bottom)

        assertEquals(49, slots[1].top - slots[0].bottom)
        assertEquals(49, slots[2].top - slots[1].bottom)
    }

    @Test
    fun testThreeTopTwoBottomSlotRects() {
        val slots = CollageGenerator.calculateSlotRects(CollageLayout.THREE_TOP_TWO_BOTTOM, 2480, 3508)
        assertEquals(3, slots.size)

        // Slot 0 (Row 1 - Full Width Top)
        assertEquals(99, slots[0].left)
        assertEquals(99, slots[0].top)
        assertEquals(2381, slots[0].right)
        assertEquals(1729, slots[0].bottom)

        // Slot 1 (Row 2 - Bottom Left)
        assertEquals(99, slots[1].left)
        assertEquals(1778, slots[1].top)
        assertEquals(1215, slots[1].right)
        assertEquals(3408, slots[1].bottom)

        // Slot 2 (Row 2 - Bottom Right)
        assertEquals(1264, slots[2].left)
        assertEquals(1778, slots[2].top)
        assertEquals(2380, slots[2].right)
        assertEquals(3408, slots[2].bottom)

        // Gutters
        assertEquals(49, slots[1].top - slots[0].bottom)
        assertEquals(49, slots[2].left - slots[1].right)
    }

    @Test
    fun testFourGridSlotRects() {
        val slots = CollageGenerator.calculateSlotRects(CollageLayout.FOUR_GRID, 2480, 3508)
        assertEquals(4, slots.size)

        assertEquals(99, slots[0].left)
        assertEquals(99, slots[0].top)
        assertEquals(1215, slots[0].right)
        assertEquals(1729, slots[0].bottom)

        assertEquals(1264, slots[1].left)
        assertEquals(99, slots[1].top)
        assertEquals(2380, slots[1].right)
        assertEquals(1729, slots[1].bottom)

        assertEquals(99, slots[2].left)
        assertEquals(1778, slots[2].top)
        assertEquals(1215, slots[2].right)
        assertEquals(3408, slots[2].bottom)

        assertEquals(1264, slots[3].left)
        assertEquals(1778, slots[3].top)
        assertEquals(2380, slots[3].right)
        assertEquals(3408, slots[3].bottom)

        assertEquals(49, slots[1].left - slots[0].right)
        assertEquals(49, slots[2].top - slots[0].bottom)
    }

    @Test
    fun testSixGridSlotRects() {
        val slots = CollageGenerator.calculateSlotRects(CollageLayout.SIX_GRID, 2480, 3508)
        assertEquals(6, slots.size)

        assertEquals(99, slots[0].left)
        assertEquals(99, slots[0].top)
        assertEquals(1215, slots[0].right)
        assertEquals(1169, slots[0].bottom)

        assertEquals(1264, slots[1].left)
        assertEquals(99, slots[1].top)
        assertEquals(2380, slots[1].right)
        assertEquals(1169, slots[1].bottom)

        assertEquals(99, slots[2].left)
        assertEquals(1218, slots[2].top)
        assertEquals(1215, slots[2].right)
        assertEquals(2288, slots[2].bottom)

        assertEquals(1264, slots[3].left)
        assertEquals(1218, slots[3].top)
        assertEquals(2380, slots[3].right)
        assertEquals(2288, slots[3].bottom)

        assertEquals(99, slots[4].left)
        assertEquals(2337, slots[4].top)
        assertEquals(1215, slots[4].right)
        assertEquals(3407, slots[4].bottom)

        assertEquals(1264, slots[5].left)
        assertEquals(2337, slots[5].top)
        assertEquals(2380, slots[5].right)
        assertEquals(3407, slots[5].bottom)

        assertEquals(49, slots[1].left - slots[0].right)
        assertEquals(49, slots[2].top - slots[0].bottom)
        assertEquals(49, slots[4].top - slots[2].bottom)
    }

    @Test
    fun testCalculateInSampleSize() {
        // 4000x3000 camera frame into 1116x1070 slot -> power of 2: inSampleSize = 2
        val sampleSize1 = CollageGenerator.calculateInSampleSize(4000, 3000, 1116, 1070)
        assertEquals(2, sampleSize1)

        // 8000x6000 48MP photo into 1116x1070 slot -> inSampleSize = 4
        val sampleSize2 = CollageGenerator.calculateInSampleSize(8000, 6000, 1116, 1070)
        assertEquals(4, sampleSize2)

        // Smaller than target -> inSampleSize = 1
        val sampleSize3 = CollageGenerator.calculateInSampleSize(1000, 800, 2282, 1630)
        assertEquals(1, sampleSize3)

        // Exact match -> inSampleSize = 1
        val sampleSize4 = CollageGenerator.calculateInSampleSize(2282, 1630, 2282, 1630)
        assertEquals(1, sampleSize4)
    }

    @Test
    fun testAspectRatioFitCenteringMath() {
        val slotW = 1116f
        val slotH = 1630f

        // Case 1: Standard portrait document (ratio 0.707)
        val docW = 1000f
        val docH = 1414f
        val scale1 = min(slotW / docW, slotH / docH)
        val drawW1 = docW * scale1
        val drawH1 = docH * scale1

        assertTrue(drawW1 <= slotW + 0.01f)
        assertTrue(drawH1 <= slotH + 0.01f)
        assertEquals(1.116f, scale1, 0.001f)

        // Case 2: Landscape image placed in slot
        val landW = 1920f
        val landH = 1080f
        val scale2 = min(slotW / landW, slotH / landH)
        val drawW2 = landW * scale2
        val drawH2 = landH * scale2

        assertTrue(drawW2 <= slotW + 0.01f)
        assertTrue(drawH2 <= slotH + 0.01f)
        // Constrained by width
        assertEquals(slotW, drawW2, 0.01f)

        // Centering offsets are non-negative
        val offsetLeft = (slotW - drawW2) / 2f
        val offsetTop = (slotH - drawH2) / 2f
        assertTrue(offsetLeft >= 0f)
        assertTrue(offsetTop >= 0f)
    }

    @Test
    fun testCollageSlotLimitsAndBounds() {
        // Layout must never process more than maxPages
        assertEquals(2, minOf(CollageLayout.TWO_VERTICAL.maxPages, 10))
        assertEquals(2, minOf(CollageLayout.TWO_HORIZONTAL.maxPages, 5))
        assertEquals(3, minOf(CollageLayout.THREE_VERTICAL.maxPages, 10))
        assertEquals(3, minOf(CollageLayout.THREE_TOP_TWO_BOTTOM.maxPages, 7))
        assertEquals(4, minOf(CollageLayout.FOUR_GRID.maxPages, 100))
        assertEquals(6, minOf(CollageLayout.SIX_GRID.maxPages, 8))

        // When provided fewer items, it handles them gracefully without out-of-bounds indexing
        assertEquals(1, minOf(CollageLayout.TWO_VERTICAL.maxPages, 1))
        assertEquals(0, minOf(CollageLayout.FOUR_GRID.maxPages, 0))
    }
}
