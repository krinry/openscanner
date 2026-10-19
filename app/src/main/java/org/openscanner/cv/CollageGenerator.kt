package org.openscanner.cv

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF

enum class CollageLayout(val title: String, val maxPages: Int) {
    TWO_VERTICAL("2-in-1 Vertical", 2),
    TWO_HORIZONTAL("2-in-1 Side by Side", 2),
    FOUR_GRID("4-in-1 Grid", 4),
    SIX_GRID("6-in-1 Grid", 6)
}

object CollageGenerator {

    /**
     * Generates a high-resolution A4 collage canvas combining multiple document pages.
     * Standard A4 at 300 DPI: 2480 x 3508 pixels.
     */
    fun createCollage(
        bitmaps: List<Bitmap>,
        layout: CollageLayout,
        canvasWidth: Int = 2480,
        canvasHeight: Int = 3508
    ): Bitmap {
        val result = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)

        val borderPaint = Paint().apply {
            color = Color.parseColor("#E0E0E0")
            style = Paint.Style.STROKE
            strokeWidth = 4f
            isAntiAlias = true
        }

        val margin = (canvasWidth * 0.04f).toInt()
        val spacing = (canvasWidth * 0.02f).toInt()
        val contentW = canvasWidth - (margin * 2)
        val contentH = canvasHeight - (margin * 2)

        when (layout) {
            CollageLayout.TWO_VERTICAL -> {
                val slotH = (contentH - spacing) / 2
                for (i in 0 until minOf(2, bitmaps.size)) {
                    val top = margin + i * (slotH + spacing)
                    val slotRect = Rect(margin, top, margin + contentW, top + slotH)
                    drawBitmapFitted(canvas, bitmaps[i], slotRect, borderPaint)
                }
            }
            CollageLayout.TWO_HORIZONTAL -> {
                val slotW = (contentW - spacing) / 2
                for (i in 0 until minOf(2, bitmaps.size)) {
                    val left = margin + i * (slotW + spacing)
                    val slotRect = Rect(left, margin, left + slotW, margin + contentH)
                    drawBitmapFitted(canvas, bitmaps[i], slotRect, borderPaint)
                }
            }
            CollageLayout.FOUR_GRID -> {
                val slotW = (contentW - spacing) / 2
                val slotH = (contentH - spacing) / 2
                for (i in 0 until minOf(4, bitmaps.size)) {
                    val row = i / 2
                    val col = i % 2
                    val left = margin + col * (slotW + spacing)
                    val top = margin + row * (slotH + spacing)
                    val slotRect = Rect(left, top, left + slotW, top + slotH)
                    drawBitmapFitted(canvas, bitmaps[i], slotRect, borderPaint)
                }
            }
            CollageLayout.SIX_GRID -> {
                val slotW = (contentW - spacing) / 2
                val slotH = (contentH - spacing * 2) / 3
                for (i in 0 until minOf(6, bitmaps.size)) {
                    val row = i / 2
                    val col = i % 2
                    val left = margin + col * (slotW + spacing)
                    val top = margin + row * (slotH + spacing)
                    val slotRect = Rect(left, top, left + slotW, top + slotH)
                    drawBitmapFitted(canvas, bitmaps[i], slotRect, borderPaint)
                }
            }
        }

        return result
    }

    private fun drawBitmapFitted(
        canvas: Canvas,
        bitmap: Bitmap,
        slotRect: Rect,
        borderPaint: Paint
    ) {
        val bmpW = bitmap.width.toFloat()
        val bmpH = bitmap.height.toFloat()
        val slotW = slotRect.width().toFloat()
        val slotH = slotRect.height().toFloat()

        val scale = minOf(slotW / bmpW, slotH / bmpH)
        val drawW = bmpW * scale
        val drawH = bmpH * scale

        val drawLeft = slotRect.left + (slotW - drawW) / 2f
        val drawTop = slotRect.top + (slotH - drawH) / 2f
        val destRect = RectF(drawLeft, drawTop, drawLeft + drawW, drawTop + drawH)

        canvas.drawBitmap(bitmap, null, destRect, Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.drawRect(destRect, borderPaint)
    }
}
