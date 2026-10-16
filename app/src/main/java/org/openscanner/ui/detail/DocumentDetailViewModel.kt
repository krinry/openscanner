package org.openscanner.ui.detail

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openscanner.OpenScannerApp
import org.openscanner.data.model.Document
import org.openscanner.data.model.Page
import org.openscanner.data.repository.ScannerRepository
import org.openscanner.pdf.PdfExportOptions
import org.openscanner.pdf.PdfGenerator
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class DocumentDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val db = (application as OpenScannerApp).database
    private val repository = ScannerRepository(application, db.documentDao(), db.pageDao())

    private val _document = MutableStateFlow<Document?>(null)
    val document: StateFlow<Document?> = _document

    private val _pages = MutableStateFlow<List<Page>>(emptyList())
    val pages: StateFlow<List<Page>> = _pages

    private val _isGeneratingPdf = MutableStateFlow(false)
    val isGeneratingPdf: StateFlow<Boolean> = _isGeneratingPdf

    fun load(docId: Long) {
        viewModelScope.launch {
            _document.value = repository.getDocumentById(docId)
            repository.getPagesForDocument(docId).collectLatest { list ->
                _pages.value = list
            }
        }
    }

    fun renameDocument(newTitle: String) {
        val doc = _document.value ?: return
        if (newTitle.isBlank()) return
        viewModelScope.launch {
            val updated = doc.copy(title = newTitle.trim(), modifiedTime = System.currentTimeMillis())
            repository.updateDocument(updated)
            _document.value = updated
        }
    }

    fun deleteDocument(onDeleted: () -> Unit) {
        val doc = _document.value ?: return
        viewModelScope.launch {
            repository.deleteDocument(doc)
            withContext(Dispatchers.Main) {
                onDeleted()
            }
        }
    }

    fun deletePage(page: Page) {
        viewModelScope.launch {
            repository.deletePage(page)
        }
    }

    fun stampSignature(pageId: Long, signatureBitmap: android.graphics.Bitmap, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.stampSignatureOnPage(pageId, signatureBitmap)
            val docId = _document.value?.id
            if (docId != null) {
                _document.value = repository.getDocumentById(docId)
            }
            withContext(Dispatchers.Main) {
                onDone()
            }
        }
    }

    fun exportAndSharePdf(context: Context, options: PdfExportOptions) {
        val currentDoc = _document.value ?: return
        val currentPages = _pages.value
        if (currentPages.isEmpty()) {
            Toast.makeText(context, "No pages to export", Toast.LENGTH_SHORT).show()
            return
        }

        _isGeneratingPdf.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val pdfDir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
            val cleanTitle = currentDoc.title.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "Document" }
            val pdfFile = File(pdfDir, "${cleanTitle}.pdf")

            val result = PdfGenerator.generatePdf(context, currentPages, options, pdfFile)
            withContext(Dispatchers.Main) {
                _isGeneratingPdf.value = false
                result.fold(
                    onSuccess = { file ->
                        try {
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Document PDF"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Failed to share: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    },
                    onFailure = { err ->
                        Toast.makeText(context, "PDF generation failed: ${err.message}", Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }

    fun savePdfToDownloads(context: Context, options: PdfExportOptions) {
        val currentDoc = _document.value ?: return
        val currentPages = _pages.value
        if (currentPages.isEmpty()) {
            Toast.makeText(context, "No pages to export", Toast.LENGTH_SHORT).show()
            return
        }

        _isGeneratingPdf.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val tempFile = File(context.cacheDir, "export_${System.currentTimeMillis()}.pdf")
            val result = PdfGenerator.generatePdf(context, currentPages, options, tempFile)

            withContext(Dispatchers.Main) {
                _isGeneratingPdf.value = false
                result.fold(
                    onSuccess = { file ->
                        val cleanTitle = currentDoc.title.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").ifBlank { "Scan" }
                        val fileName = "${cleanTitle}.pdf"
                        var savedSuccessfully = false

                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                val contentValues = ContentValues().apply {
                                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                                }
                                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                                if (uri != null) {
                                    context.contentResolver.openOutputStream(uri)?.use { out ->
                                        FileInputStream(file).use { input ->
                                            input.copyTo(out)
                                        }
                                    }
                                    savedSuccessfully = true
                                }
                            } else {
                                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                val target = File(downloadsDir, fileName)
                                FileInputStream(file).use { input ->
                                    FileOutputStream(target).use { out ->
                                        input.copyTo(out)
                                    }
                                }
                                savedSuccessfully = true
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

                        if (savedSuccessfully) {
                            Toast.makeText(context, "Saved to Downloads: $fileName", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Could not save to Downloads directory", Toast.LENGTH_LONG).show()
                        }
                    },
                    onFailure = { err ->
                        Toast.makeText(context, "PDF generation failed: ${err.message}", Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }
}
