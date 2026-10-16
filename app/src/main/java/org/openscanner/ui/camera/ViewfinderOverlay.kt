package org.openscanner.ui.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import org.openscanner.camera.CaptureMode
import org.openscanner.cv.DocumentQuadrilateral
import org.openscanner.ui.theme.OkenGreen

@Composable
fun ViewfinderOverlay(
    mode: CaptureMode,
    quad: DocumentQuadrilateral?,
    previewWidth: Int,
    previewHeight: Int,
    showGrid: Boolean = false,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        // Draw 3x3 Rule of Thirds Grid if enabled
        if (showGrid) {
            val gridColor = Color.White.copy(alpha = 0.25f)
            val strokeW = 1.dp.toPx()
            drawLine(gridColor, Offset(size.width / 3f, 0f), Offset(size.width / 3f, size.height), strokeW)
            drawLine(gridColor, Offset(size.width * 2 / 3f, 0f), Offset(size.width * 2 / 3f, size.height), strokeW)
            drawLine(gridColor, Offset(0f, size.height / 3f), Offset(size.width, size.height / 3f), strokeW)
            drawLine(gridColor, Offset(0f, size.height * 2 / 3f), Offset(size.width, size.height * 2 / 3f), strokeW)
        }

        // Mode specific guides
        when (mode) {
            CaptureMode.ID_CARD -> {
                // ISO/IEC 7810 ID-1 card aspect ratio is 1.586
                val cardW = size.width * 0.88f
                val cardH = cardW / 1.586f
                val cardX = (size.width - cardW) / 2f
                val cardY = (size.height - cardH) / 2f - 40.dp.toPx()

                val cardRect = RoundRect(
                    rect = Rect(cardX, cardY, cardX + cardW, cardY + cardH),
                    cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx())
                )
                val cardPath = Path().apply { addRoundRect(cardRect) }

                // Translucent dimming outside card
                drawPath(cardPath, color = OkenGreen.copy(alpha = 0.12f))
                drawPath(cardPath, color = OkenGreen, style = Stroke(width = 2.5.dp.toPx()))

                // ID card corner guides
                val bracketLen = 28.dp.toPx()
                fun drawBracket(x: Float, y: Float, dx: Float, dy: Float) {
                    val p = Path().apply {
                        moveTo(x + dx * bracketLen, y)
                        lineTo(x, y)
                        lineTo(x, y + dy * bracketLen)
                    }
                    drawPath(p, color = Color.White, style = Stroke(width = 4.dp.toPx()))
                    drawPath(p, color = OkenGreen, style = Stroke(width = 2.dp.toPx()))
                }
                drawBracket(cardX, cardY, 1f, 1f)
                drawBracket(cardX + cardW, cardY, -1f, 1f)
                drawBracket(cardX + cardW, cardY + cardH, -1f, -1f)
                drawBracket(cardX, cardY + cardH, 1f, -1f)
            }

            CaptureMode.QR_CODE -> {
                val boxSize = size.width * 0.70f
                val boxX = (size.width - boxSize) / 2f
                val boxY = (size.height - boxSize) / 2f - 40.dp.toPx()

                val p = Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = Rect(boxX, boxY, boxX + boxSize, boxY + boxSize),
                            cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
                        )
                    )
                }
                drawPath(p, color = OkenGreen.copy(alpha = 0.10f))
                drawPath(p, color = OkenGreen, style = Stroke(width = 2.5.dp.toPx()))
            }

            else -> {
                // Dynamic Document Detection for Single, Batch, Book modes
                if (quad != null && previewWidth > 0 && previewHeight > 0) {
                    val scaleX = size.width / previewWidth
                    val scaleY = size.height / previewHeight

                    val p1 = Offset(quad.topLeft.x * scaleX, quad.topLeft.y * scaleY)
                    val p2 = Offset(quad.topRight.x * scaleX, quad.topRight.y * scaleY)
                    val p3 = Offset(quad.bottomRight.x * scaleX, quad.bottomRight.y * scaleY)
                    val p4 = Offset(quad.bottomLeft.x * scaleX, quad.bottomLeft.y * scaleY)

                    val polyPath = Path().apply {
                        moveTo(p1.x, p1.y)
                        lineTo(p2.x, p2.y)
                        lineTo(p3.x, p3.y)
                        lineTo(p4.x, p4.y)
                        close()
                    }

                    // Translucent green tint over detected document
                    drawPath(polyPath, color = OkenGreen.copy(alpha = 0.18f))
                    // Green boundary outline
                    drawPath(polyPath, color = OkenGreen, style = Stroke(width = 2.5.dp.toPx()))

                    // CamScanner signature L-shaped corner brackets
                    val bracketLen = 22.dp.toPx()
                    val bracketStroke = Stroke(width = 3.5.dp.toPx())

                    fun drawCornerBracket(corner: Offset, dirX: Float, dirY: Float) {
                        val path = Path().apply {
                            moveTo(corner.x + dirX * bracketLen, corner.y)
                            lineTo(corner.x, corner.y)
                            lineTo(corner.x, corner.y + dirY * bracketLen)
                        }
                        drawPath(path, color = Color.White, style = bracketStroke)
                        drawPath(path, color = OkenGreen, style = Stroke(width = 2.dp.toPx()))
                    }

                    drawCornerBracket(p1, 1f, 1f)   // Top-Left
                    drawCornerBracket(p2, -1f, 1f)  // Top-Right
                    drawCornerBracket(p3, -1f, -1f) // Bottom-Right
                    drawCornerBracket(p4, 1f, -1f)  // Bottom-Left
                }
            }
        }
    }
}
