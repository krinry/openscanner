package org.openscanner.ui.crop

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import org.openscanner.cv.DocumentQuadrilateral
import org.openscanner.cv.PointF
import org.openscanner.ui.theme.ScannerGreen
import kotlin.math.hypot

@Composable
fun DraggablePolygonOverlay(
    quad: DocumentQuadrilateral,
    boundsRect: RectF,
    onQuadChanged: (DocumentQuadrilateral) -> Unit,
    onTouchPoint: (Offset?) -> Unit = {},
    onHandleActive: (handlePoint: Offset?, touchPoint: Offset?) -> Unit = { _, _ -> },
    zoomScale: Float = 1f,
    onTransform: (zoomChange: Float, panChange: Offset) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val currentQuad by rememberUpdatedState(quad)
    val currentBounds by rememberUpdatedState(boundsRect)
    val currentOnQuadChanged by rememberUpdatedState(onQuadChanged)
    val currentOnTouchPoint by rememberUpdatedState(onTouchPoint)
    val currentOnHandleActive by rememberUpdatedState(onHandleActive)
    val currentOnTransform by rememberUpdatedState(onTransform)
    val currentZoomScale by rememberUpdatedState(zoomScale)

    var activeHandleIndex by remember { mutableIntStateOf(-1) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var lastTouchPos = down.position

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
                    val touchRadius = (48.dp.toPx() / currentZoomScale.coerceAtLeast(1f)).coerceAtLeast(32.dp.toPx())

                    val closest = handles.mapIndexed { index, pt ->
                        index to hypot(pt.x - down.position.x, pt.y - down.position.y)
                    }.filter { it.second < touchRadius }
                        .minByOrNull { it.second }

                    if (closest != null) {
                        activeHandleIndex = closest.first
                        val handlePos = handles[closest.first]
                        currentOnTouchPoint(down.position)
                        currentOnHandleActive(handlePos, down.position)
                    } else {
                        // Check if touch is inside the quadrilateral (to drag entire crop area)
                        val poly = listOf(p1, p2, p3, p4)
                        var inside = false
                        var j = poly.size - 1
                        for (i in poly.indices) {
                            val pi = poly[i]
                            val pj = poly[j]
                            if (((pi.y > down.position.y) != (pj.y > down.position.y)) &&
                                (down.position.x < (pj.x - pi.x) * (down.position.y - pi.y) / (pj.y - pi.y) + pi.x)
                            ) {
                                inside = !inside
                            }
                            j = i
                        }
                        if (inside) {
                            activeHandleIndex = 8 // Move entire quadrilateral
                        } else if (currentZoomScale > 1.05f) {
                            activeHandleIndex = 9 // 1-finger Pan zoomed view
                        } else {
                            activeHandleIndex = -1
                        }
                        currentOnTouchPoint(null)
                        currentOnHandleActive(null, null)
                    }

                    var isMultiTouch = false
                    var prevPinchDistance = 0f
                    var prevPinchCentroid = Offset.Zero

                    do {
                        val event = awaitPointerEvent()
                        val pressedPointers = event.changes.filter { it.pressed }

                        if (pressedPointers.size >= 2) {
                            // Two or more fingers: Pinch-to-zoom & pan
                            isMultiTouch = true
                            activeHandleIndex = -1
                            currentOnTouchPoint(null)
                            currentOnHandleActive(null, null)

                            val ptr1 = pressedPointers[0].position
                            val ptr2 = pressedPointers[1].position
                            val currentDistance = hypot(ptr1.x - ptr2.x, ptr1.y - ptr2.y)
                            val currentCentroid = Offset((ptr1.x + ptr2.x) / 2f, (ptr1.y + ptr2.y) / 2f)

                            if (prevPinchDistance > 0f) {
                                val zoomRatio = currentDistance / prevPinchDistance
                                val panDelta = currentCentroid - prevPinchCentroid
                                currentOnTransform(zoomRatio, panDelta)
                            }
                            prevPinchDistance = currentDistance
                            prevPinchCentroid = currentCentroid

                            pressedPointers.forEach { it.consume() }
                        } else if (pressedPointers.size == 1 && !isMultiTouch) {
                            val change = pressedPointers[0]
                            val currentPos = change.position
                            val dragAmount = currentPos - lastTouchPos
                            lastTouchPos = currentPos

                            if (activeHandleIndex == 9) {
                                // 1-finger pan when zoomed in
                                change.consume()
                                currentOnTransform(1f, dragAmount)
                            } else if (activeHandleIndex in 0..8) {
                                change.consume()
                                val curQ = currentQuad
                                val b = currentBounds
                                val dx = dragAmount.x
                                val dy = dragAmount.y

                                fun clampPt(x: Float, y: Float): PointF {
                                    val cx = if (b.width() > 0f) x.coerceIn(b.left, b.right) else x
                                    val cy = if (b.height() > 0f) y.coerceIn(b.top, b.bottom) else y
                                    return PointF(cx, cy)
                                }

                                val newQuad = when (activeHandleIndex) {
                                    0 -> curQ.copy(topLeft = clampPt(curQ.topLeft.x + dx, curQ.topLeft.y + dy))
                                    1 -> curQ.copy(topRight = clampPt(curQ.topRight.x + dx, curQ.topRight.y + dy))
                                    2 -> curQ.copy(bottomRight = clampPt(curQ.bottomRight.x + dx, curQ.bottomRight.y + dy))
                                    3 -> curQ.copy(bottomLeft = clampPt(curQ.bottomLeft.x + dx, curQ.bottomLeft.y + dy))
                                    4 -> curQ.copy(
                                        topLeft = clampPt(curQ.topLeft.x + dx, curQ.topLeft.y + dy),
                                        topRight = clampPt(curQ.topRight.x + dx, curQ.topRight.y + dy)
                                    )
                                    5 -> curQ.copy(
                                        topRight = clampPt(curQ.topRight.x + dx, curQ.topRight.y + dy),
                                        bottomRight = clampPt(curQ.bottomRight.x + dx, curQ.bottomRight.y + dy)
                                    )
                                    6 -> curQ.copy(
                                        bottomLeft = clampPt(curQ.bottomLeft.x + dx, curQ.bottomLeft.y + dy),
                                        bottomRight = clampPt(curQ.bottomRight.x + dx, curQ.bottomRight.y + dy)
                                    )
                                    7 -> curQ.copy(
                                        topLeft = clampPt(curQ.topLeft.x + dx, curQ.topLeft.y + dy),
                                        bottomLeft = clampPt(curQ.bottomLeft.x + dx, curQ.bottomLeft.y + dy)
                                    )
                                    8 -> {
                                        val minX = minOf(curQ.topLeft.x, curQ.topRight.x, curQ.bottomRight.x, curQ.bottomLeft.x)
                                        val maxX = maxOf(curQ.topLeft.x, curQ.topRight.x, curQ.bottomRight.x, curQ.bottomLeft.x)
                                        val minY = minOf(curQ.topLeft.y, curQ.topRight.y, curQ.bottomRight.y, curQ.bottomLeft.y)
                                        val maxY = maxOf(curQ.topLeft.y, curQ.topRight.y, curQ.bottomRight.y, curQ.bottomLeft.y)

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

                                        curQ.copy(
                                            topLeft = PointF((curQ.topLeft.x + safeDx).coerceIn(b.left, b.right), (curQ.topLeft.y + safeDy).coerceIn(b.top, b.bottom)),
                                            topRight = PointF((curQ.topRight.x + safeDx).coerceIn(b.left, b.right), (curQ.topRight.y + safeDy).coerceIn(b.top, b.bottom)),
                                            bottomRight = PointF((curQ.bottomRight.x + safeDx).coerceIn(b.left, b.right), (curQ.bottomRight.y + safeDy).coerceIn(b.top, b.bottom)),
                                            bottomLeft = PointF((curQ.bottomLeft.x + safeDx).coerceIn(b.left, b.right), (curQ.bottomLeft.y + safeDy).coerceIn(b.top, b.bottom))
                                        )
                                    }
                                    else -> curQ
                                }

                                currentOnQuadChanged(newQuad)

                                if (activeHandleIndex < 8) {
                                    val np1 = Offset(newQuad.topLeft.x, newQuad.topLeft.y)
                                    val np2 = Offset(newQuad.topRight.x, newQuad.topRight.y)
                                    val np3 = Offset(newQuad.bottomRight.x, newQuad.bottomRight.y)
                                    val np4 = Offset(newQuad.bottomLeft.x, newQuad.bottomLeft.y)
                                    val nMidTop = Offset((np1.x + np2.x) / 2f, (np1.y + np2.y) / 2f)
                                    val nMidRight = Offset((np2.x + np3.x) / 2f, (np2.y + np3.y) / 2f)
                                    val nMidBottom = Offset((np3.x + np4.x) / 2f, (np3.y + np4.y) / 2f)
                                    val nMidLeft = Offset((np4.x + np1.x) / 2f, (np4.y + np1.y) / 2f)

                                    val updatedHandlePos = when (activeHandleIndex) {
                                        0 -> np1
                                        1 -> np2
                                        2 -> np3
                                        3 -> np4
                                        4 -> nMidTop
                                        5 -> nMidRight
                                        6 -> nMidBottom
                                        7 -> nMidLeft
                                        else -> currentPos
                                    }
                                    currentOnTouchPoint(currentPos)
                                    currentOnHandleActive(updatedHandlePos, currentPos)
                                } else {
                                    currentOnTouchPoint(null)
                                    currentOnHandleActive(null, null)
                                }
                            }
                        }
                    } while (pressedPointers.isNotEmpty())

                    activeHandleIndex = -1
                    currentOnTouchPoint(null)
                    currentOnHandleActive(null, null)
                }
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

        // Semi-transparent highlight over document area (reduced opacity for clear readability)
        drawPath(
            path = path,
            color = ScannerGreen.copy(alpha = 0.07f)
        )

        // Boundary lines in signature emerald green (subtle, crisp 2.2dp)
        drawPath(
            path = path,
            color = ScannerGreen.copy(alpha = 0.8f),
            style = Stroke(width = 2.2.dp.toPx())
        )

        // 4 Corner circular handles with white core and green border
        val cornerRadius = 14.dp.toPx()
        listOf(p1, p2, p3, p4).forEach { pt ->
            drawCircle(Color.White.copy(alpha = 0.95f), radius = cornerRadius, center = pt)
            drawCircle(ScannerGreen.copy(alpha = 0.85f), radius = cornerRadius, center = pt, style = Stroke(3.dp.toPx()))
            drawCircle(ScannerGreen, radius = 4.dp.toPx(), center = pt)
        }

        // 4 Edge midpoint pill handles
        val midRadius = 8.dp.toPx()
        val midTop = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
        val midRight = Offset((p2.x + p3.x) / 2f, (p2.y + p3.y) / 2f)
        val midBottom = Offset((p3.x + p4.x) / 2f, (p3.y + p4.y) / 2f)
        val midLeft = Offset((p4.x + p1.x) / 2f, (p4.y + p1.y) / 2f)

        listOf(midTop, midRight, midBottom, midLeft).forEach { pt ->
            drawCircle(Color.White.copy(alpha = 0.9f), radius = midRadius, center = pt)
            drawCircle(ScannerGreen.copy(alpha = 0.85f), radius = midRadius, center = pt, style = Stroke(2.dp.toPx()))
        }
    }
}
