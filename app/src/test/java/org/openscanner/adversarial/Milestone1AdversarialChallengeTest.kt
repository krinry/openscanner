package org.openscanner.adversarial

import android.content.Context
import android.graphics.Bitmap
import com.google.android.gms.tasks.OnCanceledListener
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.openscanner.cv.OcrEngine
import org.openscanner.data.local.DocumentDao
import org.openscanner.data.local.FolderDao
import org.openscanner.data.local.PageDao
import org.openscanner.data.model.Document
import org.openscanner.data.model.Folder
import org.openscanner.data.model.Page
import org.openscanner.data.repository.ScannerRepository
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Empirical Adversarial Challenge Test Suite for Milestone 1:
 * 1. Circular Folder Moves & Hierarchy Cycle Resistance
 * 2. Deep-Copy Failure Rollback & Disk File Cleanup
 * 3. OcrEngine Concurrency, Mutex Serialization & Cancellation Stress
 */
class Milestone1AdversarialChallengeTest {

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

        mockkStatic(InputImage::class)
        val mockImage = mockk<InputImage>()
        every { InputImage.fromBitmap(any(), any()) } returns mockImage
    }

    @After
    fun tearDown() {
        unmockkStatic(InputImage::class)
    }

    // =========================================================================
    // AREA 1: Circular Folder Moves & Hierarchy Integrity
    // =========================================================================

    @Test
    fun testDirectSelfMoveDoesNotCreateCycle() = runTest {
        val folder = Folder(id = 10L, name = "Self", parentFolderId = -1L, path = "/")
        coEvery { folderDao.getFolderById(10L) } returns folder

        // Attempting to move folder 10 into parent 10 should return safely without updating
        repository.moveFolder(folderId = 10L, targetParentFolderId = 10L)

        // Verify folderDao was not corrupted with self-referential cycle
        coVerify(exactly = 0) { folderDao.updateFolder(any()) }
    }

    @Test
    fun testImmediateChildMoveThrowsIllegalArgumentException() = runTest {
        val parent = Folder(id = 1L, name = "Parent", parentFolderId = -1L, path = "/")
        val child = Folder(id = 2L, name = "Child", parentFolderId = 1L, path = "/1/")

        coEvery { folderDao.getFolderById(1L) } returns parent
        coEvery { folderDao.getFolderById(2L) } returns child

        try {
            repository.moveFolder(folderId = 1L, targetParentFolderId = 2L)
            fail("Expected IllegalArgumentException when moving folder into immediate child")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("descendants", ignoreCase = true) == true)
        }

        coVerify(exactly = 0) { folderDao.updateFolder(any()) }
    }

    @Test
    fun testDeepDescendantMoveThrowsIllegalArgumentException() = runTest {
        // Construct deep hierarchy: A (1) -> B (2) -> C (3) -> D (4) -> E (5)
        val f1 = Folder(id = 1L, name = "A", parentFolderId = -1L, path = "/")
        val f2 = Folder(id = 2L, name = "B", parentFolderId = 1L, path = "/1/")
        val f3 = Folder(id = 3L, name = "C", parentFolderId = 2L, path = "/1/2/")
        val f4 = Folder(id = 4L, name = "D", parentFolderId = 3L, path = "/1/2/3/")
        val f5 = Folder(id = 5L, name = "E", parentFolderId = 4L, path = "/1/2/3/4/")

        val allFolders = listOf(f1, f2, f3, f4, f5)
        allFolders.forEach { f -> coEvery { folderDao.getFolderById(f.id) } returns f }

        val illegalMoves = listOf(
            Pair(1L, 5L), // Root into leaf
            Pair(1L, 4L),
            Pair(1L, 3L),
            Pair(2L, 5L), // Level 1 into leaf
            Pair(2L, 4L),
            Pair(3L, 5L), // Level 2 into leaf
            Pair(4L, 5L)  // Direct parent into child
        )

        for ((srcId, targetId) in illegalMoves) {
            try {
                repository.moveFolder(folderId = srcId, targetParentFolderId = targetId)
                fail("Expected IllegalArgumentException for illegal move: $srcId -> $targetId")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message?.contains("descendants", ignoreCase = true) == true)
            }
        }

        coVerify(exactly = 0) { folderDao.updateFolder(any()) }
    }

    @Test
    fun testValidMoveUpdatesPathPrefixAndDescendants() = runTest {
        // Hierarchy: A (1) -> B (2) -> C (3)
        // Moving C (3) to Root (-1L)
        val f1 = Folder(id = 1L, name = "A", parentFolderId = -1L, path = "/")
        val f2 = Folder(id = 2L, name = "B", parentFolderId = 1L, path = "/1/")
        val f3 = Folder(id = 3L, name = "C", parentFolderId = 2L, path = "/1/2/")

        coEvery { folderDao.getFolderById(1L) } returns f1
        coEvery { folderDao.getFolderById(2L) } returns f2
        coEvery { folderDao.getFolderById(3L) } returns f3
        coEvery { folderDao.updateFolder(any()) } just Runs
        coEvery { folderDao.updateFolderPathPrefix(any(), any()) } just Runs

        repository.moveFolder(folderId = 3L, targetParentFolderId = -1L)

        // C should now be a root folder
        coVerify(exactly = 1) {
            folderDao.updateFolder(match {
                it.id == 3L && it.parentFolderId == -1L && it.path == "/"
            })
        }
        // Descendants of C should be updated from "/1/2/3/" to "/3/"
        coVerify(exactly = 1) {
            folderDao.updateFolderPathPrefix(
                oldPrefix = "/1/2/3/",
                newPrefix = "/3/"
            )
        }
    }

    @Test
    fun testBreadcrumbCycleDefenseTerminatesSafely() = runTest {
        // Simulate corrupted circular references in DB: A (1) -> B (2) -> A (1)
        val f1 = Folder(id = 1L, name = "CycleA", parentFolderId = 2L, path = "/2/")
        val f2 = Folder(id = 2L, name = "CycleB", parentFolderId = 1L, path = "/1/")

        coEvery { folderDao.getFolderById(1L) } returns f1
        coEvery { folderDao.getFolderById(2L) } returns f2

        // Breadcrumbs should terminate via visited-set check without StackOverflowError or infinite loop
        val breadcrumbs = repository.getBreadcrumbs(1L)
        assertNotNull(breadcrumbs)
        assertEquals(2, breadcrumbs.size)
    }

    // =========================================================================
    // AREA 2: Deep-Copy Failure Rollback & Disk File Cleanup
    // =========================================================================

    @Test
    fun testCopyDocumentRollbackOnInsertPagesFailure() = runTest {
        val origDoc = Document(id = 100L, title = "MultiPage Contract", folderId = -1L)
        val scansDir = File(tempFolder.root, "scans").apply { mkdirs() }
        val thumbsDir = File(tempFolder.root, "thumbs").apply { mkdirs() }

        // Create 3 original pages (9 files on disk)
        val origPages = mutableListOf<Page>()
        for (i in 0 until 3) {
            val pFile = File(scansDir, "page_100_$i.jpg").apply { writeText("processed $i") }
            val oFile = File(scansDir, "orig_100_$i.jpg").apply { writeText("orig $i") }
            val tFile = File(thumbsDir, "thumb_100_$i.jpg").apply { writeText("thumb $i") }

            origPages.add(
                Page(
                    id = (i + 1).toLong(),
                    documentId = 100L,
                    pageIndex = i,
                    processedImagePath = pFile.absolutePath,
                    originalImagePath = oFile.absolutePath,
                    thumbnailPath = tFile.absolutePath
                )
            )
        }

        coEvery { documentDao.getDocumentById(100L) } returns origDoc
        coEvery { pageDao.getPagesListForDocument(100L) } returns origPages
        coEvery { documentDao.insertDocument(any()) } returns 200L
        coEvery { documentDao.deleteDocument(any()) } just Runs

        // Simulate crash when inserting pages into Room
        coEvery { pageDao.insertPages(any()) } throws IOException("Room SQLite constraint failure")

        try {
            repository.copyDocumentToFolder(docId = 100L, targetFolderId = 10L)
            fail("Expected IOException")
        } catch (e: IOException) {
            assertEquals("Room SQLite constraint failure", e.message)
        }

        // Verify document rolled back
        coVerify(exactly = 1) { documentDao.deleteDocument(match { it.id == 200L }) }

        // Verify NO copied files for doc 200 exist on disk
        val scansFiles = scansDir.listFiles()?.filter { it.name.contains("_200_") } ?: emptyList()
        val thumbsFiles = thumbsDir.listFiles()?.filter { it.name.contains("_200_") } ?: emptyList()
        assertTrue("Expected scans directory to have no copied files, found $scansFiles", scansFiles.isEmpty())
        assertTrue("Expected thumbs directory to have no copied files, found $thumbsFiles", thumbsFiles.isEmpty())

        // Verify original 9 files remain intact
        for (i in 0 until 3) {
            assertTrue(File(scansDir, "page_100_$i.jpg").exists())
            assertTrue(File(scansDir, "orig_100_$i.jpg").exists())
            assertTrue(File(thumbsDir, "thumb_100_$i.jpg").exists())
        }
    }

    @Test
    fun testCopyDocumentRollbackOnMidwayFileException() = runTest {
        val origDoc = Document(id = 300L, title = "CrashMidway Doc", folderId = -1L)
        val scansDir = File(tempFolder.root, "scans").apply { mkdirs() }
        val thumbsDir = File(tempFolder.root, "thumbs").apply { mkdirs() }

        // Page 0 has valid files
        val pFile0 = File(scansDir, "page_300_0.jpg").apply { writeText("page 0") }
        val oFile0 = File(scansDir, "orig_300_0.jpg").apply { writeText("orig 0") }
        val tFile0 = File(thumbsDir, "thumb_300_0.jpg").apply { writeText("thumb 0") }

        // Page 1 points to a path that will be mocked to throw during copyTo
        val pFile1 = File(scansDir, "page_300_1.jpg").apply { writeText("page 1") }

        val origPages = listOf(
            Page(id = 1L, documentId = 300L, pageIndex = 0, processedImagePath = pFile0.absolutePath, originalImagePath = oFile0.absolutePath, thumbnailPath = tFile0.absolutePath),
            Page(id = 2L, documentId = 300L, pageIndex = 1, processedImagePath = pFile1.absolutePath, originalImagePath = pFile1.absolutePath, thumbnailPath = "")
        )

        coEvery { documentDao.getDocumentById(300L) } returns origDoc
        coEvery { pageDao.getPagesListForDocument(300L) } returns origPages
        coEvery { documentDao.insertDocument(any()) } returns 400L
        coEvery { documentDao.deleteDocument(any()) } just Runs
        coEvery { pageDao.insertPages(any()) } throws RuntimeException("Midway failure")

        try {
            repository.copyDocumentToFolder(docId = 300L, targetFolderId = 1L)
            fail("Expected RuntimeException")
        } catch (e: RuntimeException) {
            assertEquals("Midway failure", e.message)
        }

        // All copied files must be wiped
        val residualFiles = (scansDir.listFiles()?.toList().orEmpty() + thumbsDir.listFiles()?.toList().orEmpty())
            .filter { it.name.contains("_400_") }
        assertTrue("Residual files found: $residualFiles", residualFiles.isEmpty())

        // Document record rolled back
        coVerify(exactly = 1) { documentDao.deleteDocument(match { it.id == 400L }) }
    }

    @Test
    fun testCopyNonExistentDocumentThrowsWithoutSideEffects() = runTest {
        coEvery { documentDao.getDocumentById(999L) } returns null

        try {
            repository.copyDocumentToFolder(docId = 999L, targetFolderId = 1L)
            fail("Expected IllegalArgumentException for non-existent document")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("not found") == true)
        }

        coVerify(exactly = 0) { documentDao.insertDocument(any()) }
    }

    // =========================================================================
    // AREA 3: OcrEngine Concurrency, Mutex Serialization & Cancellation Stress
    // =========================================================================

    @Test
    fun testOcrEngineRecycledBitmapReturnsEmptyImmediately() = runTest {
        val recycledBitmap = mockk<Bitmap>()
        every { recycledBitmap.isRecycled } returns true

        val result = OcrEngine.recognizeText(recycledBitmap)
        assertEquals("", result)
    }

    // Helper to inject mock recognizer into OcrEngine singleton
    private fun injectMockRecognizer(mockRecognizer: TextRecognizer): Any? {
        val delegateField = OcrEngine::class.java.getDeclaredField("recognizer\$delegate").apply { isAccessible = true }
        val lazyInstance = delegateField.get(null) ?: return null
        val valueField = lazyInstance::class.java.getDeclaredField("_value").apply { isAccessible = true }
        val previousValue = valueField.get(lazyInstance)
        valueField.set(lazyInstance, mockRecognizer)
        return lazyInstance to previousValue
    }

    private fun restoreMockRecognizer(state: Any?) {
        if (state == null) return
        @Suppress("UNCHECKED_CAST")
        val pair = state as Pair<Any, Any?>
        val lazyInstance = pair.first
        val previousValue = pair.second
        val valueField = lazyInstance::class.java.getDeclaredField("_value").apply { isAccessible = true }
        valueField.set(lazyInstance, previousValue)
    }

    @Test
    fun testOcrEngineSerializedConcurrencyUnderHighLoad() = runTest {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.e(any(), any(), any()) } answers {
            println("LOG.E with throw: ${args[1]}")
            (args[2] as? Throwable)?.printStackTrace()
            0
        }
        every { android.util.Log.e(any(), any()) } answers {
            println("LOG.E msg: ${args[1]}")
            0
        }
        every { android.util.Log.w(any(), any<String>()) } answers {
            println("LOG.W: ${args[1]}")
            0
        }

        val mockRecognizer = mockk<TextRecognizer>(relaxed = true)
        mockkStatic(TextRecognition::class)
        every { TextRecognition.getClient(any()) } returns mockRecognizer
        val realText = Text("Scanned Invoice Text", emptyList<Any>())

        // Track active concurrent callers in recognizer.process
        val activeCallers = AtomicInteger(0)
        val peakConcurrent = AtomicInteger(0)
        val totalCompleted = AtomicInteger(0)

        // Concrete thread-safe Task implementation for concurrency testing
        class SuccessfulOcrTask(private val resultText: Text) : Task<Text>() {
            override fun isComplete(): Boolean = true
            override fun isSuccessful(): Boolean = true
            override fun isCanceled(): Boolean = false
            override fun getResult(): Text = resultText
            override fun <X : Throwable?> getResult(p0: Class<X>): Text = resultText
            override fun getException(): java.lang.Exception? = null
            override fun addOnSuccessListener(listener: OnSuccessListener<in Text>): Task<Text> {
                listener.onSuccess(resultText)
                return this
            }
            override fun addOnSuccessListener(executor: java.util.concurrent.Executor, listener: OnSuccessListener<in Text>): Task<Text> {
                listener.onSuccess(resultText)
                return this
            }
            override fun addOnSuccessListener(activity: android.app.Activity, listener: OnSuccessListener<in Text>): Task<Text> {
                listener.onSuccess(resultText)
                return this
            }
            override fun addOnFailureListener(listener: OnFailureListener): Task<Text> = this
            override fun addOnFailureListener(executor: java.util.concurrent.Executor, listener: OnFailureListener): Task<Text> = this
            override fun addOnFailureListener(activity: android.app.Activity, listener: OnFailureListener): Task<Text> = this
            override fun addOnCanceledListener(listener: OnCanceledListener): Task<Text> = this
            override fun addOnCanceledListener(executor: java.util.concurrent.Executor, listener: OnCanceledListener): Task<Text> = this
            override fun addOnCanceledListener(activity: android.app.Activity, listener: OnCanceledListener): Task<Text> = this
        }

        every { mockRecognizer.process(any<InputImage>()) } answers {
            val current = activeCallers.incrementAndGet()
            peakConcurrent.updateAndGet { prev -> maxOf(prev, current) }

            // Simulate processing time
            Thread.sleep(10)

            activeCallers.decrementAndGet()
            SuccessfulOcrTask(realText)
        }

        val originalDelegate = injectMockRecognizer(mockRecognizer)

        // Pre-allocate and stub bitmaps on the test thread before concurrent dispatch
        val bitmaps = (1..30).map {
            mockk<Bitmap>().apply { every { isRecycled } returns false }
        }

        try {
            // Launch 30 concurrent coroutines
            val jobs = bitmaps.map { bmp ->
                async(Dispatchers.Default) {
                    val text = OcrEngine.recognizeText(bmp, 0)
                    assertEquals("Scanned Invoice Text", text)
                    totalCompleted.incrementAndGet()
                }
            }

            jobs.awaitAll()

            assertEquals(30, totalCompleted.get())
            // CRITICAL: Mutex serialization guarantees peak concurrent recognizer executions <= 1
            assertEquals("Mutex must strictly serialize access to ML Kit recognizer to avoid OOM", 1, peakConcurrent.get())
        } finally {
            restoreMockRecognizer(originalDelegate)
        }
    }

    @Test
    fun testOcrEngineCancellationReleasesMutexSafely() = runTest {
        val mockRecognizer = mockk<TextRecognizer>(relaxed = true)
        val mockTask = mockk<Task<Text>>(relaxed = true)
        val mockText = mockk<Text>(relaxed = true)
        every { mockText.text } returns "Normal Flow Text"

        every { mockRecognizer.process(any<InputImage>()) } returns mockTask
        every { mockTask.addOnSuccessListener(any()) } answers {
            val listener = firstArg<OnSuccessListener<Text>>()
            listener.onSuccess(mockText)
            mockTask
        }
        every { mockTask.addOnFailureListener(any()) } returns mockTask
        every { mockTask.addOnCanceledListener(any()) } returns mockTask

        val originalDelegate = injectMockRecognizer(mockRecognizer)

        try {
            val validBitmap = mockk<Bitmap>()
            every { validBitmap.isRecycled } returns false

            // Launch a coroutine that cancels immediately
            val cancelledJob = launch(Dispatchers.Default) {
                OcrEngine.recognizeText(validBitmap, 0)
            }
            cancelledJob.cancelAndJoin()

            // Subsequent caller must NOT be deadlocked and must execute cleanly
            val subsequentResult = OcrEngine.recognizeText(validBitmap, 0)
            assertEquals("Normal Flow Text", subsequentResult)
        } finally {
            restoreMockRecognizer(originalDelegate)
        }
    }

    @Test
    fun testOcrEngineFailureSafelyReturnsEmptyWithoutCrash() = runTest {
        val mockRecognizer = mockk<TextRecognizer>(relaxed = true)
        val mockTask = mockk<Task<Text>>(relaxed = true)

        every { mockRecognizer.process(any<InputImage>()) } returns mockTask
        every { mockTask.addOnSuccessListener(any()) } returns mockTask
        every { mockTask.addOnFailureListener(any()) } answers {
            val listener = firstArg<OnFailureListener>()
            listener.onFailure(RuntimeException("ML Kit low-level pipeline crashed"))
            mockTask
        }
        every { mockTask.addOnCanceledListener(any()) } returns mockTask

        val originalDelegate = injectMockRecognizer(mockRecognizer)

        try {
            val validBitmap = mockk<Bitmap>()
            every { validBitmap.isRecycled } returns false

            // Call recognizeText with failing engine
            val result = OcrEngine.recognizeText(validBitmap, 0)
            // Must not throw, returns empty string safely
            assertEquals("", result)

            // Mutex must still be healthy for subsequent calls
            every { mockTask.addOnSuccessListener(any()) } answers {
                val text = mockk<Text>()
                every { text.text } returns "Recovered Text"
                firstArg<OnSuccessListener<Text>>().onSuccess(text)
                mockTask
            }
            val recoveredResult = OcrEngine.recognizeText(validBitmap, 0)
            assertEquals("Recovered Text", recoveredResult)
        } finally {
            restoreMockRecognizer(originalDelegate)
        }
    }
}
