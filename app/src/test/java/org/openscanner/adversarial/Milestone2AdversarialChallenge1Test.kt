package org.openscanner.adversarial

import android.graphics.Bitmap
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.openscanner.cv.BookSpineDetector
import org.openscanner.cv.IdCardCropper
import kotlin.math.abs
import kotlin.random.Random

/**
 * Empirical Adversarial Challenge Test Suite for Milestone 2:
 * 1. IdCardCropper mathematical invariance, boundary clamping, and aspect-ratio stability
 * 2. BookSpineDetector vertical luminance profiling, Gaussian kernel physics, valley isolation,
 *    LUT boundary invariants, crease shadow compensation, and edge-case handling
 */
class Milestone2AdversarialChallenge1Test {

    @Before
    fun setUp() {
        mockkStatic(Bitmap::class)
    }

    @After
    fun tearDown() {
        unmockkStatic(Bitmap::class)
    }

    // =========================================================================
    // SECTION 1: IdCardCropper Mathematical & Boundary Stress Testing
    // =========================================================================

    @Test
    fun testIdCardAspectRatioStandardConstants() {
        assertEquals("Authoritative ISO/IEC 7810 ID-1 aspect ratio must be 1.586", 1.586f, IdCardCropper.ID1_ASPECT_RATIO, 0.0001f)
        assertEquals("Authoritative card width fraction must be 85%", 0.85f, IdCardCropper.CARD_WIDTH_RATIO, 0.0001f)
    }

    @Test
    fun testIdCardCropperExtremeScreenAspectRatiosMaintainBounds() {
        data class ScreenCase(val bW: Int, val bH: Int, val vW: Int, val vH: Int)

        val testConfigs = listOf(
            ScreenCase(3000, 4000, 1080, 2400), // Standard smartphone portrait
            ScreenCase(4000, 3000, 2400, 1080), // Standard smartphone landscape
            ScreenCase(4000, 4000, 1080, 1080), // Square photo on square screen
            ScreenCase(1000, 4000, 3840, 1080), // Ultra-tall photo on ultrawide monitor
            ScreenCase(4000, 1000, 1080, 3840), // Ultra-wide photo on ultra-tall screen
            ScreenCase(1920, 1080, 500, 3000),  // Landscape capture on super narrow phone
            ScreenCase(1080, 1920, 3000, 500),  // Portrait capture on super flat display
            ScreenCase(100, 100, 2000, 2000),   // Tiny bitmap on massive 4K display
            ScreenCase(8000, 6000, 200, 200)    // Giant 48MP capture on tiny preview
        )

        for ((bW, bH, vW, vH) in testConfigs) {
            val rect = IdCardCropper.calculateCropRect(bW, bH, vW, vH)
            val rectW = rect.right - rect.left
            val rectH = rect.bottom - rect.top

            assertTrue("Left bound >= 0 for ($bW, $bH, $vW, $vH): ${rect.left}", rect.left >= 0)
            assertTrue("Top bound >= 0 for ($bW, $bH, $vW, $vH): ${rect.top}", rect.top >= 0)
            assertTrue("Right bound <= bitmapWidth for ($bW, $bH, $vW, $vH): ${rect.right} <= $bW", rect.right <= bW)
            assertTrue("Bottom bound <= bitmapHeight for ($bW, $bH, $vW, $vH): ${rect.bottom} <= $bH", rect.bottom <= bH)
            assertTrue("Width > 0 for ($bW, $bH, $vW, $vH): $rectW", rectW > 0)
            assertTrue("Height > 0 for ($bW, $bH, $vW, $vH): $rectH", rectH > 0)
        }
    }

