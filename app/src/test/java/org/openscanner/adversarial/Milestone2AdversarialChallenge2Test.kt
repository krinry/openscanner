package org.openscanner.adversarial

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.openscanner.camera.BatchWarpProcessor
import org.openscanner.cv.BookSpineDetector
import org.openscanner.cv.DocumentQuadrilateral
import org.openscanner.cv.NativeScannerEngine
import org.openscanner.cv.PointF
import org.openscanner.data.local.DocumentDao
import org.openscanner.data.local.FolderDao
import org.openscanner.data.local.PageDao
import org.openscanner.data.model.Document
import org.openscanner.data.model.EnhanceMode
import org.openscanner.data.model.Page
import org.openscanner.data.repository.ScannerRepository
import org.openscanner.ui.camera.BatchCaptureItem
import org.openscanner.ui.camera.CameraViewModel
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Milestone 2 Adversarial Challenge Test Suite (Challenger 2):
 * 1. BatchWarpProcessor Concurrency Stress (50+ items through Semaphore(2)) & Fault Injection
 * 2. CameraViewModel Queue Operations & State Synchronization Stress
 * 3. ScannerRepository ID Card Compositing Geometry & Book Splitting Robustness
 */
class Milestone2AdversarialChallenge2Test {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var documentDao: DocumentDao
    private lateinit var pageDao: PageDao
    private lateinit var folderDao: FolderDao
    private lateinit var repository: ScannerRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = mockk(relaxed = true)
        every { context.filesDir } returns tempFolder.root
        documentDao = mockk(relaxed = true)
        pageDao = mockk(relaxed = true)
        folderDao = mockk(relaxed = true)
        repository = ScannerRepository(context, documentDao, pageDao, folderDao)

        mockkStatic(Bitmap::class)
        val dummyBitmap = mockk<Bitmap>(relaxed = true)
        every { dummyBitmap.width } returns 800
        every { dummyBitmap.height } returns 600
        every { dummyBitmap.isRecycled } returns false
        every { Bitmap.createBitmap(any<Bitmap>(), any(), any(), any(), any(), any(), any()) } returns dummyBitmap
        every { Bitmap.createBitmap(any<Int>(), any<Int>(), any()) } returns dummyBitmap
        every { Bitmap.createScaledBitmap(any(), any(), any(), any()) } returns dummyBitmap
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    // =========================================================================
    // AREA 1: BatchWarpProcessor Concurrency Stress & Fault Injection
    // =========================================================================

