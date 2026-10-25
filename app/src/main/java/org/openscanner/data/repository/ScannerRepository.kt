package org.openscanner.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.openscanner.cv.BookSpineDetector
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

    // --- Hierarchical Folder Queries & Mutations ---
    fun getAllFolders(): Flow<List<Folder>> = folderDao.getAllFolders()

    fun getRootFolders(): Flow<List<Folder>> = folderDao.getRootFolders()

    fun getSubfolders(parentFolderId: Long): Flow<List<Folder>> = folderDao.getSubfolders(parentFolderId)

    suspend fun createFolder(
        name: String,
        parentFolderId: Long = -1L,
        color: String = "#EEAA3B"
    ): Long = withContext(Dispatchers.IO) {
        val parent = if (parentFolderId > 0) folderDao.getFolderById(parentFolderId) else null
        val parentPath = parent?.let { "${it.path}${it.id}/" } ?: "/"
        val folder = Folder(
            name = name.trim(),
            color = color,
            parentFolderId = parentFolderId,
            path = parentPath
        )
        folderDao.insertFolder(folder)
    }

    suspend fun createFolder(
        name: String,
        color: String
    ): Long = createFolder(name = name, parentFolderId = -1L, color = color)

    suspend fun getBreadcrumbs(folderId: Long): List<Folder> = withContext(Dispatchers.IO) {
        if (folderId <= 0) return@withContext emptyList()
        val trail = mutableListOf<Folder>()
        var currentId: Long? = folderId
        val visited = mutableSetOf<Long>()
        while (currentId != null && currentId > 0 && visited.add(currentId)) {
            val folder = folderDao.getFolderById(currentId) ?: break
            trail.add(0, folder)
            currentId = folder.parentFolderId.takeIf { it > 0 }
        }
        trail
    }

    suspend fun moveFolder(folderId: Long, targetParentFolderId: Long) = withContext(Dispatchers.IO) {
        val folder = folderDao.getFolderById(folderId) ?: return@withContext
        if (folder.id == targetParentFolderId) return@withContext

        // Prevent moving a folder into its own descendant
        if (targetParentFolderId > 0) {
            val targetBreadcrumbs = getBreadcrumbs(targetParentFolderId)
            if (targetBreadcrumbs.any { it.id == folderId }) {
                throw IllegalArgumentException("Cannot move folder into one of its descendants")
            }
        }

        val oldDescendantPrefix = "${folder.path}${folder.id}/"
        val targetParent = if (targetParentFolderId > 0) folderDao.getFolderById(targetParentFolderId) else null
        val newParentPath = targetParent?.let { "${it.path}${it.id}/" } ?: "/"
        val newDescendantPrefix = "${newParentPath}${folder.id}/"

        // Update the folder itself
        folderDao.updateFolder(folder.copy(parentFolderId = targetParentFolderId, path = newParentPath))

        // Update all descendant paths
        folderDao.updateFolderPathPrefix(oldPrefix = oldDescendantPrefix, newPrefix = newDescendantPrefix)
    }

    suspend fun deleteFolderCascade(folder: Folder, deleteContainedDocuments: Boolean = false) = withContext(Dispatchers.IO) {
        val descendantPrefix = "${folder.path}${folder.id}/"
        val allFolders = folderDao.getAllFoldersList()
        val affectedFolders = allFolders.filter { it.id == folder.id || it.path.startsWith(descendantPrefix) }

        for (f in affectedFolders) {
            if (deleteContainedDocuments) {
                val docs = documentDao.getDocumentsListInFolder(f.id)
                docs.forEach { deleteDocument(it) }
            } else {
                documentDao.unlinkDocumentsFromFolder(f.id)
            }
            folderDao.deleteFolderById(f.id)
        }
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

    suspend fun copyDocumentToFolder(docId: Long, targetFolderId: Long): Long = withContext(Dispatchers.IO) {
        val origDoc = documentDao.getDocumentById(docId)
            ?: throw IllegalArgumentException("Document with id $docId not found")
        val origPages = pageDao.getPagesListForDocument(docId)

        val scansDir = File(context.filesDir, "scans").apply { if (!exists()) mkdirs() }
        val thumbsDir = File(context.filesDir, "thumbs").apply { if (!exists()) mkdirs() }

        val now = System.currentTimeMillis()
        val newDoc = Document(
            title = "${origDoc.title} (Copy)",
            createdTime = now,
            modifiedTime = now,
            pageCount = origPages.size,
            tags = origDoc.tags,
            folderId = targetFolderId
        )
        val newDocId = documentDao.insertDocument(newDoc)

        val copiedFiles = mutableListOf<File>()
        try {
            val newPages = mutableListOf<Page>()
            for ((idx, origPage) in origPages.withIndex()) {
                val timestamp = now + idx
                val newProcessed = File(scansDir, "page_${newDocId}_${timestamp}.jpg")
                val newOriginal = File(scansDir, "orig_${newDocId}_${timestamp}.jpg")
                val newThumb = File(thumbsDir, "thumb_${newDocId}_${timestamp}.jpg")

                val origProcessedFile = File(origPage.processedImagePath)
                if (origProcessedFile.exists()) {
                    origProcessedFile.copyTo(newProcessed, overwrite = true)
                    copiedFiles.add(newProcessed)
                }

                val origOriginalFile = File(origPage.originalImagePath)
                if (origOriginalFile.exists()) {
                    origOriginalFile.copyTo(newOriginal, overwrite = true)
                    copiedFiles.add(newOriginal)
                } else if (newProcessed.exists()) {
                    newProcessed.copyTo(newOriginal, overwrite = true)
                    copiedFiles.add(newOriginal)
                }

                val origThumbFile = File(origPage.thumbnailPath)
                if (origThumbFile.exists()) {
                    origThumbFile.copyTo(newThumb, overwrite = true)
                    copiedFiles.add(newThumb)
                }

                val newPage = Page(
                    documentId = newDocId,
                    pageIndex = origPage.pageIndex,
                    processedImagePath = if (newProcessed.exists()) newProcessed.absolutePath else origPage.processedImagePath,
                    originalImagePath = if (newOriginal.exists()) newOriginal.absolutePath else origPage.originalImagePath,
                    thumbnailPath = if (newThumb.exists()) newThumb.absolutePath else origPage.thumbnailPath,
                    cropBorderJson = origPage.cropBorderJson,
                    enhanceMode = origPage.enhanceMode,
                    rotationDegrees = origPage.rotationDegrees,
                    brightness = origPage.brightness,
                    contrast = origPage.contrast,
                    ocrText = origPage.ocrText,
                    watermarkText = origPage.watermarkText,
                    createdTime = now
                )
                newPages.add(newPage)
            }

            if (newPages.isNotEmpty()) {
                pageDao.insertPages(newPages)
            }
            newDocId
        } catch (e: Exception) {
            copiedFiles.forEach { it.delete() }
            documentDao.deleteDocument(newDoc.copy(id = newDocId))
            throw e
        }
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
        val defaultTitle = title ?: ("New Doc " + SimpleDateFormat("MM-dd-yyyy HH.mm", Locale.getDefault()).format(Date()))
        val doc = Document(title = defaultTitle, folderId = folderId)
        documentDao.insertDocument(doc)
    }

    suspend fun renameDocument(docId: Long, title: String) = withContext(Dispatchers.IO) {
        documentDao.updateDocumentTitle(docId, title)
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

    suspend fun stampSignatureOnPage(
        pageId: Long,
        signatureBitmap: Bitmap,
        relX: Float = 0.55f,
        relY: Float = 0.75f,
        relWidth: Float = 0.35f,
        relHeight: Float = -1f
    ) = withContext(Dispatchers.IO) {
        val page = pageDao.getPageById(pageId) ?: return@withContext
        val processedFile = File(page.processedImagePath)
        if (!processedFile.exists()) return@withContext

        val origBitmap = BitmapFactory.decodeFile(processedFile.absolutePath) ?: return@withContext
        val mutable = origBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(mutable)

        val targetWidth = (mutable.width * relWidth).toInt().coerceAtLeast(10)
        val targetHeight = if (relHeight > 0f) {
            (mutable.height * relHeight).toInt().coerceAtLeast(10)
        } else {
            (targetWidth * signatureBitmap.height / signatureBitmap.width.toFloat()).toInt().coerceAtLeast(10)
        }

        val left = (mutable.width * relX).toInt().coerceIn(0, (mutable.width - targetWidth).coerceAtLeast(0))
        val top = (mutable.height * relY).toInt().coerceIn(0, (mutable.height - targetHeight).coerceAtLeast(0))
        val destRect = android.graphics.Rect(left, top, left + targetWidth, top + targetHeight)

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
        layout: CollageLayout,
        watermarkConfig: org.openscanner.cv.CollageWatermarkConfig? = null,
        slotTransforms: Map<Int, org.openscanner.cv.SlotTransform>? = null
    ): Page = withContext(Dispatchers.IO) {
        val targetSlotRects = CollageGenerator.calculateSlotRects(layout)
        val bitmaps = mutableListOf<Bitmap>()
        val selectedIds = pageIds.take(layout.maxPages)
        for ((index, pId) in selectedIds.withIndex()) {
            val page = pageDao.getPageById(pId)
            if (page != null && File(page.processedImagePath).exists()) {
                val slot = targetSlotRects.getOrNull(index)
                val bmp = if (slot != null) {
                    CollageGenerator.decodeSampledBitmap(
                        filePath = page.processedImagePath,
                        reqWidth = slot.right - slot.left,
                        reqHeight = slot.bottom - slot.top
                    )
                } else {
                    BitmapFactory.decodeFile(page.processedImagePath)
                }
                if (bmp != null) {
                    bitmaps.add(bmp)
                }
            }
        }

        if (bitmaps.isEmpty()) {
            throw IllegalStateException("No valid page images found for collage")
        }

        val collageBitmap = try {
            CollageGenerator.createCollage(
                bitmaps = bitmaps,
                layout = layout,
                watermarkConfig = watermarkConfig,
                slotTransforms = slotTransforms
            )
        } finally {
            // Recycle intermediate downsampled page bitmaps to prevent heap bloat
            bitmaps.forEach { bmp ->
                if (!bmp.isRecycled) {
                    bmp.recycle()
                }
            }
        }

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
        targetDocId: Long? = null
    ): Page = withContext(Dispatchers.IO) {
        val docId = if (targetDocId != null && targetDocId > 0) {
            targetDocId
        } else {
            createNewDocument("ID Card Scan")
        }

        val a4Width = 2480
        val a4Height = 3508
        val a4Bitmap = Bitmap.createBitmap(a4Width, a4Height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(a4Bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)

        val cardW = (a4Width * 0.70f).toInt() // 1736 px
        val cardH = Math.round(cardW / 1.586f).toInt() // 1095 px
        val cardX = (a4Width - cardW) / 2 // 372 px

        val frontY = (a4Height * 0.20f).toInt() // 701 px
        val backY = (a4Height * 0.56f).toInt() // 1964 px

        val cardCornerRadius = 16f

        val bitmapPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            isDither = true
        }

        val borderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#E0E0E0")
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 4f
        }

        // Draw Front Card with rounded clipping and #E0E0E0 border
        val frontRectF = android.graphics.RectF(
            cardX.toFloat(),
            frontY.toFloat(),
            (cardX + cardW).toFloat(),
            (frontY + cardH).toFloat()
        )
        val frontPath = android.graphics.Path().apply {
            addRoundRect(frontRectF, cardCornerRadius, cardCornerRadius, android.graphics.Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(frontPath)
        canvas.drawBitmap(frontBitmap, null, frontRectF, bitmapPaint)
        canvas.restore()
        canvas.drawRoundRect(frontRectF, cardCornerRadius, cardCornerRadius, borderPaint)

        // Draw Back Card with rounded clipping and #E0E0E0 border
        val backRectF = android.graphics.RectF(
            cardX.toFloat(),
            backY.toFloat(),
            (cardX + cardW).toFloat(),
            (backY + cardH).toFloat()
        )
        val backPath = android.graphics.Path().apply {
            addRoundRect(backRectF, cardCornerRadius, cardCornerRadius, android.graphics.Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(backPath)
        canvas.drawBitmap(backBitmap, null, backRectF, bitmapPaint)
        canvas.restore()
        canvas.drawRoundRect(backRectF, cardCornerRadius, cardCornerRadius, borderPaint)

        savePage(
            documentId = docId,
            processedBitmap = a4Bitmap,
            originalBitmap = a4Bitmap,
            cropBorderJson = "",
            enhanceModeId = 1
        )
    }

    /**
     * Splits a book spread bitmap into Left and Right pages, applies 1D cylindrical curvature unwarping
     * and crease shadow compensation, and saves both as two consecutive document pages.
     *
     * @return Pair of (leftPageId, rightPageId)
     */
    suspend fun splitAndSaveBookPages(
        spreadBitmap: Bitmap,
        spineX: Int,
        targetDocId: Long? = null
    ): Pair<Long, Long> = withContext(Dispatchers.IO) {
        val docId = if (targetDocId != null && targetDocId > 0) {
            targetDocId
        } else {
            val defaultTitle = "Book Scan " + SimpleDateFormat("dd-MM-yyyy HH.mm", Locale.getDefault()).format(Date())
            createNewDocument(title = defaultTitle)
        }

        val width = spreadBitmap.width
        val validSpineX = spineX.coerceIn(
            (width * 0.15f).toInt().coerceAtLeast(1),
            (width * 0.85f).toInt().coerceAtMost(width - 1)
        )

        val (unwarpedLeft, unwarpedRight) = BookSpineDetector.splitAndUnwarpSpread(
            spreadBitmap = spreadBitmap,
            spineX = validSpineX
        )

        val leftPage = try {
            savePage(
                documentId = docId,
                processedBitmap = unwarpedLeft,
                originalBitmap = unwarpedLeft,
                cropBorderJson = "",
                enhanceModeId = 1
            )
        } finally {
            if (!unwarpedLeft.isRecycled) {
                unwarpedLeft.recycle()
            }
        }

        val rightPage = try {
            savePage(
                documentId = docId,
                processedBitmap = unwarpedRight,
                originalBitmap = unwarpedRight,
                cropBorderJson = "",
                enhanceModeId = 1
            )
        } finally {
            if (!unwarpedRight.isRecycled) {
                unwarpedRight.recycle()
            }
        }

        Pair(leftPage.id, rightPage.id)
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
