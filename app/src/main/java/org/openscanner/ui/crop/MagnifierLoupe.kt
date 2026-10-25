package org.openscanner.ui.crop

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.openscanner.ui.theme.ScannerGreen

@Composable
fun MagnifierLoupe(
    bitmap: Bitmap?,
    touchPoint: Offset?,
    imageScale: Float,
    fitRect: android.graphics.RectF? = null,
    handlePoint: Offset? = null,
    modifier: Modifier = Modifier
) {
    val activeTarget = handlePoint ?: touchPoint
    if (activeTarget == null || bitmap == null || imageScale <= 0f) return

    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val density = LocalDensity.current
    val config = LocalConfiguration.current

    val loupeSize = 120.dp
    val zoomFactor = 2.2f

    val loupeSizePx = with(density) { loupeSize.toPx() }
    val screenWidthPx = with(density) { config.screenWidthDp.dp.toPx() }

    // Position loupe comfortably above the finger contact point
    val anchorTouch = touchPoint ?: activeTarget
    val posX = (anchorTouch.x - loupeSizePx / 2f).coerceIn(16f, (screenWidthPx - loupeSizePx - 16f).coerceAtLeast(16f)).toInt()
    val posY = if (anchorTouch.y > loupeSizePx + 80f) {
        (anchorTouch.y - loupeSizePx - 50f).toInt()
    } else {
        (anchorTouch.y + 70f).toInt()
    }

    Box(
        modifier = modifier
            .offset { IntOffset(posX, posY) }
            .size(loupeSize)
            .clip(CircleShape)
            .border(3.dp, ScannerGreen, CircleShape)
    ) {
        Canvas(modifier = Modifier.size(loupeSize)) {
            // Circular aperture clip
            clipPath(Path().apply { addOval(Rect(Offset.Zero, size)) }) {
                drawRect(Color(0xFF141414))

                val offX = fitRect?.left ?: 0f
                val offY = fitRect?.top ?: 0f

                // Center exactly on activeTarget (handle/vertex coordinates)
                translate(
                    left = size.width / 2f - (activeTarget.x - offX) * zoomFactor,
                    top = size.height / 2f - (activeTarget.y - offY) * zoomFactor
                ) {
                    scale(
                        scaleX = zoomFactor * imageScale,
                        scaleY = zoomFactor * imageScale,
                        pivot = Offset.Zero
                    ) {
                        drawImage(image = imageBitmap)
                    }
                }
            }

            // High-precision targeting reticle exactly at center of loupe
            val c = Offset(size.width / 2f, size.height / 2f)
            // Outer shadow / glow
            drawLine(Color.Black.copy(alpha = 0.5f), Offset(c.x - 22f, c.y), Offset(c.x + 22f, c.y), strokeWidth = 4f)
            drawLine(Color.Black.copy(alpha = 0.5f), Offset(c.x, c.y - 22f), Offset(c.x, c.y + 22f), strokeWidth = 4f)

            // Primary crosshairs
            drawLine(Color.White, Offset(c.x - 20f, c.y), Offset(c.x + 20f, c.y), strokeWidth = 2.5f)
            drawLine(Color.White, Offset(c.x, c.y - 20f), Offset(c.x, c.y + 20f), strokeWidth = 2.5f)
            drawLine(ScannerGreen, Offset(c.x - 14f, c.y), Offset(c.x + 14f, c.y), strokeWidth = 1.5f)
            drawLine(ScannerGreen, Offset(c.x, c.y - 14f), Offset(c.x, c.y + 14f), strokeWidth = 1.5f)

            // Center micro-dot
            drawCircle(Color.White, radius = 3.5f, center = c)
            drawCircle(ScannerGreen, radius = 2f, center = c)
        }
    }
}
