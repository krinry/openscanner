package org.openscanner.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.openscanner.OpenScannerApp
import org.openscanner.data.model.Document
import org.openscanner.data.repository.ScannerRepository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val db = (application as OpenScannerApp).database
    private val repository = ScannerRepository(application, db.documentDao(), db.pageDao())

    private val _documents = MutableStateFlow<List<Document>>(emptyList())
    val documents: StateFlow<List<Document>> = _documents

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    init {
        loadDocuments()
    }

    private fun loadDocuments() {
        viewModelScope.launch {
            repository.getAllDocuments().collectLatest { list ->
                _documents.value = list
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        viewModelScope.launch {
            if (query.isBlank()) {
                repository.getAllDocuments().collectLatest { _documents.value = it }
            } else {
                repository.searchDocuments(query).collectLatest { _documents.value = it }
            }
        }
    }

    fun deleteDocument(document: Document) {
        viewModelScope.launch {
            repository.deleteDocument(document)
        }
    }

    fun renameDocument(docId: Long, newTitle: String) {
        viewModelScope.launch {
            repository.updateDocumentTitle(docId, newTitle)
        }
    }

    fun importImagesFromGallery(uris: List<Uri>, onDone: (Long) -> Unit) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val title = "Import " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
            val docId = repository.createNewDocument(title)
            val resolver = getApplication<Application>().contentResolver

            for (uri in uris) {
                try {
                    resolver.openInputStream(uri)?.use { stream ->
                        val bmp = BitmapFactory.decodeStream(stream)
                        if (bmp != null) {
                            repository.savePage(
                                documentId = docId,
                                processedBitmap = bmp,
                                originalBitmap = bmp,
                                cropBorderJson = "",
                                enhanceModeId = 1
                            )
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            withContext(Dispatchers.Main) {
                onDone(docId)
            }
        }
    }

    fun importPdfDocument(uri: Uri, onDone: (Long) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            try {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@launch
                val pdfRenderer = PdfRenderer(pfd)
                val count = pdfRenderer.pageCount
                if (count > 0) {
                    val title = "PDF " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                    val docId = repository.createNewDocument(title)

                    for (i in 0 until count) {
                        val page = pdfRenderer.openPage(i)
                        val w = page.width * 2
                        val h = page.height * 2
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()

                        repository.savePage(
                            documentId = docId,
                            processedBitmap = bmp,
                            originalBitmap = bmp,
                            cropBorderJson = "",
                            enhanceModeId = 0
                        )
                    }
                    pdfRenderer.close()
                    pfd.close()

                    withContext(Dispatchers.Main) {
                        onDone(docId)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
