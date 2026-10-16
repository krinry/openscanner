package org.openscanner.cv

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max

object EdgeDetector {

    /**
     * Fast, robust document boundary detection on an Android Bitmap.
     * Downscales the image for <10ms computation, computes Sobel gradient tensors,
     * and extracts the 4 extremal corner points bounding the document.
     */
    fun detectInBitmap(bitmap: Bitmap): DocumentQuadrilateral {
        val width = bitmap.width
        val height = bitmap.height
        val fallback = DocumentQuadrilateral.defaultForDimensions(width.toFloat(), height.toFloat())
        if (width < 64 || height < 64) return fallback

        // Scale down bitmap to max 480px width for instant analysis
        val scale = 480f / max(width, height)
        val sw = (width * scale).toInt().coerceAtLeast(64)
        val sh = (height * scale).toInt().coerceAtLeast(64)

        val scaledBmp = Bitmap.createScaledBitmap(bitmap, sw, sh, true)
        val pixels = IntArray(sw * sh)
        scaledBmp.getPixels(pixels, 0, sw, 0, 0, sw, sh)
        if (scaledBmp != bitmap) {
            scaledBmp.recycle()
        }

        // Compute grayscale array
        val gray = IntArray(sw * sh)
        for (i in 0 until sw * sh) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            gray[i] = (r * 77 + g * 150 + b * 29) shr 8
        }

        // Sobel edge gradient computation
        val grads = IntArray(sw * sh)
        var maxGrad = 0
        for (y in 1 until sh - 1) {
            val yOffset = y * sw
            val prevYOffset = (y - 1) * sw
            val nextYOffset = (y + 1) * sw

            for (x in 1 until sw - 1) {
                val p00 = gray[prevYOffset + x - 1]
                val p02 = gray[prevYOffset + x + 1]
                val p10 = gray[yOffset + x - 1]
                val p12 = gray[yOffset + x + 1]
                val p20 = gray[nextYOffset + x - 1]
                val p22 = gray[nextYOffset + x + 1]

                val gx = (p02 + 2 * p12 + p22) - (p00 + 2 * p10 + p20)
                val gy = (p20 + 2 * gray[nextYOffset + x] + p22) - (p00 + 2 * gray[prevYOffset + x] + p02)
                val mag = abs(gx) + abs(gy)
                grads[yOffset + x] = mag
                if (mag > maxGrad) maxGrad = mag
            }
        }

        val threshold = maxGrad * 24 / 100
        val edgePoints = mutableListOf<PointF>()
        for (y in 4 until sh - 4) {
            val yOffset = y * sw
            for (x in 4 until sw - 4) {
                if (grads[yOffset + x] > threshold) {
                    edgePoints.add(PointF(x / scale, y / scale))
                }
            }
        }

        if (edgePoints.size < 4) return fallback

        var minSum = Float.MAX_VALUE
        var maxSum = -Float.MAX_VALUE
        var minDiff = Float.MAX_VALUE
        var maxDiff = -Float.MAX_VALUE

        var tl = edgePoints[0]
        var tr = edgePoints[0]
        var br = edgePoints[0]
        var bl = edgePoints[0]

        for (pt in edgePoints) {
            val sum = pt.x + pt.y
            val diff = pt.x - pt.y

            if (sum < minSum) { minSum = sum; tl = pt }
            if (sum > maxSum) { maxSum = sum; br = pt }
            if (diff > maxDiff) { maxDiff = diff; tr = pt }
            if (diff < minDiff) { minDiff = diff; bl = pt }
        }

        val areaW = max(tr.x - tl.x, br.x - bl.x)
        val areaH = max(bl.y - tl.y, br.y - tr.y)
        if (areaW < width * 0.22f || areaH < height * 0.22f) {
            return fallback
        }

        // Verify convexity to prevent degenerate bow-tie shapes
        if (!isConvex(tl, tr, br, bl)) {
            return fallback
        }

