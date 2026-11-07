package org.openscanner.cv

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlin.math.hypot

@Parcelize
data class PointF(
    val x: Float,
    val y: Float
) : Parcelable {
    fun distanceTo(other: PointF): Float = hypot(x - other.x, y - other.y)
}

@Parcelize
data class DocumentQuadrilateral(
    val topLeft: PointF,
    val topRight: PointF,
    val bottomRight: PointF,
    val bottomLeft: PointF
) : Parcelable {

    fun toFloatArray(): FloatArray {
        return floatArrayOf(
            topLeft.x, topLeft.y,
            topRight.x, topRight.y,
            bottomRight.x, bottomRight.y,
            bottomLeft.x, bottomLeft.y
        )
    }

    companion object {
        fun fromFloatArray(arr: FloatArray): DocumentQuadrilateral {
            require(arr.size >= 8) { "Array must contain at least 8 elements (4 points)" }
            return DocumentQuadrilateral(
                topLeft = PointF(arr[0], arr[1]),
                topRight = PointF(arr[2], arr[3]),
                bottomRight = PointF(arr[4], arr[5]),
                bottomLeft = PointF(arr[6], arr[7])
            )
        }

        fun defaultForDimensions(width: Float, height: Float, marginFraction: Float = 0.08f): DocumentQuadrilateral {
            val mx = width * marginFraction
            val my = height * marginFraction
            return DocumentQuadrilateral(
                topLeft = PointF(mx, my),
                topRight = PointF(width - mx, my),
                bottomRight = PointF(width - mx, height - my),
                bottomLeft = PointF(mx, height - my)
            )
        }
    }

    fun isDefaultFallback(width: Float, height: Float, tolerance: Float = 24f): Boolean {
        val mx = width * 0.08f
        val my = height * 0.08f
        return kotlin.math.abs(topLeft.x - mx) <= tolerance &&
               kotlin.math.abs(topLeft.y - my) <= tolerance &&
               kotlin.math.abs(topRight.x - (width - mx)) <= tolerance &&
               kotlin.math.abs(topRight.y - my) <= tolerance &&
               kotlin.math.abs(bottomRight.x - (width - mx)) <= tolerance &&
               kotlin.math.abs(bottomRight.y - (height - my)) <= tolerance &&
               kotlin.math.abs(bottomLeft.x - mx) <= tolerance &&
               kotlin.math.abs(bottomLeft.y - (height - my)) <= tolerance
    }
}
