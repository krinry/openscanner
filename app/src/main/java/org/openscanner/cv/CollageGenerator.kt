package org.openscanner.cv

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF

/**
 * Supported collage page layouts.
 */
enum class CollageLayout(val title: String, val maxPages: Int) {
    TWO_VERTICAL("2-in-1 Vertical", 2),
    TWO_HORIZONTAL("2-in-1 Side by Side", 2),
    THREE_VERTICAL("3-in-1 Vertical", 3),
    THREE_TOP_TWO_BOTTOM("3-in-1 (1 Top, 2 Bottom)", 3),
    FOUR_GRID("4-in-1 Grid", 4),
    SIX_GRID("6-in-1 Grid", 6)
}

/**
 * High-performance, memory-safe document collage generator.
 * Produces standard 300 DPI A4 bitmaps (2480 x 3508 pixels) with clean background
 * clearing, proportional aspect-ratio fitCenter scaling, and subtle document card borders.
 */
object CollageGenerator {

    const val A4_WIDTH_300DPI = 2480
    const val A4_HEIGHT_300DPI = 3508
    const val DEFAULT_BORDER_COLOR = "#E0E0E0"
    const val DEFAULT_BORDER_WIDTH = 4f

    private fun createRect(left: Int, top: Int, right: Int, bottom: Int): Rect {
        return Rect(left, top, right, bottom).apply {
            this.left = left
            this.top = top
            this.right = right
            this.bottom = bottom
        }
    }

    /**
     * Calculates slot rectangles for the specified layout and canvas dimensions.
     * Pure geometric calculation suitable for unit testing and pre-decoding sizing.
     */
    fun calculateSlotRects(
        layout: CollageLayout,
        canvasWidth: Int = A4_WIDTH_300DPI,
        canvasHeight: Int = A4_HEIGHT_300DPI
    ): List<Rect> {
        val margin = (canvasWidth * 0.04f).toInt()
        val spacing = (canvasWidth * 0.02f).toInt()
        val contentW = canvasWidth - (margin * 2)
        val contentH = canvasHeight - (margin * 2)

        return when (layout) {
            CollageLayout.TWO_VERTICAL -> {
                val slotH = (contentH - spacing) / 2
                List(2) { i ->
                    val top = margin + i * (slotH + spacing)
                    createRect(margin, top, margin + contentW, top + slotH)
                }
            }
            CollageLayout.TWO_HORIZONTAL -> {
                val slotW = (contentW - spacing) / 2
                List(2) { i ->
                    val left = margin + i * (slotW + spacing)
                    createRect(left, margin, left + slotW, margin + contentH)
                }
            }
            CollageLayout.THREE_VERTICAL -> {
                val slotH = (contentH - spacing * 2) / 3
                List(3) { i ->
                    val top = margin + i * (slotH + spacing)
                    createRect(margin, top, margin + contentW, top + slotH)
                }
            }
            CollageLayout.THREE_TOP_TWO_BOTTOM -> {
                val slotH = (contentH - spacing) / 2
                val colW = (contentW - spacing) / 2
                val row2Top = margin + slotH + spacing
                listOf(
                    createRect(margin, margin, margin + contentW, margin + slotH),
                    createRect(margin, row2Top, margin + colW, row2Top + slotH),
                    createRect(margin + colW + spacing, row2Top, margin + colW + spacing + colW, row2Top + slotH)
                )
            }
            CollageLayout.FOUR_GRID -> {
                val slotW = (contentW - spacing) / 2
                val slotH = (contentH - spacing) / 2
                List(4) { i ->
                    val row = i / 2
                    val col = i % 2
                    val left = margin + col * (slotW + spacing)
                    val top = margin + row * (slotH + spacing)
                    createRect(left, top, left + slotW, top + slotH)
                }
            }
            CollageLayout.SIX_GRID -> {
                val slotW = (contentW - spacing) / 2
                val slotH = (contentH - spacing * 2) / 3
                List(6) { i ->
                    val row = i / 2
                    val col = i % 2
                    val left = margin + col * (slotW + spacing)
                    val top = margin + row * (slotH + spacing)
                    createRect(left, top, left + slotW, top + slotH)
                }
            }
        }
    }

    /**
     * Interface contract alias specified in PROJECT.md.
     */
    fun generateCollage(
        pages: List<Bitmap>,
        layout: CollageLayout,
        canvasWidth: Int = A4_WIDTH_300DPI,
        canvasHeight: Int = A4_HEIGHT_300DPI
    ): Bitmap = createCollage(pages, layout, canvasWidth, canvasHeight)