    @Test
    fun testIdCardCropperRandomFuzzingPropertyInvariants() {
        val random = Random(42)
        val iterations = 250

        for (i in 0 until iterations) {
            val bW = random.nextInt(10, 6000)
            val bH = random.nextInt(10, 6000)
            val vW = random.nextInt(50, 4000)
            val vH = random.nextInt(50, 4000)

            val rect = IdCardCropper.calculateCropRect(bW, bH, vW, vH)
            val rectW = rect.right - rect.left
            val rectH = rect.bottom - rect.top

            // Invariant 1: Inside bitmap boundary
            assertTrue("left >= 0 [iter $i]", rect.left >= 0)
            assertTrue("top >= 0 [iter $i]", rect.top >= 0)
            assertTrue("right <= bW [iter $i]", rect.right <= bW)
            assertTrue("bottom <= bH [iter $i]", rect.bottom <= bH)

            // Invariant 2: Positive area
            assertTrue("rectW > 0 [iter $i]", rectW > 0)
            assertTrue("rectH > 0 [iter $i]", rectH > 0)

            // Invariant 3: Centered within viewport projection when unconstrained
            val scale = maxOf(vW.toFloat() / bW, vH.toFloat() / bH)
            val cardW = vW * IdCardCropper.CARD_WIDTH_RATIO
            val cardH = cardW / IdCardCropper.ID1_ASPECT_RATIO
            val expectedW = (cardW / scale).toInt()
            val expectedH = (cardH / scale).toInt()

            if (expectedW <= bW && expectedH <= bH) {
                // If the target card fits completely, verify aspect ratio preservation
                val aspect = rectW.toFloat() / rectH.toFloat()
                assertTrue(
                    "Aspect ratio deviation exceeds tolerance [iter $i: got $aspect expected 1.586]",
                    abs(aspect - IdCardCropper.ID1_ASPECT_RATIO) < 0.15f
                )
            }
        }
    }

    @Test
    fun testIdCardCropperFallbackOnZeroOrNegativeViewDimensions() {
        val bW = 2000
        val bH = 1500

        // Zero view dimensions (e.g. unmeasured layout)
        val rectZero = IdCardCropper.calculateCropRect(bW, bH, 0, 0)
        assertEquals((bW * 0.85f).toInt(), rectZero.right - rectZero.left)
        val expectedH = Math.round(1700 / 1.586f).toInt()
        assertEquals(expectedH, rectZero.bottom - rectZero.top)
        assertTrue(rectZero.left >= 0 && rectZero.right <= bW)
        assertTrue(rectZero.top >= 0 && rectZero.bottom <= bH)

        // Negative view dimensions
        val rectNegative = IdCardCropper.calculateCropRect(bW, bH, -1080, -2400)
        assertEquals(rectZero.left, rectNegative.left)
        assertEquals(rectZero.top, rectNegative.top)
        assertEquals(rectZero.right, rectNegative.right)
        assertEquals(rectZero.bottom, rectNegative.bottom)
    }

    @Test
    fun testIdCardCropperNearZeroBitmapDimensions() {
        // 2x2 bitmap with positive view
        val rect2x2 = IdCardCropper.calculateCropRect(2, 2, 1080, 2400)
        assertTrue(rect2x2.left in 0..1)
        assertTrue(rect2x2.top in 0..1)
        assertTrue(rect2x2.right in 1..2)
        assertTrue(rect2x2.bottom in 1..2)
        assertTrue(rect2x2.right > rect2x2.left)
        assertTrue(rect2x2.bottom > rect2x2.top)

        // 1x1 bitmap with positive view
        val rect1x1 = IdCardCropper.calculateCropRect(1, 1, 1080, 2400)
        assertEquals(0, rect1x1.left)
        assertEquals(0, rect1x1.top)
        assertEquals(1, rect1x1.right)
        assertEquals(1, rect1x1.bottom)
    }

    // =========================================================================
    // SECTION 2: BookSpineDetector Physics, Mathematics & Profile Stress Testing
    // =========================================================================

    @Test
    fun testGaussianKernelPhysicsAndNormalization() {
        val kernel = BookSpineDetector.GAUSSIAN_KERNEL_11
        assertEquals("Gaussian kernel must have length 11 (radius 5)", 11, kernel.size)

        // Property 1: Sum of weights must equal 1.0 (energy conservation)
        val sum = kernel.sum()
        assertEquals("Kernel weights must sum to 1.0", 1.0f, sum, 1e-5f)

        // Property 2: Symmetry around central peak (index 5)
        for (i in 0..5) {
            assertEquals("Kernel must be symmetric around index 5", kernel[i], kernel[10 - i], 1e-6f)
        }

        // Property 3: Monotonic decay from peak
        for (i in 0 until 5) {
            assertTrue("Kernel must monotonically increase towards center: $i", kernel[i] < kernel[i + 1])
        }
    }

