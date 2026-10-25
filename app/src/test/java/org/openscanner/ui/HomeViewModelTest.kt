package org.openscanner.ui

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.openscanner.OpenScannerApp
import org.openscanner.data.local.AppDatabase
import org.openscanner.data.local.DocumentDao
import org.openscanner.data.local.FolderDao
import org.openscanner.data.local.PageDao
import org.openscanner.data.model.Document
import org.openscanner.data.model.Folder
import org.openscanner.ui.home.DocSortOrder
import org.openscanner.ui.home.HomeViewModel

/**
 * Tier 1, Tier 2, Tier 3: Tests for HomeViewModel UI state, sorting, searching, and batch selection.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var app: OpenScannerApp
    private lateinit var db: AppDatabase
    private lateinit var documentDao: DocumentDao
    private lateinit var pageDao: PageDao
    private lateinit var folderDao: FolderDao
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        documentDao = mockk(relaxed = true)
        pageDao = mockk(relaxed = true)
        folderDao = mockk(relaxed = true)
        db = mockk(relaxed = true) {
            every { documentDao() } returns this@HomeViewModelTest.documentDao
            every { pageDao() } returns this@HomeViewModelTest.pageDao
            every { folderDao() } returns this@HomeViewModelTest.folderDao
        }

        every { folderDao.getAllFolders() } returns flowOf(emptyList())
        every { documentDao.getAllDocuments() } returns flowOf(emptyList())

        app = mockk(relaxed = true) {
            every { database } returns this@HomeViewModelTest.db
        }

        viewModel = HomeViewModel(app)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testDocSortOrderDefinitions() {
        assertEquals("Date (Newest first)", DocSortOrder.DATE_DESC.title)
        assertEquals("Date (Oldest first)", DocSortOrder.DATE_ASC.title)
        assertEquals("Name (A to Z)", DocSortOrder.NAME_ASC.title)
        assertEquals("Pages (Most first)", DocSortOrder.PAGE_COUNT_DESC.title)
    }

    @Test
    fun testInitialState() {
        assertFalse(viewModel.isSelectMode.value)
        assertTrue(viewModel.selectedDocIds.value.isEmpty())
        assertFalse(viewModel.isGridView.value)
        assertNull(viewModel.selectedFolder.value)
        assertEquals("", viewModel.searchQuery.value)
        assertEquals(DocSortOrder.DATE_DESC, viewModel.sortOrder.value)
    }

    @Test
    fun testToggleGridView() {
        assertFalse(viewModel.isGridView.value)
        viewModel.toggleGridView()
        assertTrue(viewModel.isGridView.value)
        viewModel.toggleGridView()
        assertFalse(viewModel.isGridView.value)
    }

    @Test
    fun testSelectionModeAndTogglingDocuments() {
        assertFalse(viewModel.isSelectMode.value)

        // Toggling document automatically engages select mode
        viewModel.toggleSelectDoc(101L)
        assertTrue(viewModel.isSelectMode.value)
        assertTrue(viewModel.selectedDocIds.value.contains(101L))
        assertEquals(1, viewModel.selectedDocIds.value.size)

        // Toggling second document
        viewModel.toggleSelectDoc(102L)
        assertEquals(2, viewModel.selectedDocIds.value.size)
        assertTrue(viewModel.selectedDocIds.value.contains(102L))

        // Toggling off first document
        viewModel.toggleSelectDoc(101L)
        assertEquals(1, viewModel.selectedDocIds.value.size)
        assertFalse(viewModel.selectedDocIds.value.contains(101L))
        assertTrue(viewModel.selectedDocIds.value.contains(102L))

        // Clear selection resets select mode
        viewModel.clearSelection()
        assertFalse(viewModel.isSelectMode.value)
        assertTrue(viewModel.selectedDocIds.value.isEmpty())
    }

    @Test
    fun testFolderSelectionAndSearchQuery() {
        val folder = Folder(id = 5L, name = "Receipts")
        viewModel.selectFolder(folder)
        assertEquals(folder, viewModel.selectedFolder.value)

        viewModel.selectFolder(null)
        assertNull(viewModel.selectedFolder.value)

        viewModel.onSearchQueryChanged("Invoice #42")
        assertEquals("Invoice #42", viewModel.searchQuery.value)

        viewModel.setSortOrder(DocSortOrder.NAME_ASC)
        assertEquals(DocSortOrder.NAME_ASC, viewModel.sortOrder.value)
    }

    @Test
    fun testRootVsFolderDocumentFiltering() {
        val rootDoc = Document(id = 1L, title = "Root Doc", folderId = -1L)
        val folderDoc = Document(id = 2L, title = "Folder Doc", folderId = 5L)
        val folder = Folder(id = 5L, name = "Finance")

        every { folderDao.getAllFolders() } returns flowOf(listOf(folder))
        every { documentDao.getAllDocuments() } returns flowOf(listOf(rootDoc, folderDoc))

        val vm = HomeViewModel(app)
        testDispatcher.scheduler.advanceUntilIdle()

        // At root level, only root documents (folderId <= 0) should be visible
        assertEquals(1, vm.documents.value.size)
        assertEquals(1L, vm.documents.value.first().id)

        // When folder 5 is selected, only documents inside folder 5 should be visible
        vm.selectFolder(folder)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, vm.documents.value.size)
        assertEquals(2L, vm.documents.value.first().id)

        // When navigating back to root, root document is visible again and folder doc is hidden
        vm.selectFolder(null)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, vm.documents.value.size)
        assertEquals(1L, vm.documents.value.first().id)
    }

    @Test
    fun testNestedSubfolderHierarchyAndNavigation() {
        val rootFolder = Folder(id = 10L, name = "Work", parentFolderId = -1L)
        val subFolder = Folder(id = 20L, name = "2026 Taxes", parentFolderId = 10L)

        every { folderDao.getAllFolders() } returns flowOf(listOf(rootFolder, subFolder))
        coEvery { folderDao.getFolderById(10L) } returns rootFolder
        coEvery { folderDao.getFolderById(20L) } returns subFolder
        every { documentDao.getAllDocuments() } returns flowOf(emptyList())

        val vm = HomeViewModel(app)
        testDispatcher.scheduler.advanceUntilIdle()

        // At root level: allFoldersWithCount has 2, foldersWithCount has only rootFolder (parentFolderId = -1)
        assertEquals(2, vm.allFoldersWithCount.value.size)
        assertEquals(1, vm.foldersWithCount.value.size)
        assertEquals(10L, vm.foldersWithCount.value.first().folder.id)

        // Select rootFolder (Work): foldersWithCount now contains subFolder (parentFolderId = 10)
        vm.selectFolder(rootFolder)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, vm.foldersWithCount.value.size)
        assertEquals(20L, vm.foldersWithCount.value.first().folder.id)

        // Select subFolder
        vm.selectFolder(subFolder)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, vm.foldersWithCount.value.size) // No sub-subfolders

        // navigateUp() from subFolder -> rootFolder
        vm.navigateUp()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(10L, vm.selectedFolder.value?.id)

        // navigateUp() from rootFolder -> Root (null)
        vm.navigateUp()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(vm.selectedFolder.value)
    }
}