        return DocumentQuadrilateral(tl, tr, br, bl)
    }

    private fun isConvex(tl: PointF, tr: PointF, br: PointF, bl: PointF): Boolean {
        fun crossProduct(p0: PointF, p1: PointF, p2: PointF): Float {
            val dx1 = p1.x - p0.x
            val dy1 = p1.y - p0.y
            val dx2 = p2.x - p1.x
            val dy2 = p2.y - p1.y
            return dx1 * dy2 - dy1 * dx2
        }
        val cp1 = crossProduct(tl, tr, br)
        val cp2 = crossProduct(tr, br, bl)
        val cp3 = crossProduct(br, bl, tl)
        val cp4 = crossProduct(bl, tl, tr)
        return (cp1 > 0 && cp2 > 0 && cp3 > 0 && cp4 > 0) || (cp1 < 0 && cp2 < 0 && cp3 < 0 && cp4 < 0)
    }

    fun detectInYuv(yuvData: ByteArray, width: Int, height: Int): DocumentQuadrilateral {
        val fallback = DocumentQuadrilateral.defaultForDimensions(width.toFloat(), height.toFloat())
        if (width < 64 || height < 64 || yuvData.isEmpty()) {
            return fallback
        }

        // Subsample for fast real-time 60fps processing
        val step = 4
        val sw = width / step
        val sh = height / step

        val edgePoints = mutableListOf<PointF>()
        var maxGrad = 0
        val grads = IntArray(sw * sh)

        for (y in 1 until sh - 1) {
            val yOffset = y * step * width
            val prevYOffset = (y - 1) * step * width
            val nextYOffset = (y + 1) * step * width

            for (x in 1 until sw - 1) {
                val xIndex = x * step
                val p00 = yuvData[prevYOffset + (xIndex - step)].toInt() and 0xFF
                val p02 = yuvData[prevYOffset + (xIndex + step)].toInt() and 0xFF
                val p10 = yuvData[yOffset + (xIndex - step)].toInt() and 0xFF
                val p12 = yuvData[yOffset + (xIndex + step)].toInt() and 0xFF
                val p20 = yuvData[nextYOffset + (xIndex - step)].toInt() and 0xFF
                val p22 = yuvData[nextYOffset + (xIndex + step)].toInt() and 0xFF

                val gx = (p02 + 2 * p12 + p22) - (p00 + 2 * p10 + p20)
                val gy = (p20 + 2 * (yuvData[nextYOffset + xIndex].toInt() and 0xFF) + p22) -
                         (p00 + 2 * (yuvData[prevYOffset + xIndex].toInt() and 0xFF) + p02)

                val mag = abs(gx) + abs(gy)
                grads[y * sw + x] = mag
                if (mag > maxGrad) maxGrad = mag
            }
        }

        val threshold = maxGrad * 28 / 100
        for (y in 5 until sh - 5) {
            for (x in 5 until sw - 5) {
                if (grads[y * sw + x] > threshold) {
                    edgePoints.add(PointF(x.toFloat() * step, y.toFloat() * step))
                }
            }
        }

        if (edgePoints.size < 4) return fallback

        var minSum = Float.MAX_VALUE
        var maxSum = -Float.MAX_VALUE
        var minDiff = Float.MAX_VALUE
        var maxDiff = -Float.MAX_VALUE

        var tl = edgePoints[0]
        var tr = edgePoints[0]
        var br = edgePoints[0]
        var bl = edgePoints[0]

        for (pt in edgePoints) {
            val sum = pt.x + pt.y
            val diff = pt.x - pt.y

            if (sum < minSum) { minSum = sum; tl = pt }
            if (sum > maxSum) { maxSum = sum; br = pt }
            if (diff > maxDiff) { maxDiff = diff; tr = pt }
            if (diff < minDiff) { minDiff = diff; bl = pt }
        }

        val areaW = max(tr.x - tl.x, br.x - bl.x)
        val areaH = max(bl.y - tl.y, br.y - tr.y)
        if (areaW < width * 0.25f || areaH < height * 0.25f) {
            return fallback
        }

        return DocumentQuadrilateral(tl, tr, br, bl)
    }
}
