package org.openscanner.ui.crop

import android.graphics.Bitmap
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import kotlin.math.roundToInt

/**
 * High-precision Magnifier Loupe (Zoom Preview) for document crop handles.
 *
 * Emulates the HUD corner evasion logic of CamScanner/OKEN Scanner:
 * The loupe is parked in the Top-Left HUD corner by default. When the user's
 * finger approaches the top-left area, the loupe smoothly flips to the Top-Right
 * HUD corner, guaranteeing that the user's hand and fingers NEVER occlude the loupe.
 */
@Composable
fun MagnifierLoupe(
    bitmap: Bitmap?,
    touchPoint: Offset?,
    imageScale: Float,
    fitRect: android.graphics.RectF? = null,
    handlePoint: Offset? = null,
    isSnapped: Boolean = false,
    modifier: Modifier = Modifier
) {
    val activeTarget = handlePoint ?: touchPoint
    if (activeTarget == null || bitmap == null || imageScale <= 0f) return

    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val density = LocalDensity.current
    val config = LocalConfiguration.current

    val loupeSize = 124.dp
    val zoomFactor = 2.2f

    val loupeSizePx = with(density) { loupeSize.toPx() }
    val screenWidthPx = with(density) { config.screenWidthDp.dp.toPx() }
    val radiusPx = loupeSizePx / 2f
    val marginPx = with(density) { 16.dp.toPx() }

    // Evasion threshold: 3 * radius from top-left (OKEN Scanner b(x, y) formula)
    val evasionThresholdX = 3f * radiusPx + marginPx
    val evasionThresholdY = 3f * radiusPx + marginPx

    val anchor = touchPoint ?: activeTarget
    val isNearTopLeft = anchor.x < evasionThresholdX && anchor.y < evasionThresholdY

    // Target X position in HUD: Top-Left (marginPx) vs Top-Right (screenWidth - size - margin)
    val targetPosX = if (isNearTopLeft) {
        (screenWidthPx - loupeSizePx - marginPx).coerceAtLeast(marginPx)
    } else {
        marginPx
    }
    val targetPosY = marginPx

    // Smooth spring animation when flipping between top-left and top-right HUD corners
    val animatedPosX by animateFloatAsState(
        targetValue = targetPosX,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "loupePosX"
    )

    val reticleColor = if (isSnapped) Color(0xFF00E5FF) else ScannerGreen
    val borderColor = if (isSnapped) Color(0xFF00E5FF) else ScannerGreen

    Box(
        modifier = modifier
            .offset { IntOffset(animatedPosX.roundToInt(), targetPosY.roundToInt()) }
            .size(loupeSize)
            .shadow(12.dp, CircleShape, spotColor = Color.Black.copy(alpha = 0.6f))
            .clip(CircleShape)
            .border(3.dp, borderColor, CircleShape)
    ) {
        Canvas(modifier = Modifier.size(loupeSize)) {
            // Circular lens aperture clip
            clipPath(Path().apply { addOval(Rect(Offset.Zero, size)) }) {
                drawRect(Color(0xFF121212))

                val offX = fitRect?.left ?: 0f
                val offY = fitRect?.top ?: 0f

                // Center exactly on activeTarget vertex coordinates
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

            // High-precision targeting reticle exactly at center of lens
            val c = Offset(size.width / 2f, size.height / 2f)

            // Outer dark drop-shadow for crosshairs (readable on both white paper & dark backgrounds)
            drawLine(Color.Black.copy(alpha = 0.65f), Offset(c.x - 24f, c.y), Offset(c.x + 24f, c.y), strokeWidth = 4f)
            drawLine(Color.Black.copy(alpha = 0.65f), Offset(c.x, c.y - 24f), Offset(c.x, c.y + 24f), strokeWidth = 4f)

            // Primary crisp white crosshairs
            drawLine(Color.White, Offset(c.x - 22f, c.y), Offset(c.x + 22f, c.y), strokeWidth = 2.5f)
            drawLine(Color.White, Offset(c.x, c.y - 22f), Offset(c.x, c.y + 22f), strokeWidth = 2.5f)

            // Accent inner crosshair lines
            drawLine(reticleColor, Offset(c.x - 14f, c.y), Offset(c.x + 14f, c.y), strokeWidth = 1.8f)
            drawLine(reticleColor, Offset(c.x, c.y - 14f), Offset(c.x, c.y + 14f), strokeWidth = 1.8f)

            // Center targeting micro-dots
            drawCircle(Color.White, radius = 3.5f, center = c)
            drawCircle(reticleColor, radius = 2f, center = c)

            // Magnetic snap lock ring
            if (isSnapped) {
                drawCircle(Color(0xFF00E5FF).copy(alpha = 0.35f), radius = 12f, center = c)
                drawCircle(Color(0xFF00E5FF).copy(alpha = 0.7f), radius = 12f, center = c, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
            }
        }
    }
}
