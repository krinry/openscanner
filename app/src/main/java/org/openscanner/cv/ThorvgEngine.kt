package org.openscanner.cv

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * High-performance vector graphics and rasterization engine powered by native ThorVG.
 * Provides sub-millisecond anti-aliased vector rendering, shapes, strokes, SVG stamping,
 * and native smudge/blur redaction directly onto Android Bitmap pixel buffers.
 */
object ThorvgEngine {

    init {
        try {
            System.loadLibrary("openscanner")
            nativeInit()
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    enum class ShapeType(val id: Int) {
        RECTANGLE(0),
        FILLED_BOX(1),
        CIRCLE(2),
        LINE(3),
        ARROW(4)
    }

    /**
     * Renders a smooth anti-aliased freehand stroke onto [bitmap] using ThorVG CPU engine.
     * [points] is a flat array of alternating (x, y) coordinates: [x0, y0, x1, y1, ...].
     */
    fun renderStroke(
        bitmap: Bitmap,
        points: FloatArray,
        strokeWidth: Float,
        color: Color
    ): Boolean {
        if (points.size < 4) return false
        val argb = color.toArgb()
        val a = (argb ushr 24) and 0xFF
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF

        return nativeRenderStroke(
            bitmap = bitmap,
            points = points,
            strokeWidth = strokeWidth,
            r = r,
            g = g,
            b = b,
            a = a
        )
    }

    /**
     * Renders an anti-aliased geometric shape onto [bitmap] using ThorVG.
     */
    fun renderShape(
        bitmap: Bitmap,
        shapeType: ShapeType,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        strokeWidth: Float,
        color: Color,
        isFilled: Boolean = false
    ): Boolean {
        val argb = color.toArgb()
        val a = (argb ushr 24) and 0xFF
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF

        return nativeRenderShape(
            bitmap = bitmap,
            shapeType = shapeType.id,
            x1 = x1,
            y1 = y1,
            x2 = x2,
            y2 = y2,
            strokeWidth = strokeWidth,
            r = r,
            g = g,
            b = b,
            a = a,
            isFilled = isFilled
        )
    }

    /**
     * Renders an SVG vector snippet directly onto [bitmap] at the given coordinates.
     */
    fun renderSvg(
        bitmap: Bitmap,
        svgString: String,
        x: Float = 0f,
        y: Float = 0f,
        width: Float = 0f,
        height: Float = 0f,
        angle: Float = 0f,
        opacity: Float = 1.0f
    ): Boolean {
        return nativeRenderSvg(
            bitmap = bitmap,
            svgString = svgString,
            x = x,
            y = y,
            width = width,
            height = height,
            angle = angle,
            opacity = opacity
        )
    }

    /**
     * Applies a high-speed native Gaussian/smudge blur over a sensitive region of [bitmap].
     * Ideal for redacting signatures, account numbers, and personal IDs.
     */
    fun applySmudgeBlur(
        bitmap: Bitmap,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        radius: Int = 14
    ): Boolean {
        return nativeApplySmudgeBlur(
            bitmap = bitmap,
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            radius = radius
        )
    }

    /**
     * Renders a vector watermark onto [bitmap] using ThorVG SVG capabilities.
     */
    fun renderWatermark(
        bitmap: Bitmap,
        text: String,
        angle: Float = -35f,
        opacity: Float = 0.28f,
        colorHex: String = "#888888"
    ): Boolean {
        if (text.isBlank()) return true
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val fontSize = (w * 0.055f).coerceIn(24f, 80f)

        // Generate SVG text element
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg" width="$w" height="$h">
                <text x="${w / 2f}" y="${h / 2f}" 
                      font-size="$fontSize" 
                      font-family="sans-serif" 
                      font-weight="bold" 
                      fill="$colorHex" 
                      text-anchor="middle" 
                      dominant-baseline="central"
                      transform="rotate($angle, ${w / 2f}, ${h / 2f})">
                    ${escapeXml(text)}
                </text>
            </svg>
        """.trimIndent()

        return renderSvg(
            bitmap = bitmap,
            svgString = svg,
            x = 0f,
            y = 0f,
            width = w,
            height = h,
            angle = 0f,
            opacity = opacity
        )
    }

    private fun escapeXml(input: String): String {
        return input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    // Native JNI bindings
    @JvmStatic
    private external fun nativeInit(): Boolean

    @JvmStatic
    private external fun nativeRenderStroke(
        bitmap: Bitmap,
        points: FloatArray,
        strokeWidth: Float,
        r: Int,
        g: Int,
        b: Int,
        a: Int
    ): Boolean

    @JvmStatic
    private external fun nativeRenderShape(
        bitmap: Bitmap,
        shapeType: Int,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        strokeWidth: Float,
        r: Int,
        g: Int,
        b: Int,
        a: Int,
        isFilled: Boolean
    ): Boolean

    @JvmStatic
    private external fun nativeRenderSvg(
        bitmap: Bitmap,
        svgString: String,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        angle: Float,
        opacity: Float
    ): Boolean

    @JvmStatic
    private external fun nativeApplySmudgeBlur(
        bitmap: Bitmap,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        radius: Int
    ): Boolean
}
