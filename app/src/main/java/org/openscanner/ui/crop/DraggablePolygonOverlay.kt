package org.openscanner.ui.crop

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import org.openscanner.cv.DocumentQuadrilateral
import org.openscanner.cv.PointF
import org.openscanner.ui.theme.OkenGreen
import kotlin.math.hypot

@Composable
fun DraggablePolygonOverlay(
    quad: DocumentQuadrilateral,
    boundsRect: RectF,
    onQuadChanged: (DocumentQuadrilateral) -> Unit,
    onTouchPoint: (Offset?) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentQuad by rememberUpdatedState(quad)
    val currentBounds by rememberUpdatedState(boundsRect)
    val currentOnQuadChanged by rememberUpdatedState(onQuadChanged)
    val currentOnTouchPoint by rememberUpdatedState(onTouchPoint)

    var activeHandleIndex by remember { mutableIntStateOf(-1) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val q = currentQuad
                        val p1 = Offset(q.topLeft.x, q.topLeft.y)
                        val p2 = Offset(q.topRight.x, q.topRight.y)
                        val p3 = Offset(q.bottomRight.x, q.bottomRight.y)
                        val p4 = Offset(q.bottomLeft.x, q.bottomLeft.y)

                        val midTop = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
                        val midRight = Offset((p2.x + p3.x) / 2f, (p2.y + p3.y) / 2f)
                        val midBottom = Offset((p3.x + p4.x) / 2f, (p3.y + p4.y) / 2f)
                        val midLeft = Offset((p4.x + p1.x) / 2f, (p4.y + p1.y) / 2f)

                        val handles = listOf(p1, p2, p3, p4, midTop, midRight, midBottom, midLeft)
                        val touchRadius = 54.dp.toPx()

                        val closest = handles.mapIndexed { index, pt ->
                            index to hypot(pt.x - offset.x, pt.y - offset.y)
                        }.filter { it.second < touchRadius }
                         .minByOrNull { it.second }

                        if (closest != null) {
                            activeHandleIndex = closest.first
                            currentOnTouchPoint(offset)
                        } else {
                            // Check if touch is inside the quadrilateral (to drag entire crop area)
                            val poly = listOf(p1, p2, p3, p4)
                            var inside = false
                            var j = poly.size - 1
                            for (i in poly.indices) {
                                val pi = poly[i]
                                val pj = poly[j]
                                if (((pi.y > offset.y) != (pj.y > offset.y)) &&
                                    (offset.x < (pj.x - pi.x) * (offset.y - pi.y) / (pj.y - pi.y) + pi.x)) {
                                    inside = !inside
                                }
                                j = i
                            }
                            if (inside) {
                                activeHandleIndex = 8 // Move entire quadrilateral
                            } else {
                                activeHandleIndex = -1
                            }
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (activeHandleIndex == -1) return@detectDragGestures

                        if (activeHandleIndex < 8) {
                            currentOnTouchPoint(change.position)
                        }

                        val q = currentQuad
                        val b = currentBounds
                        val dx = dragAmount.x
                        val dy = dragAmount.y

                        fun clampPt(x: Float, y: Float): PointF {
                            val cx = if (b.width() > 0f) x.coerceIn(b.left, b.right) else x
                            val cy = if (b.height() > 0f) y.coerceIn(b.top, b.bottom) else y
                            return PointF(cx, cy)
                        }

                        val newQuad = when (activeHandleIndex) {
                            0 -> q.copy(topLeft = clampPt(q.topLeft.x + dx, q.topLeft.y + dy))
                            1 -> q.copy(topRight = clampPt(q.topRight.x + dx, q.topRight.y + dy))
                            2 -> q.copy(bottomRight = clampPt(q.bottomRight.x + dx, q.bottomRight.y + dy))
                            3 -> q.copy(bottomLeft = clampPt(q.bottomLeft.x + dx, q.bottomLeft.y + dy))
                            4 -> q.copy( // Top Edge (move both top handles)
                                topLeft = clampPt(q.topLeft.x + dx, q.topLeft.y + dy),
                                topRight = clampPt(q.topRight.x + dx, q.topRight.y + dy)
                            )
                            5 -> q.copy( // Right Edge (move both right handles)
                                topRight = clampPt(q.topRight.x + dx, q.topRight.y + dy),
                                bottomRight = clampPt(q.bottomRight.x + dx, q.bottomRight.y + dy)
                            )
                            6 -> q.copy( // Bottom Edge (move both bottom handles)
                                bottomLeft = clampPt(q.bottomLeft.x + dx, q.bottomLeft.y + dy),
                                bottomRight = clampPt(q.bottomRight.x + dx, q.bottomRight.y + dy)
                            )
                            7 -> q.copy( // Left Edge (move both left handles)
                                topLeft = clampPt(q.topLeft.x + dx, q.topLeft.y + dy),
                                bottomLeft = clampPt(q.bottomLeft.x + dx, q.bottomLeft.y + dy)
                            )
                            8 -> { // Move entire quadrilateral together
                                val minX = minOf(q.topLeft.x, q.bottomLeft.x)
                                val maxX = maxOf(q.topRight.x, q.bottomRight.x)
                                val minY = minOf(q.topLeft.y, q.topRight.y)
                                val maxY = maxOf(q.bottomLeft.y, q.bottomRight.y)

                                val safeDx = if (b.width() > 0f) {
                                    if (minX + dx < b.left) b.left - minX
                                    else if (maxX + dx > b.right) b.right - maxX
                                    else dx
                                } else dx

                                val safeDy = if (b.height() > 0f) {
                                    if (minY + dy < b.top) b.top - minY
                                    else if (maxY + dy > b.bottom) b.bottom - maxY
                                    else dy
                                } else dy

                                q.copy(
                                    topLeft = PointF(q.topLeft.x + safeDx, q.topLeft.y + safeDy),
                                    topRight = PointF(q.topRight.x + safeDx, q.topRight.y + safeDy),
                                    bottomRight = PointF(q.bottomRight.x + safeDx, q.bottomRight.y + safeDy),
                                    bottomLeft = PointF(q.bottomLeft.x + safeDx, q.bottomLeft.y + safeDy)
                                )
                            }
                            else -> q
                        }
                        currentOnQuadChanged(newQuad)
                    },
                    onDragEnd = {
                        activeHandleIndex = -1
                        currentOnTouchPoint(null)
                    },
                    onDragCancel = {
                        activeHandleIndex = -1
                        currentOnTouchPoint(null)
                    }
                )
            }
    ) {
        val p1 = Offset(quad.topLeft.x, quad.topLeft.y)
        val p2 = Offset(quad.topRight.x, quad.topRight.y)
        val p3 = Offset(quad.bottomRight.x, quad.bottomRight.y)
        val p4 = Offset(quad.bottomLeft.x, quad.bottomLeft.y)

        val path = Path().apply {
            moveTo(p1.x, p1.y)
            lineTo(p2.x, p2.y)
            lineTo(p3.x, p3.y)
            lineTo(p4.x, p4.y)
            close()
        }

        // Semi-transparent highlight over document area
        drawPath(
            path = path,
            color = OkenGreen.copy(alpha = 0.15f)
        )

        // Boundary lines in signature emerald green
        drawPath(
            path = path,
            color = OkenGreen,
            style = Stroke(width = 3.dp.toPx())
        )

        // 4 Corner circular handles (CamScanner Style with white core and green border)
        val cornerRadius = 16.dp.toPx()
        listOf(p1, p2, p3, p4).forEach { pt ->
            drawCircle(Color.White, radius = cornerRadius, center = pt)
            drawCircle(OkenGreen, radius = cornerRadius, center = pt, style = Stroke(3.5.dp.toPx()))
            drawCircle(OkenGreen, radius = 5.dp.toPx(), center = pt)
        }

        // 4 Edge midpoint pill handles
        val midRadius = 9.dp.toPx()
        val midTop = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
        val midRight = Offset((p2.x + p3.x) / 2f, (p2.y + p3.y) / 2f)
        val midBottom = Offset((p3.x + p4.x) / 2f, (p3.y + p4.y) / 2f)
        val midLeft = Offset((p4.x + p1.x) / 2f, (p4.y + p1.y) / 2f)

        listOf(midTop, midRight, midBottom, midLeft).forEach { pt ->
            drawCircle(Color.White, radius = midRadius, center = pt)
            drawCircle(OkenGreen, radius = midRadius, center = pt, style = Stroke(2.5.dp.toPx()))
        }
    }
}