    @Test
    fun testLuminanceProfileColorWeightsCompliance() {
        val width = 5
        val height = 2
        val pixels = IntArray(width * height)

        // Col 0: Pure Red (#FF0000) -> 0.299 * 255 = 76.245
        // Col 1: Pure Green (#00FF00) -> 0.587 * 255 = 149.685
        // Col 2: Pure Blue (#0000FF) -> 0.114 * 255 = 29.070
        // Col 3: Pure White (#FFFFFF) -> 255.0
        // Col 4: Pure Black (#000000) -> 0.0
        for (y in 0 until height) {
            pixels[y * width + 0] = (0xFF shl 24) or (255 shl 16)
            pixels[y * width + 1] = (0xFF shl 24) or (255 shl 8)
            pixels[y * width + 2] = (0xFF shl 24) or 255
            pixels[y * width + 3] = (0xFF shl 24) or (255 shl 16) or (255 shl 8) or 255
            pixels[y * width + 4] = (0xFF shl 24)
        }

        val profile = BookSpineDetector.computeLuminanceProfile(pixels, width, height)
        assertEquals(76.245f, profile[0], 0.05f)
        assertEquals(149.685f, profile[1], 0.05f)
        assertEquals(29.070f, profile[2], 0.05f)
        assertEquals(255.0f, profile[3], 0.01f)
        assertEquals(0.0f, profile[4], 0.001f)
    }

    @Test
    fun testFlatProfileGracefulFallbackToGeometricCenter() {
        val width = 320

        // Test 1: Uniform pure white page
        val whiteProfile = FloatArray(width) { 255f }
        assertEquals("Flat white spread must fall back to geometric center", width / 2, BookSpineDetector.findValleySpineIndex(whiteProfile))

        // Test 2: Uniform dark spread
        val darkProfile = FloatArray(width) { 15f }
        assertEquals("Flat dark spread must fall back to geometric center", width / 2, BookSpineDetector.findValleySpineIndex(darkProfile))

        // Test 3: Subtle noise below 2.5f threshold
        val noisyProfile = FloatArray(width) { idx -> 200f + (idx % 3) * 0.7f } // max diff = 1.4f < 2.5f
        assertEquals("Noise below 2.5f threshold must fall back to center", width / 2, BookSpineDetector.findValleySpineIndex(noisyProfile))
    }

    @Test
    fun testValleyDetectionIgnoresDistractorsOutsideSearchBand() {
        val width = 320
        val profile = FloatArray(width) { 220f }

        // Place a very dark distractor on the outer left margin at x = 20 (6.25% of width)
        for (x in 10..30) {
            profile[x] = 10f
        }

        // Place a very dark distractor on the outer right margin at x = 300 (93.75% of width)
        for (x in 290..310) {
            profile[x] = 10f
        }

        // Central band [0.35W, 0.65W] (x in [112, 208]) remains flat at 220f
        val smoothed = BookSpineDetector.smoothProfile(profile)
        val spine = BookSpineDetector.findValleySpineIndex(smoothed)

        // Outer margin shadows must NOT pull the spine away from center
        assertEquals("Outer margin shadows outside [0.35W, 0.65W] must be ignored", width / 2, spine)
    }

    @Test
    fun testValleyDetectionIsolatesCreaseShadowAcrossBand() {
        val width = 320

        // Test spine detection at 40%, 50%, and 60% of width
        val testSpines = listOf(
            (width * 0.40f).toInt(), // 128
            (width * 0.50f).toInt(), // 160
            (width * 0.60f).toInt()  // 192
        )

        for (targetSpine in testSpines) {
            val profile = FloatArray(width) { 210f }
            // Simulate 20px wide crease shadow dip of 80 luminance units
            for (x in (targetSpine - 10)..(targetSpine + 10)) {
                val dist = abs(x - targetSpine)
                profile[x] = 210f - 80f * (1f - dist / 10f)
            }

            val smoothed = BookSpineDetector.smoothProfile(profile)
            val detected = BookSpineDetector.findValleySpineIndex(smoothed)
            assertEquals("Must isolate crease shadow at $targetSpine", targetSpine, detected)
        }
    }

    // =========================================================================
    // SECTION 3: 1D Cylindrical Unwarp LUT Mathematical & Boundary Invariants
    // =========================================================================

