package org.openscanner.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tier 1 & Tier 2: Tests for camera capture modes.
 */
class CaptureModeTest {

    @Test
    fun testAllCaptureModesExist() {
        val modes = CaptureMode.values()
        assertEquals(5, modes.size)
        assertTrue(modes.contains(CaptureMode.SINGLE))
        assertTrue(modes.contains(CaptureMode.BATCH))
        assertTrue(modes.contains(CaptureMode.ID_CARD))
        assertTrue(modes.contains(CaptureMode.QR_CODE))
        assertTrue(modes.contains(CaptureMode.BOOK))
    }

    @Test
    fun testCaptureModeTitlesAndDescriptions() {
        assertEquals("Single", CaptureMode.SINGLE.title)
        assertEquals("Single document page with auto-crop", CaptureMode.SINGLE.description)

        assertEquals("Batch", CaptureMode.BATCH.title)
        assertEquals("Continuous scanning for multi-page documents", CaptureMode.BATCH.description)

        assertEquals("ID Card", CaptureMode.ID_CARD.title)
        assertEquals("Front & Back card sides combined onto one page", CaptureMode.ID_CARD.description)

        assertEquals("QR Code", CaptureMode.QR_CODE.title)
        assertEquals("Fast QR and Barcode scanner", CaptureMode.QR_CODE.description)

        assertEquals("Book", CaptureMode.BOOK.title)
        assertEquals("Two-page spread with curve correction", CaptureMode.BOOK.description)
    }

    @Test
    fun testCaptureModeValueOf() {
        assertEquals(CaptureMode.SINGLE, CaptureMode.valueOf("SINGLE"))
        assertEquals(CaptureMode.BATCH, CaptureMode.valueOf("BATCH"))
        assertEquals(CaptureMode.ID_CARD, CaptureMode.valueOf("ID_CARD"))
        assertEquals(CaptureMode.QR_CODE, CaptureMode.valueOf("QR_CODE"))
        assertEquals(CaptureMode.BOOK, CaptureMode.valueOf("BOOK"))
    }
}