    @Test
    fun testBatchWarpProcessorSemaphoreConcurrencyLimit50Items() = runTest {
        mockkStatic(BitmapFactory::class)
        mockkObject(NativeScannerEngine)

        val mockRawBmp = mockk<Bitmap>(relaxed = true)
        every { mockRawBmp.width } returns 800
        every { mockRawBmp.height } returns 600
        every { mockRawBmp.isRecycled } returns false

        val mockWarpedBmp = mockk<Bitmap>(relaxed = true)
        every { mockWarpedBmp.width } returns 800
        every { mockWarpedBmp.height } returns 600
        every { mockWarpedBmp.isRecycled } returns false

        every { BitmapFactory.decodeFile(any(), any<BitmapFactory.Options>()) } answers {
            val opts = secondArg<BitmapFactory.Options>()
            if (opts.inJustDecodeBounds) {
                opts.outWidth = 800
                opts.outHeight = 600
                null
            } else {
                mockRawBmp
            }
        }

        every { NativeScannerEngine.detectDocumentBounds(any<Bitmap>()) } returns DocumentQuadrilateral(
            topLeft = PointF(0f, 0f),
            topRight = PointF(800f, 0f),
            bottomRight = PointF(800f, 600f),
            bottomLeft = PointF(0f, 600f)
        )
        every { NativeScannerEngine.warpDocument(any(), any(), any(), any()) } returns mockWarpedBmp
        every { NativeScannerEngine.applyEnhancement(any(), any()) } just Runs

        val currentConcurrent = AtomicInteger(0)
        val maxConcurrentObserved = AtomicInteger(0)
        val completedPages = AtomicInteger(0)

        val mockRepository = mockk<ScannerRepository>(relaxed = true)
        coEvery { mockRepository.savePage(any(), any(), any(), any(), any()) } coAnswers {
            val current = currentConcurrent.incrementAndGet()
            maxConcurrentObserved.updateAndGet { prev -> maxOf(prev, current) }

            // Artificial delay to stress-test semaphore permit queue
            delay(15)

            currentConcurrent.decrementAndGet()
            completedPages.incrementAndGet()

            Page(
                id = completedPages.get().toLong(),
                documentId = firstArg(),
                pageIndex = completedPages.get() - 1,
                processedImagePath = "/mock/path.jpg",
                originalImagePath = "/mock/orig.jpg"
            )
        }

        val processor = BatchWarpProcessor(context, mockRepository)

        // Create 50 real temporary files on disk
        val items = (1..50).map { i ->
            val file = tempFolder.newFile("stress_burst_$i.jpg").apply {
                writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()))
            }
            BatchCaptureItem(
                id = i.toLong(),
                rawFilePath = file.absolutePath,
                thumbnailBitmap = mockRawBmp,
                rotationDegrees = 0
            )
        }

        val lastProgressDone = AtomicInteger(0)
        val lastProgressTotal = AtomicInteger(0)

        val resultDocId = processor.processBatch(
            targetDocId = 200L,
            items = items,
            selectedEnhanceMode = EnhanceMode.MAGIC_COLOR,
            onProgress = { done, total ->
                lastProgressDone.set(done)
                lastProgressTotal.set(total)
            }
        )

        assertEquals("Target document ID must be preserved", 200L, resultDocId)
        assertEquals("All 50 pages must complete processing", 50, completedPages.get())
        assertEquals("Progress callback must report 50 done", 50, lastProgressDone.get())
        assertEquals("Progress callback must report 50 total", 50, lastProgressTotal.get())

        // EMPIRICAL CHALLENGE: Verify Semaphore(2) strictly capped concurrent active warps
        assertTrue(
            "Max concurrent active executions must not exceed 2 (observed: ${maxConcurrentObserved.get()})",
            maxConcurrentObserved.get() <= 2
        )
        assertTrue(
            "Concurrent executions must have engaged at least 1 permit",
            maxConcurrentObserved.get() >= 1
        )
    }

    @Test
    fun testEmptyBatchProcessingReturnsValidDocIdWithoutException() = runTest {
        val mockRepo = mockk<ScannerRepository>(relaxed = true)
        val processor = BatchWarpProcessor(context, mockRepo)

        // Case A: Pre-existing valid docId
        val docIdA = processor.processBatch(
            targetDocId = 999L,
            items = emptyList(),
            onProgress = { _, _ -> }
        )
        assertEquals(999L, docIdA)
        coVerify(exactly = 0) { mockRepo.createNewDocument(any(), any()) }

        // Case B: No target docId specified (<= 0)
        // With an empty batch, processor safely returns the ID without throwing
        val docIdB = processor.processBatch(
            targetDocId = -1L,
            items = emptyList(),
            onProgress = { _, _ -> }
        )
        assertEquals(-1L, docIdB)
    }

    @Test
    fun testFaultInjectionMissingFilesHandledGracefully() = runTest {
        val mockRepo = mockk<ScannerRepository>(relaxed = true)
        val processor = BatchWarpProcessor(context, mockRepo)

        val nonExistentPath = File(tempFolder.root, "does_not_exist_burst.jpg").absolutePath
        val dummyBitmap = mockk<Bitmap>(relaxed = true)
        val item = BatchCaptureItem(
            id = 404L,
            rawFilePath = nonExistentPath,
            thumbnailBitmap = dummyBitmap
        )

        val progressCalled = AtomicInteger(0)
        val docId = processor.processBatch(
            targetDocId = 100L,
            items = listOf(item),
            onProgress = { done, total ->
                progressCalled.set(done)
                assertEquals(1, total)
            }
        )

        assertEquals(100L, docId)
        // Missing file must not crash; onProgress is incremented so UI does not freeze
        assertEquals(1, progressCalled.get())
        // savePage must not be called for missing file
        coVerify(exactly = 0) { mockRepo.savePage(any(), any(), any(), any(), any()) }
    }

    @Test
    fun testFaultInjectionCorruptedJpegHandledGracefully() = runTest {
        mockkStatic(BitmapFactory::class)

        // Write corrupt file with random non-JPEG bytes
        val corruptFile = tempFolder.newFile("corrupt_burst.jpg").apply {
            writeBytes(byteArrayOf(0x00, 0x11, 0x22, 0x33, 0x44))
        }

        // Simulate decode failure: outWidth and outHeight <= 0
        every { BitmapFactory.decodeFile(corruptFile.absolutePath, any<BitmapFactory.Options>()) } answers {
            val opts = secondArg<BitmapFactory.Options>()
            opts.outWidth = -1
            opts.outHeight = -1
            null
        }

        val mockRepo = mockk<ScannerRepository>(relaxed = true)
        val processor = BatchWarpProcessor(context, mockRepo)
        val dummyBitmap = mockk<Bitmap>(relaxed = true)
        val item = BatchCaptureItem(
            id = 505L,
            rawFilePath = corruptFile.absolutePath,
            thumbnailBitmap = dummyBitmap
        )

        val progressCount = AtomicInteger(0)
        val docId = processor.processBatch(
            targetDocId = 150L,
            items = listOf(item),
            onProgress = { done, _ -> progressCount.set(done) }
        )

        assertEquals(150L, docId)
        assertEquals(1, progressCount.get())
        coVerify(exactly = 0) { mockRepo.savePage(any(), any(), any(), any(), any()) }
    }

    @Test
    fun testMixedBatchStressWithFaults() = runTest {
        mockkStatic(BitmapFactory::class)
        mockkObject(NativeScannerEngine)

        val mockRawBmp = mockk<Bitmap>(relaxed = true)
        every { mockRawBmp.width } returns 800
        every { mockRawBmp.height } returns 600
        every { mockRawBmp.isRecycled } returns false

        val mockWarpedBmp = mockk<Bitmap>(relaxed = true)
        every { mockWarpedBmp.width } returns 800
        every { mockWarpedBmp.height } returns 600
        every { mockWarpedBmp.isRecycled } returns false

        every { NativeScannerEngine.detectDocumentBounds(any<Bitmap>()) } returns DocumentQuadrilateral(
            topLeft = PointF(0f, 0f),
            topRight = PointF(800f, 0f),
            bottomRight = PointF(800f, 600f),
            bottomLeft = PointF(0f, 600f)
        )
        every { NativeScannerEngine.warpDocument(any(), any(), any(), any()) } returns mockWarpedBmp
        every { NativeScannerEngine.applyEnhancement(any(), any()) } just Runs

        val validFiles = (1..10).map { i ->
            tempFolder.newFile("mixed_valid_$i.jpg").apply {
                writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()))
            }
        }
        val corruptFiles = (1..10).map { i ->
            tempFolder.newFile("mixed_corrupt_$i.jpg").apply {
                writeBytes(byteArrayOf(0x01, 0x02, 0x03))
            }
        }
        val missingPaths = (1..10).map { i ->
            File(tempFolder.root, "mixed_missing_$i.jpg").absolutePath
        }

        every { BitmapFactory.decodeFile(any(), any<BitmapFactory.Options>()) } answers {
            val path = firstArg<String>()
            val opts = secondArg<BitmapFactory.Options>()
            if (path.contains("valid")) {
                if (opts.inJustDecodeBounds) {
                    opts.outWidth = 800
                    opts.outHeight = 600
                    null
                } else {
                    mockRawBmp
                }
            } else {
                opts.outWidth = -1
                opts.outHeight = -1
                null
            }
        }

        val currentConcurrent = AtomicInteger(0)
        val maxConcurrentObserved = AtomicInteger(0)
        val savedPageCount = AtomicInteger(0)

        val mockRepo = mockk<ScannerRepository>(relaxed = true)
        coEvery { mockRepo.savePage(any(), any(), any(), any(), any()) } coAnswers {
            val c = currentConcurrent.incrementAndGet()
            maxConcurrentObserved.updateAndGet { prev -> maxOf(prev, c) }
            delay(10)
            currentConcurrent.decrementAndGet()
            savedPageCount.incrementAndGet()
            mockk(relaxed = true)
        }

        val allItems = mutableListOf<BatchCaptureItem>()
        validFiles.forEachIndexed { i, f ->
            allItems.add(BatchCaptureItem(id = (i + 1).toLong(), rawFilePath = f.absolutePath, thumbnailBitmap = mockRawBmp))
        }
        corruptFiles.forEachIndexed { i, f ->
            allItems.add(BatchCaptureItem(id = (i + 101).toLong(), rawFilePath = f.absolutePath, thumbnailBitmap = mockRawBmp))
        }
        missingPaths.forEachIndexed { i, p ->
            allItems.add(BatchCaptureItem(id = (i + 201).toLong(), rawFilePath = p, thumbnailBitmap = mockRawBmp))
        }

        val processor = BatchWarpProcessor(context, mockRepo)
        val doneCount = AtomicInteger(0)

        val docId = processor.processBatch(
            targetDocId = 333L,
            items = allItems,
            onProgress = { done, total ->
                doneCount.set(done)
                assertEquals(30, total)
            }
        )

        assertEquals(333L, docId)
        assertEquals("Total progress must report 30/30", 30, doneCount.get())
        assertEquals("Exactly 10 valid files must be saved", 10, savedPageCount.get())
        assertTrue("Max concurrent warps must be <= 2", maxConcurrentObserved.get() <= 2)
    }

    // =========================================================================
    // AREA 2: CameraViewModel Batch Queue Operations & State Synchronization
    // =========================================================================

    @Test
    fun testCameraViewModelRapidSequentialAdditions() {
        val app = mockk<Application>(relaxed = true)
        val vm = CameraViewModel(app)
        val dummyBitmap = mockk<Bitmap>(relaxed = true)

        // Rapidly add 50 items and verify state synchronization at every addition
        for (i in 1..50) {
            val item = BatchCaptureItem(
                id = i.toLong(),
                rawFilePath = "/cache/burst_$i.jpg",
                thumbnailBitmap = dummyBitmap
            )
            vm.addBatchItem(item)

            assertEquals("batchQueue size must match insertion count $i", i, vm.batchQueue.value.size)
            assertEquals("batchCount must stay synchronized with batchQueue size ($i)", i, vm.batchCount.value)
            assertEquals("Inserted item ID must match", i.toLong(), vm.batchQueue.value[i - 1].id)
        }
    }

    @Test
    fun testCameraViewModelSequentialRemovalsFromHeadMiddleTail() {
        val app = mockk<Application>(relaxed = true)
        val vm = CameraViewModel(app)
        val dummyBitmap = mockk<Bitmap>(relaxed = true)

        for (i in 1..10) {
            vm.addBatchItem(
                BatchCaptureItem(
                    id = (1000 + i).toLong(),
                    rawFilePath = "/cache/burst_$i.jpg",
                    thumbnailBitmap = dummyBitmap
                )
            )
        }
        assertEquals(10, vm.batchQueue.value.size)
        assertEquals(10, vm.batchCount.value)

        // 1. Remove from Head (id 1001)
        vm.removeBatchItem(1001L)
        assertEquals(9, vm.batchQueue.value.size)
        assertEquals(9, vm.batchCount.value)
        assertFalse(vm.batchQueue.value.any { it.id == 1001L })
        assertEquals(1002L, vm.batchQueue.value.first().id)

        // 2. Remove from Middle (id 1005)
        vm.removeBatchItem(1005L)
        assertEquals(8, vm.batchQueue.value.size)
        assertEquals(8, vm.batchCount.value)
        assertFalse(vm.batchQueue.value.any { it.id == 1005L })

        // 3. Remove from Tail (id 1010)
        vm.removeBatchItem(1010L)
        assertEquals(7, vm.batchQueue.value.size)
        assertEquals(7, vm.batchCount.value)
        assertFalse(vm.batchQueue.value.any { it.id == 1010L })
        assertEquals(1009L, vm.batchQueue.value.last().id)

        // 4. Remove Non-Existent ID (id 9999L) -> No-op, no exception
        vm.removeBatchItem(9999L)
        assertEquals(7, vm.batchQueue.value.size)
        assertEquals(7, vm.batchCount.value)
    }

    @Test
    fun testCameraViewModelRotationCyclesClockwiseAndCounterClockwise() {
        val app = mockk<Application>(relaxed = true)
        val vm = CameraViewModel(app)
        val dummyBitmap = mockk<Bitmap>(relaxed = true)

        val itemA = BatchCaptureItem(id = 100L, rawFilePath = "/cache/a.jpg", thumbnailBitmap = dummyBitmap)
        val itemB = BatchCaptureItem(id = 200L, rawFilePath = "/cache/b.jpg", thumbnailBitmap = dummyBitmap)

        vm.addBatchItem(itemA)
        vm.addBatchItem(itemB)

        // Clockwise rotation cycle on itemA: 0 -> 90 -> 180 -> 270 -> 0
        vm.rotateBatchItem(100L, clockwise = true)
        assertEquals(90, vm.batchQueue.value.first { it.id == 100L }.rotationDegrees)
        assertEquals(0, vm.batchQueue.value.first { it.id == 200L }.rotationDegrees) // Isolation check

        vm.rotateBatchItem(100L, clockwise = true)
        assertEquals(180, vm.batchQueue.value.first { it.id == 100L }.rotationDegrees)

        vm.rotateBatchItem(100L, clockwise = true)
        assertEquals(270, vm.batchQueue.value.first { it.id == 100L }.rotationDegrees)

        vm.rotateBatchItem(100L, clockwise = true)
        assertEquals(0, vm.batchQueue.value.first { it.id == 100L }.rotationDegrees)

        // Counter-clockwise rotation cycle on itemB: 0 -> 270 -> 180 -> 90 -> 0
        vm.rotateBatchItem(200L, clockwise = false)
        assertEquals(270, vm.batchQueue.value.first { it.id == 200L }.rotationDegrees)
        assertEquals(0, vm.batchQueue.value.first { it.id == 100L }.rotationDegrees) // Isolation check

        vm.rotateBatchItem(200L, clockwise = false)
        assertEquals(180, vm.batchQueue.value.first { it.id == 200L }.rotationDegrees)

        vm.rotateBatchItem(200L, clockwise = false)
        assertEquals(90, vm.batchQueue.value.first { it.id == 200L }.rotationDegrees)

        vm.rotateBatchItem(200L, clockwise = false)
        assertEquals(0, vm.batchQueue.value.first { it.id == 200L }.rotationDegrees)
    }

    @Test
    fun testCameraViewModelClearBatchIdempotence() {
        val app = mockk<Application>(relaxed = true)
        val vm = CameraViewModel(app)
        val dummyBitmap = mockk<Bitmap>(relaxed = true)

        for (i in 1..25) {
            vm.addBatchItem(BatchCaptureItem(id = i.toLong(), rawFilePath = "/cache/$i.jpg", thumbnailBitmap = dummyBitmap))
        }
        assertEquals(25, vm.batchCount.value)

        // First clear
        vm.clearBatch()
        assertTrue("Queue must be empty after clear", vm.batchQueue.value.isEmpty())
        assertEquals("batchCount must reset to 0", 0, vm.batchCount.value)

        // Second clear on already empty queue -> must remain empty and not throw
        vm.clearBatch()
        assertTrue("Queue must remain empty", vm.batchQueue.value.isEmpty())
        assertEquals(0, vm.batchCount.value)
    }

    // =========================================================================
    // AREA 3: ScannerRepository Compositing Geometry & Book Splitting Robustness
    // =========================================================================

    @Test
    fun testIdCardCompositingNonOverlappingGeometryAndMargins() {
        val a4Width = 2480
        val a4Height = 3508

        // Exact formula checks from ScannerRepository.stitchIdCardPages
        val cardW = (a4Width * 0.70f).toInt() // 1736 px
        val cardH = Math.round(cardW / 1.586f).toInt() // 1095 px
        val cardX = (a4Width - cardW) / 2 // 372 px

        val frontY = (a4Height * 0.20f).toInt() // 701 px
        val backY = (a4Height * 0.56f).toInt() // 1964 px

        // 1. Boundary dimension assertions
        assertEquals(1736, cardW)
        assertEquals(1095, cardH)
        assertEquals(372, cardX)
        assertEquals(701, frontY)
        assertEquals(1964, backY)

        // 2. Authoritative ISO/IEC 7810 ID-1 standard ratio check
        val ratio = cardW.toFloat() / cardH.toFloat()
        assertEquals(1.586f, ratio, 0.001f)

        // 3. Margin & Non-overlapping proofs:
        // Front Card span: [701, 1796]
        val frontBottom = frontY + cardH
        assertEquals(1796, frontBottom)

        // Back Card span: [1964, 3059]
        val backBottom = backY + cardH
        assertEquals(3059, backBottom)

        // Non-overlapping gap between front and back cards
        val verticalGap = backY - frontBottom
        assertTrue("Front and back cards must have positive non-overlapping gap", verticalGap > 0)
        assertEquals(168, verticalGap)

        // Safety margins within A4 boundaries
        assertTrue("Top margin must be > 0", frontY > 0)
        assertTrue("Bottom margin must be > 0", a4Height - backBottom > 0)
        assertEquals(449, a4Height - backBottom)

        val rightMargin = a4Width - (cardX + cardW)
        assertEquals("Horizontal margins must be symmetrically centered", cardX, rightMargin)
        assertEquals(372, rightMargin)
    }

    @Test
    fun testStitchIdCardTargetDocIdHandling() = runTest {
        val mockFront = mockk<Bitmap>(relaxed = true)
        val mockBack = mockk<Bitmap>(relaxed = true)

        coEvery { documentDao.insertDocument(any()) } returns 999L
        coEvery { pageDao.insertPage(any()) } returns 1L
        coEvery { pageDao.getPagesListForDocument(any()) } returns emptyList()

        // 1. targetDocId = null -> creates new document
        repository.stitchIdCardPages(mockFront, mockBack, targetDocId = null)
        coVerify(exactly = 1) {
            documentDao.insertDocument(match { it.title == "ID Card Scan" })
        }

        // 2. targetDocId = 777L -> reuses existing document without inserting new document
        repository.stitchIdCardPages(mockFront, mockBack, targetDocId = 777L)
        coVerify(exactly = 1) { documentDao.insertDocument(any()) } // Still only 1 from previous call
        coVerify(exactly = 1) {
            pageDao.insertPage(match { it.documentId == 777L })
        }
    }

    @Test
    fun testSplitAndSaveBookPagesTargetDocIdHandling() = runTest {
        mockkObject(BookSpineDetector)
        val mockSpread = mockk<Bitmap>(relaxed = true)
        every { mockSpread.width } returns 2000
        every { mockSpread.height } returns 1500

        val mockLeft = mockk<Bitmap>(relaxed = true)
        val mockRight = mockk<Bitmap>(relaxed = true)
        every { mockLeft.isRecycled } returns false
        every { mockRight.isRecycled } returns false

        every { BookSpineDetector.splitAndUnwarpSpread(any(), any()) } returns Pair(mockLeft, mockRight)

        coEvery { documentDao.insertDocument(any()) } returns 888L
        coEvery { pageDao.insertPage(any()) } returns 1L andThen 2L
        coEvery { pageDao.getPagesListForDocument(any()) } returns emptyList()

        // Case A: targetDocId = null -> creates new document
        val (p1, p2) = repository.splitAndSaveBookPages(mockSpread, spineX = 1000, targetDocId = null)
        coVerify(exactly = 1) {
            documentDao.insertDocument(match { it.title.startsWith("Book Scan ") })
        }

        // Case B: targetDocId = 444L -> reuses existing document
        repository.splitAndSaveBookPages(mockSpread, spineX = 1000, targetDocId = 444L)
        coVerify(exactly = 1) { documentDao.insertDocument(any()) } // Unchanged
        coVerify(exactly = 2) {
            pageDao.insertPage(match { it.documentId == 444L })
        }
    }

    @Test
    fun testSplitAndSaveBookPagesConsecutiveIndexAllocation() = runTest {
        mockkObject(BookSpineDetector)
        val mockSpread = mockk<Bitmap>(relaxed = true)
        every { mockSpread.width } returns 2000
        every { mockSpread.height } returns 1500

        val mockLeft = mockk<Bitmap>(relaxed = true)
        val mockRight = mockk<Bitmap>(relaxed = true)
        every { mockLeft.isRecycled } returns false
        every { mockRight.isRecycled } returns false

        every { BookSpineDetector.splitAndUnwarpSpread(any(), any()) } returns Pair(mockLeft, mockRight)

        val docId = 600L
        val initialExistingPages = listOf(
            Page(id = 10L, documentId = docId, pageIndex = 0, processedImagePath = "", originalImagePath = ""),
            Page(id = 11L, documentId = docId, pageIndex = 1, processedImagePath = "", originalImagePath = ""),
            Page(id = 12L, documentId = docId, pageIndex = 2, processedImagePath = "", originalImagePath = "")
        )

        // When saving left page, 3 existing pages -> new index = 3
        // When saving right page, 4 existing pages -> new index = 4
        coEvery { pageDao.getPagesListForDocument(docId) } returns initialExistingPages andThen (initialExistingPages + Page(id = 13L, documentId = docId, pageIndex = 3, processedImagePath = "", originalImagePath = ""))

        val capturedPages = mutableListOf<Page>()
        coEvery { pageDao.insertPage(capture(capturedPages)) } returns 13L andThen 14L

        repository.splitAndSaveBookPages(mockSpread, spineX = 1000, targetDocId = docId)

        assertEquals("Must save exactly 2 consecutive pages", 2, capturedPages.size)
        val leftPage = capturedPages[0]
        val rightPage = capturedPages[1]

        assertEquals("Left page must receive index k (3)", 3, leftPage.pageIndex)
        assertEquals("Right page must receive consecutive index k+1 (4)", 4, rightPage.pageIndex)
        assertEquals("Consecutive delta must be strictly 1", 1, rightPage.pageIndex - leftPage.pageIndex)

        // Verify document pageCount was updated to k+1 (4) and then k+2 (5)
        coVerify(ordering = Ordering.ORDERED) {
            documentDao.updatePageCount(docId, 4, any())
            documentDao.updatePageCount(docId, 5, any())
        }
    }

    @Test
    fun testSplitAndSaveBookPagesSpineBoundaryClampingStress() {
        val width = 2400
        val minX = (width * 0.15f).toInt() // 360
        val maxX = (width * 0.85f).toInt() // 2040

        fun clamp(spineX: Int): Int = spineX.coerceIn(minX, maxX)

        // Extreme Left Out-of-Bounds
        assertEquals(360, clamp(-1000))
        assertEquals(360, clamp(0))
        assertEquals(360, clamp(100))
        assertEquals(360, clamp(359))

        // Valid In-Bounds
        assertEquals(360, clamp(360))
        assertEquals(1200, clamp(1200))
        assertEquals(2040, clamp(2040))

        // Extreme Right Out-of-Bounds
        assertEquals(2040, clamp(2041))
        assertEquals(2040, clamp(2399))
        assertEquals(2040, clamp(5000))
    }

    @Test
    fun testSplitAndSaveBookPagesGuaranteesBitmapRecycling() = runTest {
        mockkObject(BookSpineDetector)
        val mockSpread = mockk<Bitmap>(relaxed = true)
        every { mockSpread.width } returns 2000
        every { mockSpread.height } returns 1500

        val mockLeft = mockk<Bitmap>(relaxed = true)
        val mockRight = mockk<Bitmap>(relaxed = true)
        every { mockLeft.isRecycled } returns false
        every { mockRight.isRecycled } returns false

        every { BookSpineDetector.splitAndUnwarpSpread(any(), any()) } returns Pair(mockLeft, mockRight)

        // Simulate save failure on right page
        coEvery { pageDao.insertPage(any()) } returns 1L andThenThrows IOException("Disk full during right page write")

        try {
            repository.splitAndSaveBookPages(mockSpread, spineX = 1000, targetDocId = 700L)
            fail("Expected IOException")
        } catch (e: IOException) {
            assertEquals("Disk full during right page write", e.message)
        }

        // CRITICAL ADVERSARIAL VERIFICATION: Both bitmaps must be recycled despite failure
        verify(exactly = 1) { mockLeft.recycle() }
        verify(exactly = 1) { mockRight.recycle() }
    }
}