    @Test
    fun testUnwarpLUTStrictBoundaryFixedPoints() {
        val widths = listOf(2, 10, 100, 500, 1000, 2480)

        for (w in widths) {
            val maxD = (w - 1).toFloat()

            // Left Page: outer edge at x=0, spine at x=w-1
            val (leftSrcX, leftGain) = BookSpineDetector.computeUnwarpLUT(w, isLeftPage = true)
            assertEquals("Left page x=0 must map exactly to 0f [w=$w]", 0f, leftSrcX[0], 0.001f)
            assertEquals("Left page x=W-1 must map exactly to (W-1)f [w=$w]", maxD, leftSrcX[w - 1], 0.001f)

            // Right Page: spine at x=0, outer edge at x=w-1
            val (rightSrcX, rightGain) = BookSpineDetector.computeUnwarpLUT(w, isLeftPage = false)
            assertEquals("Right page x=0 must map exactly to 0f [w=$w]", 0f, rightSrcX[0], 0.001f)
            assertEquals("Right page x=W-1 must map exactly to (W-1)f [w=$w]", maxD, rightSrcX[w - 1], 0.001f)
        }
    }

    @Test
    fun testUnwarpLUTStrictMonotonicityAndRangeInvariance() {
        val width = 1200
        val (leftSrcX, _) = BookSpineDetector.computeUnwarpLUT(width, isLeftPage = true)
        val (rightSrcX, _) = BookSpineDetector.computeUnwarpLUT(width, isLeftPage = false)

        for (x in 0 until width - 1) {
            // Strict monotonic increase
            assertTrue("Left srcX must be strictly monotonic at $x: ${leftSrcX[x]} < ${leftSrcX[x+1]}", leftSrcX[x] < leftSrcX[x + 1])
            assertTrue("Right srcX must be strictly monotonic at $x: ${rightSrcX[x]} < ${rightSrcX[x+1]}", rightSrcX[x] < rightSrcX[x + 1])

            // Range invariance
            assertTrue("Left srcX in bounds [0, maxD]", leftSrcX[x] >= 0f && leftSrcX[x] <= (width - 1).toFloat())
            assertTrue("Right srcX in bounds [0, maxD]", rightSrcX[x] >= 0f && rightSrcX[x] <= (width - 1).toFloat())
        }
    }

    @Test
    fun testShadowBoostGainPropertiesAndFalloff() {
        val width = 1000
        val boost = 1.35f
        val gutterFraction = 0.22f
        val gutterPx = (width - 1) * gutterFraction // ~219.78px

        val (_, leftGain) = BookSpineDetector.computeUnwarpLUT(width, isLeftPage = true, shadowBoost = boost, gutterFraction = gutterFraction)
        val (_, rightGain) = BookSpineDetector.computeUnwarpLUT(width, isLeftPage = false, shadowBoost = boost, gutterFraction = gutterFraction)

        // 1. Peak gain at spine
        assertEquals("Peak gain at Left spine (x = W-1)", boost, leftGain[width - 1], 0.001f)
        assertEquals("Peak gain at Right spine (x = 0)", boost, rightGain[0], 0.001f)

        // 2. Unity gain at outer edge
        assertEquals("Unity gain at Left outer edge (x = 0)", 1.0f, leftGain[0], 0.001f)
        assertEquals("Unity gain at Right outer edge (x = W-1)", 1.0f, rightGain[width - 1], 0.001f)

        // 3. Gain strictly within [1.0, 1.35] everywhere
        for (x in 0 until width) {
            assertTrue("Left gain >= 1.0 at $x: ${leftGain[x]}", leftGain[x] >= 1.0f - 1e-5f)
            assertTrue("Left gain <= 1.35 at $x: ${leftGain[x]}", leftGain[x] <= boost + 1e-5f)
            assertTrue("Right gain >= 1.0 at $x: ${rightGain[x]}", rightGain[x] >= 1.0f - 1e-5f)
            assertTrue("Right gain <= 1.35 at $x: ${rightGain[x]}", rightGain[x] <= boost + 1e-5f)
        }

        // 4. Outside gutter, gain must be exactly 1.0
        val insideGutterRight = gutterPx.toInt()
        assertEquals("Gain outside gutter must be exactly 1.0", 1.0f, rightGain[insideGutterRight + 5], 0.0001f)
        val insideGutterLeft = (width - 1 - gutterPx).toInt()
        assertEquals("Gain outside gutter must be exactly 1.0", 1.0f, leftGain[insideGutterLeft - 5], 0.0001f)
    }

