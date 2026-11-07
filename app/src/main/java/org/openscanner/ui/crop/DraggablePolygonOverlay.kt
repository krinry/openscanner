package org.openscanner.ui.crop

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import org.openscanner.cv.DocumentQuadrilateral
import org.openscanner.cv.PointF
import org.openscanner.ui.theme.ScannerGreen
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Interactive 8-handle Crop Quadrilateral Overlay.
 *
 * Implements the complete document crop box mechanics of OKEN Scanner / CamScanner:
 * 1. Outside dimming: 52% dark translucent mask outside the quadrilateral polygon.
 * 2. 8 Interactive Handles: 4 circular corner vertices + 4 directional edge midpoint pills.
 * 3. OKEN Midpoint translation: Dragging an edge midpoint translates both adjacent vertices
 *    together, preserving perspective tilt and document skew.
 * 4. Center pan: Dragging inside the polygon translates the entire document bounding box.
 * 5. Magnetic snap: Locks onto physical document edges, 90° orthogonal alignments, and image bounds.
 * 6. Validity detection: Turns orange-red if the quad becomes self-intersecting or concave.
 */
@Composable
fun DraggablePolygonOverlay(
    quad: DocumentQuadrilateral,
    boundsRect: RectF,
    onQuadChanged: (DocumentQuadrilateral) -> Unit,
    onTouchPoint: (Offset?) -> Unit = {},
    onHandleActive: (handlePoint: Offset?, touchPoint: Offset?, isSnapped: Boolean) -> Unit = { _, _, _ -> },
    zoomScale: Float = 1f,
    onTransform: (zoomChange: Float, panChange: Offset) -> Unit = { _, _ -> },
    onSnapPoint: ((rawPoint: PointF, searchRadiusPx: Float) -> Pair<PointF, Boolean>)? = null,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val currentQuad by rememberUpdatedState(quad)
    val currentBounds by rememberUpdatedState(boundsRect)
    val currentOnQuadChanged by rememberUpdatedState(onQuadChanged)
    val currentOnTouchPoint by rememberUpdatedState(onTouchPoint)
    val currentOnHandleActive by rememberUpdatedState(onHandleActive)
    val currentOnTransform by rememberUpdatedState(onTransform)
    val currentZoomScale by rememberUpdatedState(zoomScale)
    val currentOnSnapPoint by rememberUpdatedState(onSnapPoint)

    var activeHandleIndex by remember { mutableIntStateOf(-1) }
    var wasSnapped by remember { mutableStateOf(false) }
    var magneticGuideLines by remember { mutableStateOf<List<Pair<Offset, Offset>>>(emptyList()) }

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
                    val touchRadius = (42.dp.toPx() / currentZoomScale.coerceAtLeast(1f)).coerceAtLeast(30.dp.toPx())

                    val closest = handles.mapIndexed { index, pt ->
                        index to hypot(pt.x - down.position.x, pt.y - down.position.y)
                    }.filter { it.second < touchRadius }
                        .minByOrNull { it.second }

                    if (closest != null) {
                        activeHandleIndex = closest.first
                        val handlePos = handles[closest.first]
                        currentOnTouchPoint(down.position)
                        currentOnHandleActive(handlePos, down.position, false)
                    } else {
                        // Check if touch is inside the quadrilateral (to drag entire crop polygon)
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
                        currentOnHandleActive(null, null, false)
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
                            magneticGuideLines = emptyList()
                            currentOnTouchPoint(null)
                            currentOnHandleActive(null, null, false)

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

                                // Magnetic snap threshold
                                val snapDist = (24.dp.toPx() / currentZoomScale.coerceAtLeast(1f))
                                var isSnapped = false
                                val currentGuides = mutableListOf<Pair<Offset, Offset>>()

                                fun magneticSnapCorner(rawPt: PointF, cornerIndex: Int): PointF {
                                    var sx = rawPt.x
                                    var sy = rawPt.y

                                    // Image border magnetic snap
                                    if (abs(sx - b.left) < snapDist) { sx = b.left; isSnapped = true }
                                    else if (abs(sx - b.right) < snapDist) { sx = b.right; isSnapped = true }

                                    if (abs(sy - b.top) < snapDist) { sy = b.top; isSnapped = true }
                                    else if (abs(sy - b.bottom) < snapDist) { sy = b.bottom; isSnapped = true }

                                    // Orthogonal 90-degree alignment snap to adjacent corners
                                    when (cornerIndex) {
                                        0 -> { // Top-Left: horizontal to Top-Right, vertical to Bottom-Left
                                            if (abs(sy - curQ.topRight.y) < snapDist) {
                                                sy = curQ.topRight.y
                                                isSnapped = true
                                                currentGuides.add(Offset(sx, sy) to Offset(curQ.topRight.x, curQ.topRight.y))
                                            }
                                            if (abs(sx - curQ.bottomLeft.x) < snapDist) {
                                                sx = curQ.bottomLeft.x
                                                isSnapped = true
                                                currentGuides.add(Offset(sx, sy) to Offset(curQ.bottomLeft.x, curQ.bottomLeft.y))
                                            }
                                        }
                                        1 -> { // Top-Right: horizontal to Top-Left, vertical to Bottom-Right
                                            if (abs(sy - curQ.topLeft.y) < snapDist) {
                                                sy = curQ.topLeft.y
                                                isSnapped = true
                                                currentGuides.add(Offset(sx, sy) to Offset(curQ.topLeft.x, curQ.topLeft.y))
                                            }
                                            if (abs(sx - curQ.bottomRight.x) < snapDist) {
                                                sx = curQ.bottomRight.x
                                                isSnapped = true
                                                currentGuides.add(Offset(sx, sy) to Offset(curQ.bottomRight.x, curQ.bottomRight.y))
                                            }
                                        }
                                        2 -> { // Bottom-Right: horizontal to Bottom-Left, vertical to Top-Right
                                            if (abs(sy - curQ.bottomLeft.y) < snapDist) {
                                                sy = curQ.bottomLeft.y
                                                isSnapped = true
                                                currentGuides.add(Offset(sx, sy) to Offset(curQ.bottomLeft.x, curQ.bottomLeft.y))
                                            }
                                            if (abs(sx - curQ.topRight.x) < snapDist) {
                                                sx = curQ.topRight.x
                                                isSnapped = true
                                                currentGuides.add(Offset(sx, sy) to Offset(curQ.topRight.x, curQ.topRight.y))
                                            }
                                        }
                                        3 -> { // Bottom-Left: horizontal to Bottom-Right, vertical to Top-Left
                                            if (abs(sy - curQ.bottomRight.y) < snapDist) {
                                                sy = curQ.bottomRight.y
                                                isSnapped = true
                                                currentGuides.add(Offset(sx, sy) to Offset(curQ.bottomRight.x, curQ.bottomRight.y))
                                            }
                                            if (abs(sx - curQ.topLeft.x) < snapDist) {
                                                sx = curQ.topLeft.x
                                                isSnapped = true
                                                currentGuides.add(Offset(sx, sy) to Offset(curQ.topLeft.x, curQ.topLeft.y))
                                            }
                                        }
                                    }

                                    // If not snapped to right angle, check CV edge absorption (magnetic snap to document border)
                                    if (!isSnapped) {
                                        currentOnSnapPoint?.invoke(PointF(sx, sy), snapDist)?.let { (snappedPt, didSnap) ->
                                            if (didSnap) {
                                                sx = snappedPt.x
                                                sy = snappedPt.y
                                                isSnapped = true
                                            }
                                        }
                                    }

                                    return clampPt(sx, sy)
                                }

                                val newQuad = when (activeHandleIndex) {
                                    0 -> curQ.copy(topLeft = magneticSnapCorner(PointF(curQ.topLeft.x + dx, curQ.topLeft.y + dy), 0))
                                    1 -> curQ.copy(topRight = magneticSnapCorner(PointF(curQ.topRight.x + dx, curQ.topRight.y + dy), 1))
                                    2 -> curQ.copy(bottomRight = magneticSnapCorner(PointF(curQ.bottomRight.x + dx, curQ.bottomRight.y + dy), 2))
                                    3 -> curQ.copy(bottomLeft = magneticSnapCorner(PointF(curQ.bottomLeft.x + dx, curQ.bottomLeft.y + dy), 3))
                                    // Midpoint Top: Moves both Top-Left and Top-Right together by (dx, dy)
                                    4 -> {
                                        var targetDy = dy
                                        val midX = (curQ.topLeft.x + curQ.topRight.x) / 2f
                                        val midY = (curQ.topLeft.y + curQ.topRight.y) / 2f + dy
                                        if (abs(midY - b.top) < snapDist) {
                                            targetDy = b.top - (curQ.topLeft.y + curQ.topRight.y) / 2f
                                            isSnapped = true
                                        } else {
                                            currentOnSnapPoint?.invoke(PointF(midX, midY), snapDist)?.let { (snappedPt, didSnap) ->
                                                if (didSnap) {
                                                    targetDy = snappedPt.y - (curQ.topLeft.y + curQ.topRight.y) / 2f
                                                    isSnapped = true
                                                }
                                            }
                                        }
                                        curQ.copy(
                                            topLeft = clampPt(curQ.topLeft.x + dx, curQ.topLeft.y + targetDy),
                                            topRight = clampPt(curQ.topRight.x + dx, curQ.topRight.y + targetDy)
                                        )
                                    }
                                    // Midpoint Right: Moves both Top-Right and Bottom-Right together by (dx, dy)
                                    5 -> {
                                        var targetDx = dx
                                        val midX = (curQ.topRight.x + curQ.bottomRight.x) / 2f + dx
                                        val midY = (curQ.topRight.y + curQ.bottomRight.y) / 2f
                                        if (abs(midX - b.right) < snapDist) {
                                            targetDx = b.right - (curQ.topRight.x + curQ.bottomRight.x) / 2f
                                            isSnapped = true
                                        } else {
                                            currentOnSnapPoint?.invoke(PointF(midX, midY), snapDist)?.let { (snappedPt, didSnap) ->
                                                if (didSnap) {
                                                    targetDx = snappedPt.x - (curQ.topRight.x + curQ.bottomRight.x) / 2f
                                                    isSnapped = true
                                                }
                                            }
                                        }
                                        curQ.copy(
                                            topRight = clampPt(curQ.topRight.x + targetDx, curQ.topRight.y + dy),
                                            bottomRight = clampPt(curQ.bottomRight.x + targetDx, curQ.bottomRight.y + dy)
                                        )
                                    }
                                    // Midpoint Bottom: Moves both Bottom-Right and Bottom-Left together by (dx, dy)
                                    6 -> {
                                        var targetDy = dy
                                        val midX = (curQ.bottomLeft.x + curQ.bottomRight.x) / 2f
                                        val midY = (curQ.bottomLeft.y + curQ.bottomRight.y) / 2f + dy
                                        if (abs(midY - b.bottom) < snapDist) {
                                            targetDy = b.bottom - (curQ.bottomLeft.y + curQ.bottomRight.y) / 2f
                                            isSnapped = true
                                        } else {
                                            currentOnSnapPoint?.invoke(PointF(midX, midY), snapDist)?.let { (snappedPt, didSnap) ->
                                                if (didSnap) {
                                                    targetDy = snappedPt.y - (curQ.bottomLeft.y + curQ.bottomRight.y) / 2f
                                                    isSnapped = true
                                                }
                                            }
                                        }
                                        curQ.copy(
                                            bottomRight = clampPt(curQ.bottomRight.x + dx, curQ.bottomRight.y + targetDy),
                                            bottomLeft = clampPt(curQ.bottomLeft.x + dx, curQ.bottomLeft.y + targetDy)
                                        )
                                    }
                                    // Midpoint Left: Moves both Bottom-Left and Top-Left together by (dx, dy)
                                    7 -> {
                                        var targetDx = dx
                                        val midX = (curQ.topLeft.x + curQ.bottomLeft.x) / 2f + dx
                                        val midY = (curQ.topLeft.y + curQ.bottomLeft.y) / 2f
                                        if (abs(midX - b.left) < snapDist) {
                                            targetDx = b.left - (curQ.topLeft.x + curQ.bottomLeft.x) / 2f
                                            isSnapped = true
                                        } else {
                                            currentOnSnapPoint?.invoke(PointF(midX, midY), snapDist)?.let { (snappedPt, didSnap) ->
                                                if (didSnap) {
                                                    targetDx = snappedPt.x - (curQ.topLeft.x + curQ.bottomLeft.x) / 2f
                                                    isSnapped = true
                                                }
                                            }
                                        }
                                        curQ.copy(
                                            topLeft = clampPt(curQ.topLeft.x + targetDx, curQ.topLeft.y + dy),
                                            bottomLeft = clampPt(curQ.bottomLeft.x + targetDx, curQ.bottomLeft.y + dy)
                                        )
                                    }
                                    // Body Pan: Moves entire quadrilateral
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

                                // Haptic pulse feedback on snap state transition
                                if (isSnapped && !wasSnapped) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                wasSnapped = isSnapped
                                magneticGuideLines = currentGuides

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
                                    currentOnHandleActive(updatedHandlePos, currentPos, isSnapped)
                                } else {
                                    currentOnTouchPoint(null)
                                    currentOnHandleActive(null, null, false)
                                }
                            }
                        }
                    } while (pressedPointers.isNotEmpty())

                    activeHandleIndex = -1
                    wasSnapped = false
                    magneticGuideLines = emptyList()
                    currentOnTouchPoint(null)
                    currentOnHandleActive(null, null, false)
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

        val isValidGeometry = quad.isValidConvex()
        val primaryColor = if (isValidGeometry) ScannerGreen else Color(0xFFFF9500)

        // 1. Outside dimming: 52% translucent dark mask outside the document crop boundary
        clipPath(path, clipOp = ClipOp.Difference) {
            drawRect(Color.Black.copy(alpha = 0.52f))
        }

        // 2. High-contrast document boundary stroke
        drawPath(
            path = path,
            color = primaryColor.copy(alpha = 0.95f),
            style = Stroke(
                width = 2.4.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // 3. Draw magnetic alignment guide lines if active
        if (magneticGuideLines.isNotEmpty()) {
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
            magneticGuideLines.forEach { (start, end) ->
                drawLine(
                    color = Color(0xFF00E5FF),
                    start = start,
                    end = end,
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = dashEffect
                )
            }
        }

        // 4. 4 Corner circular handles with white disc, green core, and center dot
        val cornerRadius = 14.dp.toPx()
        val cornerInnerRadius = 6.5.dp.toPx()
        listOf(
            0 to p1,
            1 to p2,
            2 to p3,
            3 to p4
        ).forEach { (idx, pt) ->
            val isCurrentActive = (activeHandleIndex == idx)
            val ringColor = if (isCurrentActive && wasSnapped) Color(0xFF00E5FF) else primaryColor

            // Drop shadow simulation
            drawCircle(Color.Black.copy(alpha = 0.35f), radius = cornerRadius + 1.5.dp.toPx(), center = Offset(pt.x, pt.y + 1.dp.toPx()))
            // Outer white solid circle
            drawCircle(Color.White, radius = cornerRadius, center = pt)
            // Inner color circle
            drawCircle(ringColor, radius = cornerInnerRadius, center = pt)
            // Center white dot
            drawCircle(Color.White, radius = 2.dp.toPx(), center = pt)

            if (isCurrentActive && wasSnapped) {
                // Glowing magnetic lock pulse ring
                drawCircle(Color(0xFF00E5FF).copy(alpha = 0.35f), radius = cornerRadius + 6.dp.toPx(), center = pt, style = Stroke(2.5.dp.toPx()))
            }
        }

        // 5. 4 Directional Edge Midpoint Pills (Horizontal for top/bottom, Vertical for left/right)
        val midTop = Offset((p1.x + p2.x) / 2f, (p1.y + p2.y) / 2f)
        val midRight = Offset((p2.x + p3.x) / 2f, (p2.y + p3.y) / 2f)
        val midBottom = Offset((p3.x + p4.x) / 2f, (p3.y + p4.y) / 2f)
        val midLeft = Offset((p4.x + p1.x) / 2f, (p4.y + p1.y) / 2f)

        val pillLong = 22.dp.toPx()
        val pillShort = 9.dp.toPx()
        val pillRadius = CornerRadius(pillShort / 2f, pillShort / 2f)

        // Midpoint 4: Top (Horizontal pill)
        drawPillHandle(
            center = midTop,
            size = Size(pillLong, pillShort),
            cornerRadius = pillRadius,
            isActive = (activeHandleIndex == 4),
            isSnapped = wasSnapped,
            primaryColor = primaryColor
        )

        // Midpoint 5: Right (Vertical pill)
        drawPillHandle(
            center = midRight,
            size = Size(pillShort, pillLong),
            cornerRadius = pillRadius,
            isActive = (activeHandleIndex == 5),
            isSnapped = wasSnapped,
            primaryColor = primaryColor
        )

        // Midpoint 6: Bottom (Horizontal pill)
        drawPillHandle(
            center = midBottom,
            size = Size(pillLong, pillShort),
            cornerRadius = pillRadius,
            isActive = (activeHandleIndex == 6),
            isSnapped = wasSnapped,
            primaryColor = primaryColor
        )

        // Midpoint 7: Left (Vertical pill)
        drawPillHandle(
            center = midLeft,
            size = Size(pillShort, pillLong),
            cornerRadius = pillRadius,
            isActive = (activeHandleIndex == 7),
            isSnapped = wasSnapped,
            primaryColor = primaryColor
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPillHandle(
    center: Offset,
    size: Size,
    cornerRadius: CornerRadius,
    isActive: Boolean,
    isSnapped: Boolean,
    primaryColor: Color
) {
    val topLeft = Offset(center.x - size.width / 2f, center.y - size.height / 2f)
    val ringColor = if (isActive && isSnapped) Color(0xFF00E5FF) else primaryColor

    // Shadow
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.35f),
        topLeft = Offset(topLeft.x, topLeft.y + 1.dp.toPx()),
        size = size,
        cornerRadius = cornerRadius
    )
    // White background
    drawRoundRect(
        color = Color.White,
        topLeft = topLeft,
        size = size,
        cornerRadius = cornerRadius
    )
    // Colored border
    drawRoundRect(
        color = ringColor,
        topLeft = topLeft,
        size = size,
        cornerRadius = cornerRadius,
        style = Stroke(width = 1.6.dp.toPx())
    )
    // Small center accent bar/dot
    val isHorizontal = size.width > size.height
    if (isHorizontal) {
        drawLine(
            color = ringColor,
            start = Offset(center.x - 4.dp.toPx(), center.y),
            end = Offset(center.x + 4.dp.toPx(), center.y),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
    } else {
        drawLine(
            color = ringColor,
            start = Offset(center.x, center.y - 4.dp.toPx()),
            end = Offset(center.x, center.y + 4.dp.toPx()),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}
