package org.openscanner.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.openscanner.cv.CollageGenerator
import org.openscanner.cv.CollageLayout
import org.openscanner.data.local.DocumentDao
import org.openscanner.data.local.FolderDao
import org.openscanner.data.local.PageDao
import org.openscanner.data.model.Document
import org.openscanner.data.model.Folder
import org.openscanner.data.model.Page
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class ScannerRepository(
    private val context: Context,
    private val documentDao: DocumentDao,
    private val pageDao: PageDao,
    private val folderDao: FolderDao
) {

    // --- Document Queries ---
    fun getAllDocuments(): Flow<List<Document>> = documentDao.getAllDocuments()

    fun getRootDocuments(): Flow<List<Document>> = documentDao.getRootDocuments()

    fun getDocumentsInFolder(folderId: Long): Flow<List<Document>> = documentDao.getDocumentsInFolder(folderId)

    fun searchDocuments(query: String): Flow<List<Document>> = documentDao.searchDocuments(query)

    suspend fun getDocumentById(id: Long): Document? = documentDao.getDocumentById(id)

    // --- Folder Queries & Mutations ---
    fun getAllFolders(): Flow<List<Folder>> = folderDao.getAllFolders()

    suspend fun createFolder(name: String, color: String = "#EEAA3B"): Long = withContext(Dispatchers.IO) {
        val folder = Folder(name = name.trim(), color = color)
        folderDao.insertFolder(folder)
    }

    suspend fun renameFolder(folderId: Long, newName: String) = withContext(Dispatchers.IO) {
        val folder = folderDao.getFolderById(folderId) ?: return@withContext
        folderDao.updateFolder(folder.copy(name = newName.trim()))
    }

    suspend fun deleteFolder(folder: Folder) = withContext(Dispatchers.IO) {
        documentDao.unlinkDocumentsFromFolder(folder.id)
        folderDao.deleteFolder(folder)
    }

    suspend fun getFolderDocCount(folderId: Long): Int = withContext(Dispatchers.IO) {
        documentDao.getFolderDocCount(folderId)
    }

    suspend fun moveDocumentToFolder(docId: Long, folderId: Long) = withContext(Dispatchers.IO) {
        documentDao.updateDocumentFolder(docId, folderId)
    }

    suspend fun batchMoveToFolder(docIds: List<Long>, folderId: Long) = withContext(Dispatchers.IO) {
        documentDao.batchUpdateFolder(docIds, folderId)
    }

    suspend fun batchDeleteDocuments(docIds: List<Long>) = withContext(Dispatchers.IO) {
        for (id in docIds) {
            val pages = pageDao.getPagesListForDocument(id)
            pages.forEach { p ->
                File(p.processedImagePath).delete()
                File(p.originalImagePath).delete()
                File(p.thumbnailPath).delete()
            }
        }
        documentDao.batchDeleteDocuments(docIds)
    }

    // --- Page Operations ---
    fun getPagesForDocument(docId: Long): Flow<List<Page>> = pageDao.getPagesForDocument(docId)

    suspend fun getPagesListForDocument(docId: Long): List<Page> = pageDao.getPagesListForDocument(docId)

    suspend fun createNewDocument(title: String? = null, folderId: Long = -1L): Long = withContext(Dispatchers.IO) {
        val defaultTitle = title ?: ("New Doc " + SimpleDateFormat("dd-MM-yyyy HH.mm", Locale.getDefault()).format(Date()))
        val doc = Document(title = defaultTitle, folderId = folderId)
        documentDao.insertDocument(doc)
    }

    suspend fun savePage(
        documentId: Long,
        processedBitmap: Bitmap,
        originalBitmap: Bitmap?,
        cropBorderJson: String,
        enhanceModeId: Int
    ): Page = withContext(Dispatchers.IO) {
        val scansDir = File(context.filesDir, "scans").apply { if (!exists()) mkdirs() }
        val thumbsDir = File(context.filesDir, "thumbs").apply { if (!exists()) mkdirs() }

        val timestamp = System.currentTimeMillis()
        val processedFile = File(scansDir, "page_${documentId}_${timestamp}.jpg")
        val originalFile = File(scansDir, "orig_${documentId}_${timestamp}.jpg")
        val thumbFile = File(thumbsDir, "thumb_${documentId}_${timestamp}.jpg")

        // Save processed full image
        FileOutputStream(processedFile).use { out ->
            processedBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }

        // Save original photo (for re-cropping later)
        if (originalBitmap != null) {
            FileOutputStream(originalFile).use { out ->
                originalBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
        }

        // Generate and save thumbnail
        val thumb = Bitmap.createScaledBitmap(
            processedBitmap,
            240,
            (240f * processedBitmap.height / processedBitmap.width).toInt().coerceAtLeast(1),
            true
        )
        FileOutputStream(thumbFile).use { out ->
            thumb.compress(Bitmap.CompressFormat.JPEG, 80, out)
        }

        val existingPages = pageDao.getPagesListForDocument(documentId)
        val newPageIndex = existingPages.size

        val page = Page(
            documentId = documentId,
            pageIndex = newPageIndex,
            processedImagePath = processedFile.absolutePath,
            originalImagePath = originalFile.absolutePath,
            thumbnailPath = thumbFile.absolutePath,
            cropBorderJson = cropBorderJson,
            enhanceMode = enhanceModeId
        )

        val pageId = pageDao.insertPage(page)
        documentDao.updatePageCount(documentId, existingPages.size + 1)

        page.copy(id = pageId)
    }

    suspend fun updatePage(page: Page) = withContext(Dispatchers.IO) {
        pageDao.updatePage(page)
    }

    suspend fun updateDocument(document: Document) = withContext(Dispatchers.IO) {
        documentDao.updateDocument(document)
    }

    suspend fun updateDocumentTitle(docId: Long, title: String) = withContext(Dispatchers.IO) {
        val doc = documentDao.getDocumentById(docId)
        if (doc != null) {
            documentDao.updateDocument(doc.copy(title = title, modifiedTime = System.currentTimeMillis()))
        }
    }

    suspend fun stampSignatureOnPage(pageId: Long, signatureBitmap: Bitmap) = withContext(Dispatchers.IO) {
        val page = pageDao.getPageById(pageId) ?: return@withContext
        val processedFile = File(page.processedImagePath)
        if (!processedFile.exists()) return@withContext

        val origBitmap = BitmapFactory.decodeFile(processedFile.absolutePath) ?: return@withContext
        val mutable = origBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(mutable)

        val targetWidth = (mutable.width * 0.35f).toInt()
        val targetHeight = (targetWidth * signatureBitmap.height / signatureBitmap.width.toFloat()).toInt()
        val destRect = android.graphics.Rect(
            mutable.width - targetWidth - (mutable.width * 0.08f).toInt(),
            mutable.height - targetHeight - (mutable.height * 0.08f).toInt(),
            mutable.width - (mutable.width * 0.08f).toInt(),
            mutable.height - (mutable.height * 0.08f).toInt()
        )

        canvas.drawBitmap(signatureBitmap, null, destRect, null)

        FileOutputStream(processedFile).use { out ->
            mutable.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }

        val thumbFile = File(page.thumbnailPath)
        if (thumbFile.exists()) {
            val thumb = Bitmap.createScaledBitmap(mutable, 240, (240f * mutable.height / mutable.width).toInt().coerceAtLeast(1), true)
            FileOutputStream(thumbFile).use { out ->
                thumb.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
        }

        val doc = documentDao.getDocumentById(page.documentId)
        if (doc != null) {
            documentDao.updateDocument(doc.copy(modifiedTime = System.currentTimeMillis()))
        }
    }

    suspend fun createCollagePage(
        documentId: Long,
        pageIds: List<Long>,
        layout: CollageLayout
    ): Page = withContext(Dispatchers.IO) {
        val bitmaps = mutableListOf<Bitmap>()
        for (pId in pageIds) {
            val page = pageDao.getPageById(pId)
            if (page != null && File(page.processedImagePath).exists()) {
                val bmp = BitmapFactory.decodeFile(page.processedImagePath)
                if (bmp != null) {
                    bitmaps.add(bmp)
                }
            }
        }

        if (bitmaps.isEmpty()) {
            throw IllegalStateException("No valid page images found for collage")
        }

        val collageBitmap = CollageGenerator.createCollage(bitmaps, layout)
        savePage(
            documentId = documentId,
            processedBitmap = collageBitmap,
            originalBitmap = collageBitmap,
            cropBorderJson = "",
            enhanceModeId = 1
        )
    }

    suspend fun reorderPages(documentId: Long, orderedPages: List<Page>) = withContext(Dispatchers.IO) {
        orderedPages.forEachIndexed { index, page ->
            pageDao.updatePage(page.copy(pageIndex = index))
        }
        val doc = documentDao.getDocumentById(documentId)
        if (doc != null) {
            documentDao.updateDocument(doc.copy(modifiedTime = System.currentTimeMillis()))
        }
    }

    suspend fun stitchIdCardPages(
        frontBitmap: Bitmap,
        backBitmap: Bitmap,
        documentId: Long
    ): Page = withContext(Dispatchers.IO) {
        val a4Width = 2480
        val a4Height = 3508
        val a4Bitmap = Bitmap.createBitmap(a4Width, a4Height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(a4Bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)

        val cardW = (a4Width * 0.70f).toInt()
        val cardH = (cardW / 1.586f).toInt()
        val cardX = (a4Width - cardW) / 2

        val frontY = (a4Height * 0.20f).toInt()
        val frontRect = android.graphics.Rect(cardX, frontY, cardX + cardW, frontY + cardH)
        canvas.drawBitmap(frontBitmap, null, frontRect, null)

        val strokePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.LTGRAY
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 6f
        }
        canvas.drawRect(frontRect, strokePaint)

        val backY = (a4Height * 0.56f).toInt()
        val backRect = android.graphics.Rect(cardX, backY, cardX + cardW, backY + cardH)
        canvas.drawBitmap(backBitmap, null, backRect, null)
        canvas.drawRect(backRect, strokePaint)

        savePage(
            documentId = documentId,
            processedBitmap = a4Bitmap,
            originalBitmap = a4Bitmap,
            cropBorderJson = "",
            enhanceModeId = 1
        )
    }

    suspend fun deleteDocument(document: Document) = withContext(Dispatchers.IO) {
        val pages = pageDao.getPagesListForDocument(document.id)
        pages.forEach { p ->
            File(p.processedImagePath).delete()
            File(p.originalImagePath).delete()
            File(p.thumbnailPath).delete()
        }
        documentDao.deleteDocument(document)
    }

    suspend fun deletePage(page: Page) = withContext(Dispatchers.IO) {
        File(page.processedImagePath).delete()
        File(page.originalImagePath).delete()
        File(page.thumbnailPath).delete()
        pageDao.deletePage(page)

        val remaining = pageDao.getPagesListForDocument(page.documentId)
        documentDao.updatePageCount(page.documentId, remaining.size)
    }
}