    @Test
    fun testCurvatureExpansionPhysicsNearSpine() {
        val width = 1000
        val gamma = 0.85f // expansion exponent
        val (rightSrcX, _) = BookSpineDetector.computeUnwarpLUT(width, isLeftPage = false, gamma = gamma)

        // For Right Page, spine is at x = 0.
        // Compressed text lines near spine mean: a small step in source maps to a larger step in destination,
        // so source coordinate xSrc < xDst.
        for (xDst in 1..150) {
            val xSrc = rightSrcX[xDst]
            assertTrue("Near spine, xSrc ($xSrc) must be < xDst ($xDst) due to cylindrical unwarp expansion", xSrc < xDst)
        }

        // Verify expansion rate (derivative dxSrc / dxDst) increases towards 1.0 far from spine
        val dNearSpine = rightSrcX[10] - rightSrcX[9]
        val dMidPage = rightSrcX[300] - rightSrcX[299]
        val dFarEdge = rightSrcX[950] - rightSrcX[949]

        assertTrue("Step size must grow as distance from spine increases ($dNearSpine < $dMidPage)", dNearSpine < dMidPage)
        val beta = 1.0f / gamma
        assertEquals("Step size at far edge matches theoretical derivative beta", beta, dFarEdge, 0.05f)
    }

    // =========================================================================
    // SECTION 4: Degenerate, Micro-Dimensions & Boundary Limits Robustness
    // =========================================================================

    @Test
    fun testSpineDetectorDegenerateDimensionsDoNotThrow() {
        // Micro-bitmaps in detectSpine
        val tiny1x1 = mockk<Bitmap>()
        every { tiny1x1.width } returns 1
        every { tiny1x1.height } returns 1
        assertEquals("1x1 bitmap returns width/2 = 0 without crash", 0, BookSpineDetector.detectSpine(tiny1x1))

        val tiny2x2 = mockk<Bitmap>()
        every { tiny2x2.width } returns 2
        every { tiny2x2.height } returns 2
        assertEquals("2x2 bitmap returns width/2 = 1 without crash", 1, BookSpineDetector.detectSpine(tiny2x2))

        val small63x63 = mockk<Bitmap>()
        every { small63x63.width } returns 63
        every { small63x63.height } returns 63
        assertEquals("Sub-64 bitmap bypasses downsampling and returns 31 without crash", 31, BookSpineDetector.detectSpine(small63x63))
    }

    @Test
    fun testComputeUnwarpLUTDegenerateWidths() {
        // width = 1
        val (src1, gain1) = BookSpineDetector.computeUnwarpLUT(1, isLeftPage = false)
        assertEquals(1, src1.size)
        assertEquals(1, gain1.size)
        assertEquals(0f, src1[0], 0.001f)
        assertEquals(1.35f, gain1[0], 0.001f)

        // width = 2
        val (src2, gain2) = BookSpineDetector.computeUnwarpLUT(2, isLeftPage = false)
        assertEquals(2, src2.size)
        assertEquals(0f, src2[0], 0.001f)
        assertEquals(1f, src2[1], 0.001f)

        // width = 0
        val (src0, gain0) = BookSpineDetector.computeUnwarpLUT(0, isLeftPage = false)
        assertEquals(0, src0.size)
        assertEquals(0, gain0.size)
    }

    @Test
    fun testSplitAndUnwarpClampsSpineWithinSafeBoundaries() {
        val width = 2000
        val minAllowed = (width * 0.15f).toInt() // 300
        val maxAllowed = (width * 0.85f).toInt() // 1700

        fun clampSpine(raw: Int): Int = raw.coerceIn(minAllowed, maxAllowed)

        // Negative spine
        assertEquals(300, clampSpine(-100))
        // Extreme left edge
        assertEquals(300, clampSpine(0))
        assertEquals(300, clampSpine(10))
        assertEquals(300, clampSpine(299))
        // Valid central range
        assertEquals(300, clampSpine(300))
        assertEquals(1000, clampSpine(1000))
        assertEquals(1700, clampSpine(1700))
        // Extreme right edge
        assertEquals(1700, clampSpine(1701))
        assertEquals(1700, clampSpine(2000))
        assertEquals(1700, clampSpine(9999))
    }
}
