package org.openscanner.cv

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import org.openscanner.ink.InkStrokeManager

class ThorvgAndInkTest {

    @Test
    fun testThorvgShapeTypeEnumIntegrity() {
        assertEquals(0, ThorvgEngine.ShapeType.RECTANGLE.id)
        assertEquals(1, ThorvgEngine.ShapeType.FILLED_BOX.id)
        assertEquals(2, ThorvgEngine.ShapeType.CIRCLE.id)
        assertEquals(3, ThorvgEngine.ShapeType.LINE.id)
        assertEquals(4, ThorvgEngine.ShapeType.ARROW.id)
    }

    @Test
    fun testInkFlatPointsConversion() {
        val points = listOf(
            Offset(10f, 20f),
            Offset(30f, 40f),
            Offset(50f, 60f)
        )
        val flat = InkStrokeManager.toFlatPoints(points)
        assertEquals(6, flat.size)
        assertEquals(10f, flat[0], 0.001f)
        assertEquals(20f, flat[1], 0.001f)
        assertEquals(30f, flat[2], 0.001f)
        assertEquals(40f, flat[3], 0.001f)
        assertEquals(50f, flat[4], 0.001f)
        assertEquals(60f, flat[5], 0.001f)
    }

    @Test
    fun testInkBrushCreationOnAndroidRuntime() {
        try {
            val penBrush = InkStrokeManager.createPenBrush(6f, Color(0xFF00E676))
            assertNotNull(penBrush)
            assertEquals(6f, penBrush.size, 0.001f)
        } catch (_: UnsatisfiedLinkError) {
            // Expected on host JVM tests where Android AAR JNI binaries are not loaded
        }
    }
}
