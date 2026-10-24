package org.openscanner.data

import android.content.Context
import io.mockk.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.openscanner.data.local.DocumentDao
import org.openscanner.data.local.FolderDao
import org.openscanner.data.local.PageDao
import org.openscanner.data.model.Document
import org.openscanner.data.model.Folder
import org.openscanner.data.model.Page
import org.openscanner.data.repository.ScannerRepository
import java.io.File
import java.io.IOException

class FolderHierarchyTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var documentDao: DocumentDao
    private lateinit var pageDao: PageDao
    private lateinit var folderDao: FolderDao
    private lateinit var repository: ScannerRepository

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        every { context.filesDir } returns tempFolder.root
        documentDao = mockk(relaxed = true)
        pageDao = mockk(relaxed = true)
        folderDao = mockk(relaxed = true)
        repository = ScannerRepository(context, documentDao, pageDao, folderDao)
    }

    @Test
    fun testCreateRootFolderGeneratesRootPath() = runTest {
        val folderSlot = slot<Folder>()
        coEvery { folderDao.insertFolder(capture(folderSlot)) } returns 1L

        val folderId = repository.createFolder("Work", parentFolderId = -1L, color = "#4CAF50")
        assertEquals(1L, folderId)
        assertTrue(folderSlot.isCaptured)
        val created = folderSlot.captured
        assertEquals("Work", created.name)
        assertEquals(-1L, created.parentFolderId)
        assertEquals("/", created.path)
        assertEquals("#4CAF50", created.color)
    }

    @Test
    fun testCreateSubfolderGeneratesHierarchicalPath() = runTest {
        val parentFolder = Folder(id = 10L, name = "Finance", path = "/10/", parentFolderId = -1L)
        coEvery { folderDao.getFolderById(10L) } returns parentFolder

        val childSlot = slot<Folder>()
        coEvery { folderDao.insertFolder(capture(childSlot)) } returns 25L

        val childId = repository.createFolder("Invoices", parentFolderId = 10L, color = "#2196F3")
        assertEquals(25L, childId)
        assertTrue(childSlot.isCaptured)
        val child = childSlot.captured
        assertEquals("Invoices", child.name)
        assertEquals(10L, child.parentFolderId)
        assertEquals("/10/10/", child.path)
    }

    @Test
    fun testGetRootFoldersAndSubfoldersFlows() = runTest {
        val rootFolders = listOf(
            Folder(id = 1L, name = "Root 1", parentFolderId = -1L, path = "/"),
            Folder(id = 2L, name = "Root 2", parentFolderId = -1L, path = "/")
        )
        val subFolders = listOf(
            Folder(id = 3L, name = "Sub 1", parentFolderId = 1L, path = "/1/")
        )

        every { folderDao.getRootFolders() } returns flowOf(rootFolders)
        every { folderDao.getSubfolders(1L) } returns flowOf(subFolders)

        val retrievedRoots = repository.getRootFolders().first()
        assertEquals(2, retrievedRoots.size)
        assertEquals(1L, retrievedRoots[0].id)
        assertEquals(-1L, retrievedRoots[0].parentFolderId)

        val retrievedSubs = repository.getSubfolders(1L).first()
        assertEquals(1, retrievedSubs.size)
        assertEquals(3L, retrievedSubs[0].id)
        assertEquals(1L, retrievedSubs[0].parentFolderId)
    }

    @Test
    fun testGetBreadcrumbsTraversal() = runTest {
        val rootFolder = Folder(id = 1L, name = "Root", parentFolderId = -1L, path = "/")
        val middleFolder = Folder(id = 2L, name = "Mid", parentFolderId = 1L, path = "/1/")
        val leafFolder = Folder(id = 3L, name = "Leaf", parentFolderId = 2L, path = "/1/2/")

        coEvery { folderDao.getFolderById(3L) } returns leafFolder
        coEvery { folderDao.getFolderById(2L) } returns middleFolder
        coEvery { folderDao.getFolderById(1L) } returns rootFolder

        val trail = repository.getBreadcrumbs(3L)
        assertEquals(3, trail.size)
        assertEquals("Root", trail[0].name)
        assertEquals("Mid", trail[1].name)
        assertEquals("Leaf", trail[2].name)
    }

    @Test
    fun testMoveFolderUpdatesPathAndDescendants() = runTest {
        val sourceFolder = Folder(id = 5L, name = "Docs", parentFolderId = 1L, path = "/1/")
        val targetParent = Folder(id = 20L, name = "Archive", parentFolderId = -1L, path = "/")

        coEvery { folderDao.getFolderById(5L) } returns sourceFolder
        coEvery { folderDao.getFolderById(20L) } returns targetParent
        coEvery { folderDao.updateFolder(any()) } just Runs
        coEvery { folderDao.updateFolderPathPrefix(any(), any()) } just Runs

        repository.moveFolder(folderId = 5L, targetParentFolderId = 20L)

        // Verify folder updated with new parent and path
        coVerify(exactly = 1) {
            folderDao.updateFolder(match {
                it.id == 5L && it.parentFolderId == 20L && it.path == "/20/"
            })
        }

        // Verify descendant paths updated
        coVerify(exactly = 1) {
            folderDao.updateFolderPathPrefix(
                oldPrefix = "/1/5/",
                newPrefix = "/20/5/"
            )
        }
    }

    @Test
    fun testMoveFolderCircularPreventionThrowsException() = runTest {
        val folderA = Folder(id = 10L, name = "Folder A", parentFolderId = -1L, path = "/")
        val folderB = Folder(id = 11L, name = "Folder B", parentFolderId = 10L, path = "/10/")

        coEvery { folderDao.getFolderById(10L) } returns folderA
        coEvery { folderDao.getFolderById(11L) } returns folderB

        // Attempt to move Folder A into its own child (Folder B)
        try {
            repository.moveFolder(folderId = 10L, targetParentFolderId = 11L)
            fail("Expected IllegalArgumentException for circular move")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("descendants") == true)
        }
    }

    @Test
    fun testDeleteFolderCascadeUnlinksOrDeletesDocuments() = runTest {
        val root = Folder(id = 1L, name = "Projects", parentFolderId = -1L, path = "/")
        val child = Folder(id = 2L, name = "2026", parentFolderId = 1L, path = "/1/")
        val other = Folder(id = 3L, name = "Other", parentFolderId = -1L, path = "/")

        coEvery { folderDao.getAllFoldersList() } returns listOf(root, child, other)
        coEvery { folderDao.deleteFolderById(any()) } just Runs
        coEvery { documentDao.unlinkDocumentsFromFolder(any()) } just Runs

        // Unlink mode (deleteContainedDocuments = false)
        repository.deleteFolderCascade(root, deleteContainedDocuments = false)

        coVerify(exactly = 1) { documentDao.unlinkDocumentsFromFolder(1L) }
        coVerify(exactly = 1) { documentDao.unlinkDocumentsFromFolder(2L) }
        coVerify(exactly = 1) { folderDao.deleteFolderById(1L) }
        coVerify(exactly = 1) { folderDao.deleteFolderById(2L) }
        coVerify(exactly = 0) { folderDao.deleteFolderById(3L) }
    }

    @Test
    fun testCopyDocumentToFolderDeepCopiesFiles() = runTest {
        val origDoc = Document(id = 100L, title = "Original Contract", folderId = -1L)
        val scansDir = File(tempFolder.root, "scans").apply { mkdirs() }
        val thumbsDir = File(tempFolder.root, "thumbs").apply { mkdirs() }

        val origProcFile = File(scansDir, "page_100_1.jpg").apply { writeText("fake processed image data") }
        val origOrigFile = File(scansDir, "orig_100_1.jpg").apply { writeText("fake original image data") }
        val origThumbFile = File(thumbsDir, "thumb_100_1.jpg").apply { writeText("fake thumb data") }

        val origPage = Page(
            id = 1L,
            documentId = 100L,
            pageIndex = 0,
            processedImagePath = origProcFile.absolutePath,
            originalImagePath = origOrigFile.absolutePath,
            thumbnailPath = origThumbFile.absolutePath
        )

        coEvery { documentDao.getDocumentById(100L) } returns origDoc
        coEvery { pageDao.getPagesListForDocument(100L) } returns listOf(origPage)

        val insertedDocSlot = slot<Document>()
        coEvery { documentDao.insertDocument(capture(insertedDocSlot)) } returns 200L

        val insertedPagesSlot = slot<List<Page>>()
        coEvery { pageDao.insertPages(capture(insertedPagesSlot)) } returns listOf(2L)

        val newDocId = repository.copyDocumentToFolder(docId = 100L, targetFolderId = 55L)
        assertEquals(200L, newDocId)

        // Verify document attributes
        assertTrue(insertedDocSlot.isCaptured)
        val newDoc = insertedDocSlot.captured
        assertEquals("Original Contract (Copy)", newDoc.title)
        assertEquals(55L, newDoc.folderId)
        assertEquals(1, newDoc.pageCount)

        // Verify page attributes and distinct disk files
        assertTrue(insertedPagesSlot.isCaptured)
        val newPages = insertedPagesSlot.captured
        assertEquals(1, newPages.size)
        val newPage = newPages[0]
        assertEquals(200L, newPage.documentId)
        assertEquals(0, newPage.pageIndex)

        assertNotEquals(origPage.processedImagePath, newPage.processedImagePath)
        assertNotEquals(origPage.originalImagePath, newPage.originalImagePath)
        assertNotEquals(origPage.thumbnailPath, newPage.thumbnailPath)

        // Verify new files exist on disk with identical copied content
        val newProcFile = File(newPage.processedImagePath)
        val newOrigFile = File(newPage.originalImagePath)
        val newThumbFile = File(newPage.thumbnailPath)

        assertTrue(newProcFile.exists())
        assertTrue(newOrigFile.exists())
        assertTrue(newThumbFile.exists())

        assertEquals("fake processed image data", newProcFile.readText())
        assertEquals("fake original image data", newOrigFile.readText())
        assertEquals("fake thumb data", newThumbFile.readText())

        // Verify original files still exist
        assertTrue(origProcFile.exists())
        assertTrue(origOrigFile.exists())
        assertTrue(origThumbFile.exists())
    }

    @Test
    fun testCopyDocumentRollbackOnFailureDeletesFiles() = runTest {
        val origDoc = Document(id = 300L, title = "Fail Test", folderId = -1L)
        val scansDir = File(tempFolder.root, "scans").apply { mkdirs() }
        val procFile = File(scansDir, "page_300_1.jpg").apply { writeText("data to copy") }

        val origPage = Page(
            id = 1L,
            documentId = 300L,
            pageIndex = 0,
            processedImagePath = procFile.absolutePath,
            originalImagePath = procFile.absolutePath,
            thumbnailPath = ""
        )

        coEvery { documentDao.getDocumentById(300L) } returns origDoc
        coEvery { pageDao.getPagesListForDocument(300L) } returns listOf(origPage)
        coEvery { documentDao.insertDocument(any()) } returns 400L
        // Simulate DB crash during page insert
        coEvery { pageDao.insertPages(any()) } throws IOException("Disk write failure")
        coEvery { documentDao.deleteDocument(any()) } just Runs

        try {
            repository.copyDocumentToFolder(docId = 300L, targetFolderId = 1L)
            fail("Expected IOException")
        } catch (e: IOException) {
            // Document should be deleted from DB
            coVerify(exactly = 1) { documentDao.deleteDocument(match { it.id == 400L }) }

            // Copied files should be rolled back (deleted from disk)
            val filesInScans = scansDir.listFiles()?.filter { it.name.startsWith("page_400_") } ?: emptyList()
            assertTrue("Expected rolled back files to be deleted", filesInScans.isEmpty())
        }
    }
}
