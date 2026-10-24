package org.openscanner.ui

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.openscanner.OpenScannerApp
import org.openscanner.data.local.AppDatabase
import org.openscanner.data.local.DocumentDao
import org.openscanner.data.local.FolderDao
import org.openscanner.data.local.PageDao
import org.openscanner.data.model.Document
import org.openscanner.data.model.Folder
import org.openscanner.data.model.Page
import org.openscanner.ui.detail.DocumentDetailViewModel

/**
 * Tier 1, Tier 2, Tier 3: Tests for DocumentDetailViewModel page management and document actions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DocumentDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var app: OpenScannerApp
    private lateinit var db: AppDatabase
    private lateinit var documentDao: DocumentDao
    private lateinit var pageDao: PageDao
    private lateinit var folderDao: FolderDao
    private lateinit var viewModel: DocumentDetailViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        documentDao = mockk(relaxed = true)
        pageDao = mockk(relaxed = true)
        folderDao = mockk(relaxed = true)
        db = mockk(relaxed = true) {
            every { documentDao() } returns this@DocumentDetailViewModelTest.documentDao
            every { pageDao() } returns this@DocumentDetailViewModelTest.pageDao
            every { folderDao() } returns this@DocumentDetailViewModelTest.folderDao
        }

        app = mockk(relaxed = true) {
            every { database } returns this@DocumentDetailViewModelTest.db
        }

        viewModel = DocumentDetailViewModel(app)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testLoadDocumentAndPages() {
        val docId = 44L
        val doc = Document(id = docId, title = "Lease Agreement", pageCount = 2)
        val pages = listOf(
            Page(id = 1L, documentId = docId, pageIndex = 0, processedImagePath = "/p1.jpg", originalImagePath = "/o1.jpg"),
            Page(id = 2L, documentId = docId, pageIndex = 1, processedImagePath = "/p2.jpg", originalImagePath = "/o2.jpg")
        )
        val folders = listOf(Folder(id = 1L, name = "Real Estate"))

        coEvery { documentDao.getDocumentById(docId) } returns doc
        every { pageDao.getPagesForDocument(docId) } returns flowOf(pages)
        every { folderDao.getAllFolders() } returns flowOf(folders)

        viewModel.load(docId)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(doc, viewModel.document.value)
        assertEquals(2, viewModel.pages.value.size)
        assertEquals(1, viewModel.folders.value.size)
        assertFalse(viewModel.isGeneratingPdf.value)
    }

    @Test
    fun testRenameDocument() = runTest {
        val docId = 44L
        val doc = Document(id = docId, title = "Original Name")
        coEvery { documentDao.getDocumentById(docId) } returns doc
        every { pageDao.getPagesForDocument(docId) } returns flowOf(emptyList())
        every { folderDao.getAllFolders() } returns flowOf(emptyList())

        viewModel.load(docId)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.renameDocument("Updated Contract Name")
        testDispatcher.scheduler.advanceUntilIdle()
        // Wait briefly for withContext(Dispatchers.IO) on background thread
        var count = 0
        while (viewModel.document.value?.title != "Updated Contract Name" && count < 20) {
            Thread.sleep(20)
            testDispatcher.scheduler.advanceUntilIdle()
            count++
        }

        assertEquals("Updated Contract Name", viewModel.document.value?.title)
        coVerify(exactly = 1) { documentDao.updateDocument(match { it.title == "Updated Contract Name" }) }
    }

    @Test
    fun testMoveToFolder() = runTest {
        val docId = 44L
        val doc = Document(id = docId, title = "Doc", folderId = -1L)
        val updatedDoc = doc.copy(folderId = 12L)

        coEvery { documentDao.getDocumentById(docId) } returns doc andThen updatedDoc
        every { pageDao.getPagesForDocument(docId) } returns flowOf(emptyList())
        every { folderDao.getAllFolders() } returns flowOf(emptyList())

        viewModel.load(docId)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.moveToFolder(12L)
        testDispatcher.scheduler.advanceUntilIdle()

        var count = 0
        while (viewModel.document.value?.folderId != 12L && count < 20) {
            Thread.sleep(20)
            testDispatcher.scheduler.advanceUntilIdle()
            count++
        }

        coVerify(exactly = 1) { documentDao.updateDocumentFolder(docId, 12L) }
        assertEquals(12L, viewModel.document.value?.folderId)
    }
}