    /**
     * Generates a high-resolution A4 collage canvas combining multiple document page bitmaps.
     */
    fun createCollage(
        bitmaps: List<Bitmap>,
        layout: CollageLayout,
        canvasWidth: Int = A4_WIDTH_300DPI,
        canvasHeight: Int = A4_HEIGHT_300DPI,
        drawBorders: Boolean = true
    ): Bitmap {
        val result = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888).apply {
            density = 300
        }
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)

        val borderPaint = if (drawBorders) {
            Paint().apply {
                color = Color.parseColor(DEFAULT_BORDER_COLOR)
                style = Paint.Style.STROKE
                strokeWidth = DEFAULT_BORDER_WIDTH
                isAntiAlias = true
            }
        } else null

        val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val slots = calculateSlotRects(layout, canvasWidth, canvasHeight)

        val count = minOf(layout.maxPages, minOf(slots.size, bitmaps.size))
        for (i in 0 until count) {
            drawBitmapFitted(canvas, bitmaps[i], slots[i], borderPaint, bitmapPaint)
        }

        return result
    }

    /**
     * Memory-safe collage generator from disk file paths.
     * Decodes and downsamples each image one at a time and recycles it immediately,
     * reducing peak heap allocation to just 1 slot bitmap + canvas bitmap (~40MB total).
     */
    fun createCollageFromFiles(
        filePaths: List<String>,
        layout: CollageLayout,
        canvasWidth: Int = A4_WIDTH_300DPI,
        canvasHeight: Int = A4_HEIGHT_300DPI,
        drawBorders: Boolean = true
    ): Bitmap {
        val result = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888).apply {
            density = 300
        }
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)

        val borderPaint = if (drawBorders) {
            Paint().apply {
                color = Color.parseColor(DEFAULT_BORDER_COLOR)
                style = Paint.Style.STROKE
                strokeWidth = DEFAULT_BORDER_WIDTH
                isAntiAlias = true
            }
        } else null

        val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        val slots = calculateSlotRects(layout, canvasWidth, canvasHeight)

        val count = minOf(layout.maxPages, minOf(slots.size, filePaths.size))
        for (i in 0 until count) {
            val slotRect = slots[i]
            val slotW = slotRect.right - slotRect.left
            val slotH = slotRect.bottom - slotRect.top
            val bmp = decodeSampledBitmap(filePaths[i], slotW, slotH)
            if (bmp != null) {
                try {
                    drawBitmapFitted(canvas, bmp, slotRect, borderPaint, bitmapPaint)
                } finally {
                    bmp.recycle()
                }
            }
        }

        return result
    }

    /**
     * Calculates the power-of-two inSampleSize for BitmapFactory.Options.
     */
    fun calculateInSampleSize(
        rawWidth: Int,
        rawHeight: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        var inSampleSize = 1
        if (rawHeight > reqHeight || rawWidth > reqWidth) {
            val halfHeight = rawHeight / 2
            val halfWidth = rawWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /**
     * Decodes a bitmap from disk downsampled to match the target slot dimensions.
     */
    fun decodeSampledBitmap(
        filePath: String,
        reqWidth: Int,
        reqHeight: Int
    ): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(filePath, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null

        options.inSampleSize = calculateInSampleSize(
            rawWidth = options.outWidth,
            rawHeight = options.outHeight,
            reqWidth = reqWidth,
            reqHeight = reqHeight
        )
        options.inJustDecodeBounds = false
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        return BitmapFactory.decodeFile(filePath, options)
    }

    /**
     * Draws a bitmap fitted proportionally within slotRect (fitCenter) and draws an optional border.
     */
    fun drawBitmapFitted(
        canvas: Canvas,
        bitmap: Bitmap,
        slotRect: Rect,
        borderPaint: Paint? = null,
        bitmapPaint: Paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    ) {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return

        val bmpW = bitmap.width.toFloat()
        val bmpH = bitmap.height.toFloat()
        val slotW = (slotRect.right - slotRect.left).toFloat()
        val slotH = (slotRect.bottom - slotRect.top).toFloat()

        if (slotW <= 0f || slotH <= 0f) return

        val scale = minOf(slotW / bmpW, slotH / bmpH)
        val drawW = bmpW * scale
        val drawH = bmpH * scale

        val drawLeft = slotRect.left + (slotW - drawW) / 2f
        val drawTop = slotRect.top + (slotH - drawH) / 2f
        val destRect = RectF(drawLeft, drawTop, drawLeft + drawW, drawTop + drawH)

        canvas.drawBitmap(bitmap, null, destRect, bitmapPaint)
        if (borderPaint != null) {
            canvas.drawRect(destRect, borderPaint)
        }
    }

    /**
     * Stitches multiple document page images into a single vertical long image.
     */
    fun generateLongImage(filePaths: List<String>, targetWidth: Int = 1200): Bitmap? {
        if (filePaths.isEmpty()) return null

        val scaledHeights = filePaths.map { path ->
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, opts)
            if (opts.outWidth > 0 && opts.outHeight > 0) {
                (targetWidth.toFloat() * opts.outHeight / opts.outWidth).toInt()
            } else {
                0
            }
        }
        val validIndices = scaledHeights.indices.filter { scaledHeights[it] > 0 }
        if (validIndices.isEmpty()) return null

        val spacing = 20
        val totalHeight = validIndices.sumOf { scaledHeights[it] } + (validIndices.size - 1) * spacing
        val result = Bitmap.createBitmap(targetWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)

        var currentY = 0
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

        for (i in validIndices) {
            val path = filePaths[i]
            val h = scaledHeights[i]
            val bmp = decodeSampledBitmap(path, targetWidth, h)
            if (bmp != null) {
                val dest = RectF(0f, currentY.toFloat(), targetWidth.toFloat(), (currentY + h).toFloat())
                canvas.drawBitmap(bmp, null, dest, paint)
                bmp.recycle()
            }
            currentY += h + spacing
        }

        return result
    }
}
