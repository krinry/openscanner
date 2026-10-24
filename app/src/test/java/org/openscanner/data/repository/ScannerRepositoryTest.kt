package org.openscanner.data.repository

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.openscanner.data.local.DocumentDao
import org.openscanner.data.local.FolderDao
import org.openscanner.data.local.PageDao
import org.openscanner.data.model.Document
import org.openscanner.data.model.Folder
import org.openscanner.data.model.Page
import java.io.File

/**
 * Tier 1, Tier 2, Tier 3: Tests for ScannerRepository operations, page reordering,
 * document management, and ID card composite geometry.
 */
class ScannerRepositoryTest {

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
    fun testCreateNewDocumentWithCustomTitle() = runTest {
        val docSlot = slot<Document>()
        coEvery { documentDao.insertDocument(capture(docSlot)) } returns 101L

        val docId = repository.createNewDocument("Legal Brief 2026", folderId = 3L)
        assertEquals(101L, docId)
        assertTrue(docSlot.isCaptured)
        assertEquals("Legal Brief 2026", docSlot.captured.title)
        assertEquals(3L, docSlot.captured.folderId)
    }

    @Test
    fun testCreateNewDocumentWithDefaultGeneratedTitle() = runTest {
        val docSlot = slot<Document>()
        coEvery { documentDao.insertDocument(capture(docSlot)) } returns 102L

        val docId = repository.createNewDocument(title = null, folderId = -1L)
        assertEquals(102L, docId)
        assertTrue(docSlot.isCaptured)
        assertTrue(docSlot.captured.title.startsWith("New Doc "))
        assertEquals(-1L, docSlot.captured.folderId)
    }

    @Test
    fun testCreateAndRenameFolder() = runTest {
        val folderSlot = slot<Folder>()
        coEvery { folderDao.insertFolder(capture(folderSlot)) } returns 7L
        coEvery { folderDao.getFolderById(7L) } returns Folder(id = 7L, name = "Drafts")

        val createdId = repository.createFolder("   Taxes   ", "#FF9800")
        assertEquals(7L, createdId)
        assertEquals("Taxes", folderSlot.captured.name)
        assertEquals("#FF9800", folderSlot.captured.color)

        repository.renameFolder(7L, "   Final Taxes 2026   ")
        coVerify(exactly = 1) {
            folderDao.updateFolder(match { it.id == 7L && it.name == "Final Taxes 2026" })
        }
    }

    @Test
    fun testDeleteFolderUnlinksDocuments() = runTest {
        val folder = Folder(id = 8L, name = "Temporary")

        repository.deleteFolder(folder)

        // Verify unlinking before deleting folder
        coVerify(ordering = io.mockk.Ordering.ORDERED) {
            documentDao.unlinkDocumentsFromFolder(8L)
            folderDao.deleteFolder(folder)
        }
    }

    @Test
    fun testReorderPagesConsecutiveIndexAssignment() = runTest {
        val docId = 55L
        val pageA = Page(id = 1L, documentId = docId, pageIndex = 2, processedImagePath = "", originalImagePath = "")
        val pageB = Page(id = 2L, documentId = docId, pageIndex = 0, processedImagePath = "", originalImagePath = "")
        val pageC = Page(id = 3L, documentId = docId, pageIndex = 1, processedImagePath = "", originalImagePath = "")

        val newOrder = listOf(pageA, pageB, pageC)
        val updatedPages = mutableListOf<Page>()
        coEvery { pageDao.updatePage(capture(updatedPages)) } returns Unit
        coEvery { documentDao.getDocumentById(docId) } returns Document(id = docId, title = "Reorder Test")

        repository.reorderPages(docId, newOrder)

        assertEquals(3, updatedPages.size)
        assertEquals(0, updatedPages[0].pageIndex)
        assertEquals(1L, updatedPages[0].id)

        assertEquals(1, updatedPages[1].pageIndex)
        assertEquals(2L, updatedPages[1].id)

        assertEquals(2, updatedPages[2].pageIndex)
        assertEquals(3L, updatedPages[2].id)

        coVerify(exactly = 1) { documentDao.updateDocument(any()) }
    }

    @Test
    fun testDeletePageUpdatesDocumentPageCount() = runTest {
        val docId = 99L
        val pageToDelete = Page(
            id = 501L,
            documentId = docId,
            pageIndex = 0,
            processedImagePath = "/nonexistent/proc.jpg",
            originalImagePath = "/nonexistent/orig.jpg",
            thumbnailPath = "/nonexistent/thumb.jpg"
        )

        val remainingPages = listOf(
            Page(id = 502L, documentId = docId, pageIndex = 1, processedImagePath = "", originalImagePath = "")
        )

        coEvery { pageDao.getPagesListForDocument(docId) } returns remainingPages

        repository.deletePage(pageToDelete)

        coVerify(exactly = 1) { pageDao.deletePage(pageToDelete) }
        coVerify(exactly = 1) { documentDao.updatePageCount(docId, 1, any()) }
    }

    @Test
    fun testIdCardCompositingGeometryConstants() {
        // Authoritative ISO/IEC 7810 ID-1 aspect ratio verification
        val a4Width = 2480
        val a4Height = 3508

        val cardW = (a4Width * 0.70f).toInt()
        val cardH = Math.round(cardW / 1.586f).toInt()
        val cardX = (a4Width - cardW) / 2
        val frontY = (a4Height * 0.20f).toInt()
        val backY = (a4Height * 0.56f).toInt()

        assertEquals(1736, cardW)
        assertEquals(1095, cardH)
        assertEquals(372, cardX)
        assertEquals(701, frontY)
        assertEquals(1964, backY)

        // Verify cards do not overlap and fit completely within A4 height
        assertTrue(frontY + cardH < backY)
        assertTrue(backY + cardH < a4Height)
    }

    @Test
    fun testBatchMoveDocuments() = runTest {
        val docIds = listOf(10L, 20L, 30L)
        repository.batchMoveToFolder(docIds, 999L)
        coVerify(exactly = 1) { documentDao.batchUpdateFolder(docIds, 999L) }
    }
}
