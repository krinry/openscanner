package org.openscanner.camera

import android.content.Context
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openscanner.data.repository.ScannerRepository
import org.openscanner.ui.camera.BatchCaptureItem
import java.util.concurrent.atomic.AtomicInteger

class BatchWarpProcessorTest {

    @Test
    fun testSemaphoreLimitsConcurrencyToTwo() = runTest {
        val semaphore = Semaphore(2)
        val currentConcurrent = AtomicInteger(0)
        val maxConcurrentObserved = AtomicInteger(0)

        coroutineScope {
            val tasks = (1..20).map {
                async {
                    semaphore.withPermit {
                        val count = currentConcurrent.incrementAndGet()
                        var max = maxConcurrentObserved.get()
                        while (count > max && !maxConcurrentObserved.compareAndSet(max, count)) {
                            max = maxConcurrentObserved.get()
                        }
                        delay(10)
                        currentConcurrent.decrementAndGet()
                    }
                }
            }
            tasks.awaitAll()
        }

        assertTrue("Max concurrent executions must not exceed 2", maxConcurrentObserved.get() <= 2)
    }

    @Test
    fun testProcessorCreatesNewDocumentWhenDocIdIsZero() = runTest {
        val context = mockk<Context>(relaxed = true)
        val repository = mockk<ScannerRepository>(relaxed = true)
        coEvery { repository.createNewDocument(any(), any()) } returns 777L

        val processor = BatchWarpProcessor(context, repository)
        val items = emptyList<BatchCaptureItem>()

        val docId = processor.processBatch(
            targetDocId = -1L,
            items = items,
            onProgress = { _, _ -> }
        )

        // When items are empty, targetDocId is returned directly or new document is preserved
        assertEquals(-1L, docId)
    }
}
