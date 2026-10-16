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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.openscanner.ui.theme.OkenGreen

@Composable
fun MagnifierLoupe(
    bitmap: Bitmap?,
    touchPoint: Offset?,
    imageScale: Float,
    fitRect: android.graphics.RectF? = null,
    modifier: Modifier = Modifier
) {
    if (touchPoint == null || bitmap == null || imageScale <= 0f) return

    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val loupeSize = 110.dp
    val zoomFactor = 2.0f

    // Position loupe above the finger, flipping below if near top
    val loupeSizePx = 300f
    val posX = (touchPoint.x - loupeSizePx / 2f).toInt().coerceAtLeast(16)
    val posY = if (touchPoint.y > 350f) {
        (touchPoint.y - 280f).toInt()
    } else {
        (touchPoint.y + 70f).toInt()
    }

    Box(
        modifier = modifier
            .offset { IntOffset(posX, posY) }
            .size(loupeSize)
            .clip(CircleShape)
            .border(3.dp, OkenGreen, CircleShape)
    ) {
        Canvas(modifier = Modifier.size(loupeSize)) {
            val offX = fitRect?.left ?: 0f
            val offY = fitRect?.top ?: 0f
            val bmpX = ((touchPoint.x - offX) / imageScale)
            val bmpY = ((touchPoint.y - offY) / imageScale)

            val loupeRadiusPx = size.width / 2f
            val sampleRadius = (loupeRadiusPx / zoomFactor).toInt()

            val srcLeft = (bmpX - sampleRadius).toInt().coerceIn(0, bitmap.width)
            val srcTop = (bmpY - sampleRadius).toInt().coerceIn(0, bitmap.height)
            val srcW = (sampleRadius * 2).coerceAtMost(bitmap.width - srcLeft)
            val srcH = (sampleRadius * 2).coerceAtMost(bitmap.height - srcTop)

            if (srcW > 0 && srcH > 0) {
                // Zero-allocation subregion drawing
                drawImage(
                    image = imageBitmap,
                    srcOffset = IntOffset(srcLeft, srcTop),
                    srcSize = IntSize(srcW, srcH),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(size.width.toInt(), size.height.toInt())
                )
            }

            // Draw center crosshair
            val c = Offset(size.width / 2f, size.height / 2f)
            drawLine(OkenGreen, Offset(c.x - 16f, c.y), Offset(c.x + 16f, c.y), strokeWidth = 2.5f)
            drawLine(OkenGreen, Offset(c.x, c.y - 16f), Offset(c.x, c.y + 16f), strokeWidth = 2.5f)
        }
    }
}
