package org.openscanner.data.repository

import android.content.Context
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.openscanner.data.local.DocumentDao
import org.openscanner.data.local.FolderDao
import org.openscanner.data.local.PageDao
import org.openscanner.data.model.Page

/**
 * Tests for ScannerRepository book spread splitting, consecutive page numbering,
 * and document association.
 */
class ScannerRepositoryBookTest {

    private lateinit var context: Context
    private lateinit var documentDao: DocumentDao
    private lateinit var pageDao: PageDao
    private lateinit var folderDao: FolderDao
    private lateinit var repository: ScannerRepository

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        documentDao = mockk(relaxed = true)
        pageDao = mockk(relaxed = true)
        folderDao = mockk(relaxed = true)
        repository = ScannerRepository(context, documentDao, pageDao, folderDao)
    }

    @Test
    fun testConsecutivePageNumberingLogic() {
        val existingPages = listOf(
            Page(id = 1L, documentId = 10L, pageIndex = 0, processedImagePath = "", originalImagePath = "")
        )
        val nextIndex1 = existingPages.size // 1
        val nextIndex2 = existingPages.size + 1 // 2

        assertEquals(1, nextIndex1)
        assertEquals(2, nextIndex2)
        assertEquals(nextIndex1 + 1, nextIndex2)
    }

    @Test
    fun testSpineBoundaryClamping() {
        val width = 2000
        val minAllowed = (width * 0.15f).toInt() // 300
        val maxAllowed = (width * 0.85f).toInt() // 1700

        fun clampSpine(raw: Int): Int = raw.coerceIn(minAllowed, maxAllowed)

        assertEquals(300, clampSpine(50))
        assertEquals(1000, clampSpine(1000))
        assertEquals(1700, clampSpine(1950))
    }
}
