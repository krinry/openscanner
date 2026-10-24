package org.openscanner.cv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Tests for BookSpineDetector algorithmic math, vertical luminance projection,
 * Gaussian smoothing, valley profiling, and 1D cylindrical transformation LUTs.
 */
class BookSpineDetectorTest {

    @Test
    fun testLuminanceProfileCalculationFormula() {
        val width = 10
        val height = 4
        val pixels = IntArray(width * height)

        // Column 2 pure white: (255, 255, 255) -> 255.0f
        for (y in 0 until height) {
            pixels[y * width + 2] = (0xFF shl 24) or (255 shl 16) or (255 shl 8) or 255
        }
        // Column 5 pure red: (255, 0, 0) -> 0.299 * 255 = 76.245f
        for (y in 0 until height) {
            pixels[y * width + 5] = (0xFF shl 24) or (255 shl 16)
        }

        val profile = BookSpineDetector.computeLuminanceProfile(pixels, width, height)
        assertEquals(255f, profile[2], 0.01f)
        assertEquals(76.245f, profile[5], 0.05f)
        assertEquals(0f, profile[0], 0.001f)
    }

    @Test
    fun testGaussianSmoothingWeightsNormalized() {
        val uniformProfile = FloatArray(100) { 150f }
        val smoothed = BookSpineDetector.smoothProfile(uniformProfile)
        for (i in 10 until 90) {
            assertEquals(150f, smoothed[i], 0.01f)
        }
    }

    @Test
    fun testSpineValleyDetectionIdentifiesCreaseShadow() {
        val width = 320
        val profile = FloatArray(width) { 220f } // Bright white pages

        // Simulate dark crease shadow valley centered at x = 152 (47.5% spread)
        val valleyCenter = 152
        for (x in 130..174) {
            val dist = abs(x - valleyCenter)
            profile[x] = 220f - 140f * (1f - dist / 22f).coerceAtLeast(0f)
        }

        val smoothed = BookSpineDetector.smoothProfile(profile)
        val detectedValley = BookSpineDetector.findValleySpineIndex(smoothed)

        assertEquals(valleyCenter, detectedValley)
    }

    @Test
    fun testSpineValleyFallbackWhenProfileIsFlat() {
        val width = 320
        val flatProfile = FloatArray(width) { 200f }
        val detected = BookSpineDetector.findValleySpineIndex(flatProfile)
        assertEquals(160, detected) // Center fallback
    }

    @Test
    fun testUnwarpLUTLeftAndRightBoundaries() {
        val width = 1000

        // Left Page: Spine at right (x = 999), Outer edge at left (x = 0)
        val (leftSrcX, leftGain) = BookSpineDetector.computeUnwarpLUT(width, isLeftPage = true)
        assertEquals(0f, leftSrcX[0], 0.01f) // Outer edge invariant
        assertEquals(999f, leftSrcX[999], 0.01f) // Spine invariant
        assertTrue(leftGain[999] > 1.30f) // Crease shadow boost at spine
        assertEquals(1.0f, leftGain[0], 0.001f) // No boost at outer edge

        // Right Page: Spine at left (x = 0), Outer edge at right (x = 999)
        val (rightSrcX, rightGain) = BookSpineDetector.computeUnwarpLUT(width, isLeftPage = false)
        assertEquals(0f, rightSrcX[0], 0.01f) // Spine invariant
        assertEquals(999f, rightSrcX[999], 0.01f) // Outer edge invariant
        assertTrue(rightGain[0] > 1.30f) // Crease shadow boost at spine
        assertEquals(1.0f, rightGain[999], 0.001f) // No boost at outer edge
    }

    @Test
    fun testUnwarpCurvatureExpansionNearSpine() {
        val width = 1000
        val (rightSrcX, _) = BookSpineDetector.computeUnwarpLUT(width, isLeftPage = false, gamma = 0.85f)

        // Near spine at x = 100 (10% of width):
        // In destination (unwarped), 100px from spine is mapped from compressed source:
        // x_src = 1000 * (100 / 1000)^(1 / 0.85) = 1000 * 0.1^1.17647 ~= 66.6px
        val srcXAt100 = rightSrcX[100]
        assertTrue(srcXAt100 < 100f) // Source coordinate is closer to spine
        assertEquals(66.6f, srcXAt100, 2.0f) // Verified expansion ratio ~= 1.5x
    }
}
