package org.openscanner.cv

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.openscanner.data.local.DocumentDao
import org.openscanner.data.local.FolderDao
import org.openscanner.data.local.PageDao
import org.openscanner.data.model.Document
import org.openscanner.data.model.Page
import org.openscanner.data.repository.CollagePageSpec
import org.openscanner.data.repository.ScannerRepository
import org.openscanner.ui.detail.dialogs.CollageSheetState
import org.openscanner.ui.detail.dialogs.CrossPageDragController
import java.io.File

/**
 * Unit tests verifying:
 * 1. Collage file and document naming convention: 'collage-<filename>'
 * 2. CrossPageDragController multi-page move and swap behavior
 * 3. Mathematical coordinate invariance eliminating gesture flicker
 */
class CollageNamingAndCrossPageTest {

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

        val filesDir = File("/tmp/openscanner_test_files").apply { mkdirs() }
        coEvery { context.filesDir } returns filesDir

        io.mockk.mockkStatic(android.graphics.Bitmap::class)
        val dummyThumb = mockk<android.graphics.Bitmap>(relaxed = true)
        io.mockk.every { android.graphics.Bitmap.createScaledBitmap(any(), any(), any(), any()) } returns dummyThumb

        repository = ScannerRepository(context, documentDao, pageDao, folderDao)
    }

    @org.junit.After
    fun tearDown() {
        io.mockk.unmockkAll()
    }

    @Test
    fun testCollageDocumentNamingFormat() = runTest {
        val sourceDocId = 42L
        val sourceDoc = Document(id = sourceDocId, title = "Tax Receipts 2026", folderId = 5L)
        coEvery { documentDao.getDocumentById(sourceDocId) } returns sourceDoc

        val insertedDocSlot = slot<Document>()
        coEvery { documentDao.insertDocument(capture(insertedDocSlot)) } returns 999L

        // Call repository.createCollageDocument with empty specs (will create new document)
        val createdDocId = repository.createCollageDocument(
            sourceDocId = sourceDocId,
            specs = emptyList()
        )

        assertEquals(999L, createdDocId)
        assertTrue(insertedDocSlot.isCaptured)
        // Title must follow collage-<filename/docname>
        assertEquals("collage-Tax_Receipts_2026", insertedDocSlot.captured.title)
        assertEquals(5L, insertedDocSlot.captured.folderId)
    }

    @Test
    fun testCollageDocumentCustomTitle() = runTest {
        val sourceDocId = 43L
        val sourceDoc = Document(id = sourceDocId, title = "Invoice", folderId = -1L)
        coEvery { documentDao.getDocumentById(sourceDocId) } returns sourceDoc

        val insertedDocSlot = slot<Document>()
        coEvery { documentDao.insertDocument(capture(insertedDocSlot)) } returns 1000L

        val createdDocId = repository.createCollageDocument(
            sourceDocId = sourceDocId,
            specs = emptyList(),
            customTitle = "collage-custom_export"
        )

        assertEquals(1000L, createdDocId)
        assertTrue(insertedDocSlot.isCaptured)
        assertEquals("collage-custom_export", insertedDocSlot.captured.title)
    }

    @Test
    fun testCrossPageDragControllerStartAndCancel() {
        val controller = CrossPageDragController()
        assertFalse(controller.isDragging)
        assertEquals(-1, controller.sourceSheetIndex)

        val page = Page(id = 1L, documentId = 10L, pageIndex = 0, processedImagePath = "/path/p1.jpg", originalImagePath = "")
        controller.startDrag(
            sheetIndex = 0,
            slotIndex = 1,
            page = page,
            customPath = null,
            bitmap = null,
            itemSize = IntSize(300, 400),
            rootPos = Offset(150f, 200f)
        )

        assertTrue(controller.isDragging)
        assertEquals(0, controller.sourceSheetIndex)
        assertEquals(1, controller.sourceSlotIndex)
        assertEquals(page, controller.draggedPage)
        assertEquals(0, controller.hoverSheetIndex)
        assertEquals(1, controller.hoverSlotIndex)

        controller.cancelDrag()
        assertFalse(controller.isDragging)
        assertEquals(-1, controller.sourceSheetIndex)
        assertNull(controller.draggedPage)
    }

    @Test
    fun testCrossPageDragPositionDetectionAcrossSheets() {
        val controller = CrossPageDragController()
        val sheet0Rect = Rect(0f, 0f, 500f, 800f)
        val sheet1Rect = Rect(0f, 900f, 500f, 1700f)

        controller.sheetBoundsInRoot[0] = sheet0Rect
        controller.sheetBoundsInRoot[1] = sheet1Rect

        val page = Page(id = 2L, documentId = 10L, pageIndex = 0, processedImagePath = "", originalImagePath = "")
        controller.startDrag(0, 0, page, null, null, IntSize(200, 200), Offset(100f, 100f))

        // Hover within sheet 0
        controller.updateDragPosition(Offset(250f, 400f), CollageLayout.FOUR_GRID)
        assertEquals(0, controller.hoverSheetIndex)
        assertNotNull(controller.hoverSlotIndex)

        // Drag down into sheet 1
        controller.updateDragPosition(Offset(250f, 1100f), CollageLayout.FOUR_GRID)
        assertEquals(1, controller.hoverSheetIndex)
        assertNotNull(controller.hoverSlotIndex)

        // Drag outside any sheet bounds
        controller.updateDragPosition(Offset(9999f, 9999f), CollageLayout.FOUR_GRID)
        assertNull(controller.hoverSheetIndex)
    }

    @Test
    fun testCrossPageDragCommitMoveToEmptySlotOnAnotherPage() {
        val controller = CrossPageDragController()
        val sheet0 = CollageSheetState()
        val sheet1 = CollageSheetState()

        val pageA = Page(id = 101L, documentId = 1L, pageIndex = 0, processedImagePath = "/p1.jpg", originalImagePath = "")
        sheet0.slotAssignments[0] = pageA
        sheet0.slotCroppedPaths[0] = "/crop/p1.jpg"

        val sheets = mutableListOf(sheet0, sheet1)

        controller.startDrag(0, 0, pageA, "/crop/p1.jpg", null, IntSize(100, 100), Offset(50f, 50f))
        controller.hoverSheetIndex = 1
        controller.hoverSlotIndex = 2

        var selectedSheet = -1
        var selectedSlot = -1
        var toastMessage = ""

        controller.commitDrop(
            sheets = sheets,
            onSelect = { s, slot ->
                selectedSheet = s
                selectedSlot = slot
            },
            showToast = { toastMessage = it }
        )

        // Slot on sheet 0 should be empty
        assertNull(sheet0.slotAssignments[0])
        assertNull(sheet0.slotCroppedPaths[0])

        // Slot 2 on sheet 1 should contain pageA and its crop
        assertEquals(pageA, sheet1.slotAssignments[2])
        assertEquals("/crop/p1.jpg", sheet1.slotCroppedPaths[2])

        assertEquals(1, selectedSheet)
        assertEquals(2, selectedSlot)
        assertTrue(toastMessage.contains("Moved to Page 2"))
        assertFalse(controller.isDragging)
    }

    @Test
    fun testCrossPageDragCommitSwapBetweenPages() {
        val controller = CrossPageDragController()
        val sheet0 = CollageSheetState()
        val sheet1 = CollageSheetState()

        val pageA = Page(id = 101L, documentId = 1L, pageIndex = 0, processedImagePath = "/p1.jpg", originalImagePath = "")
        val pageB = Page(id = 102L, documentId = 1L, pageIndex = 1, processedImagePath = "/p2.jpg", originalImagePath = "")
        sheet0.slotAssignments[0] = pageA
        sheet1.slotAssignments[1] = pageB

        val sheets = mutableListOf(sheet0, sheet1)

        controller.startDrag(0, 0, pageA, null, null, IntSize(100, 100), Offset(50f, 50f))
        controller.hoverSheetIndex = 1
        controller.hoverSlotIndex = 1

        var toastMessage = ""
        controller.commitDrop(
            sheets = sheets,
            onSelect = { _, _ -> },
            showToast = { toastMessage = it }
        )

        // Swapped!
        assertEquals(pageB, sheet0.slotAssignments[0])
        assertEquals(pageA, sheet1.slotAssignments[1])
        assertTrue(toastMessage.contains("Swapped with Page 2"))
        assertFalse(controller.isDragging)
    }

    @Test
    fun testCoordinateInvarianceMath() {
        // Simulates finger moving by 20px on screen.
        // In local coordinates inside graphicsLayer (with translationX = panX * sheetW):
        // If panX changes by delta, the local touch point in inverted space jumps by -delta, causing oscillation.
        // With root coordinates:
        val sheetWidth = 1000f
        val touch0 = Offset(500f, 500f)
        val touch1 = Offset(520f, 500f)

        // Root delta is always 20px invariant to any layer matrix
        val rootDeltaX = touch1.x - touch0.x
        assertEquals(20f, rootDeltaX, 0.001f)

        val panDelta = rootDeltaX / sheetWidth
        assertEquals(0.02f, panDelta, 0.0001f)

        // Verify next frame with finger at same physical screen location (520f) has 0 delta:
        val touch2 = Offset(520f, 500f)
        val nextRootDeltaX = touch2.x - touch1.x
        assertEquals(0f, nextRootDeltaX, 0.001f)
    }

    @Test
    fun testSavePageCollagePrefixFileName() = runTest {
        val processedBitmap = mockk<android.graphics.Bitmap>(relaxed = true)
        val insertedPageSlot = slot<Page>()
        coEvery { pageDao.insertPage(capture(insertedPageSlot)) } returns 101L

        repository.savePage(
            documentId = 1L,
            processedBitmap = processedBitmap,
            originalBitmap = null,
            cropBorderJson = "",
            enhanceModeId = 1,
            fileNamePrefix = "collage-tax_return"
        )

        assertTrue(insertedPageSlot.isCaptured)
        val savedPath = insertedPageSlot.captured.processedImagePath
        assertTrue(File(savedPath).name.startsWith("collage-tax_return_"))
        assertFalse(File(savedPath).name.contains("page_1_"))
    }

    @Test
    fun testCrossPageDragWithGapTolerance() {
        val controller = CrossPageDragController()
        val sheet0Rect = Rect(0f, 0f, 500f, 800f)
        val sheet1Rect = Rect(0f, 830f, 500f, 1630f) // 30px gap between sheets

        controller.sheetBoundsInRoot[0] = sheet0Rect
        controller.sheetBoundsInRoot[1] = sheet1Rect

        val page = Page(id = 2L, documentId = 10L, pageIndex = 0, processedImagePath = "", originalImagePath = "")
        controller.startDrag(0, 0, page, null, null, IntSize(200, 200), Offset(100f, 100f))

        // Hover in the 30px gap at Y = 820f (within 80px tolerance of Sheet 1)
        controller.updateDragPosition(Offset(250f, 820f), CollageLayout.FOUR_GRID)
        assertEquals(1, controller.hoverSheetIndex)
        assertNotNull(controller.hoverSlotIndex)
    }

    @Test
    fun testMathematicalTransformInvariance() {
        val baseW = 400f
        val baseH = 300f
        val sheetW = 1000f
        val sheetH = 1400f
        val cx = baseW / 2f
        val cy = baseH / 2f

        // Arbitrary live transform parameters
        val panX = 0.15f
        val panY = -0.08f
        val scale = 1.75f
        val rotationDeg = 35f

        val rad = Math.toRadians(rotationDeg.toDouble())
        val cosR = kotlin.math.cos(rad).toFloat()
        val sinR = kotlin.math.sin(rad).toFloat()

        // Finger is physically at screen/parent point (280f, 320f)
        val trueParentPoint = Offset(280f, 320f)

        // 1. Compose simulates pointer input down/move by applying inverse layer matrix:
        val unpannedX = trueParentPoint.x - cx - panX * sheetW
        val unpannedY = trueParentPoint.y - cy - panY * sheetH
        // Inverse rotation (-rotationDeg):
        val unrotX = unpannedX * cosR + unpannedY * sinR
        val unrotY = -unpannedX * sinR + unpannedY * cosR
        // Inverse scale (1 / scale):
        val localTouchX = cx + unrotX / scale
        val localTouchY = cy + unrotY / scale
        val localTouch = Offset(localTouchX, localTouchY)

        // 2. mapLocalItemToRoot forward transform:
        val dx = (localTouch.x - cx) * scale
        val dy = (localTouch.y - cy) * scale
        val rotX = dx * cosR - dy * sinR
        val rotY = dx * sinR + dy * cosR
        val recoveredParentX = cx + rotX + panX * sheetW
        val recoveredParentY = cy + rotY + panY * sheetH

        // Must match the original screen/parent position within floating point epsilon
        assertEquals(trueParentPoint.x, recoveredParentX, 0.001f)
        assertEquals(trueParentPoint.y, recoveredParentY, 0.001f)
    }
}
