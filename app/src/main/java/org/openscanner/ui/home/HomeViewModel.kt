package org.openscanner.ui.home

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openscanner.OpenScannerApp
import org.openscanner.data.model.Document
import org.openscanner.data.model.Folder
import org.openscanner.data.model.FolderWithCount
import org.openscanner.data.repository.ScannerRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DocSortOrder(val title: String) {
    DATE_DESC("Date (Newest first)"),
    DATE_ASC("Date (Oldest first)"),
    NAME_ASC("Name (A to Z)"),
    PAGE_COUNT_DESC("Pages (Most first)")
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val db = (application as OpenScannerApp).database
    private val repository = ScannerRepository(application, db.documentDao(), db.pageDao(), db.folderDao())

    private val _rawDocuments = MutableStateFlow<List<Document>>(emptyList())
    private val _folders = MutableStateFlow<List<Folder>>(emptyList())
    private val _foldersWithCount = MutableStateFlow<List<FolderWithCount>>(emptyList())
    val foldersWithCount: StateFlow<List<FolderWithCount>> = _foldersWithCount

    private val _selectedFolder = MutableStateFlow<Folder?>(null)
    val selectedFolder: StateFlow<Folder?> = _selectedFolder

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _sortOrder = MutableStateFlow(DocSortOrder.DATE_DESC)
    val sortOrder: StateFlow<DocSortOrder> = _sortOrder

    private val _isSelectMode = MutableStateFlow(false)
    val isSelectMode: StateFlow<Boolean> = _isSelectMode

    private val _selectedDocIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedDocIds: StateFlow<Set<Long>> = _selectedDocIds

    private val _isGridView = MutableStateFlow(false)
    val isGridView: StateFlow<Boolean> = _isGridView

    private val _documents = MutableStateFlow<List<Document>>(emptyList())
    val documents: StateFlow<List<Document>> = _documents

    init {
        loadData()
    }

    private fun loadData() {
        // Collect all folders
        viewModelScope.launch {
            repository.getAllFolders().collectLatest { folderList ->
                _folders.value = folderList
                refreshFolderCounts(folderList, _rawDocuments.value)
            }
        }

        // Collect all documents
        viewModelScope.launch {
            repository.getAllDocuments().collectLatest { docList ->
                _rawDocuments.value = docList
                refreshFolderCounts(_folders.value, docList)
                applyFilterAndSort()
            }
        }
    }

    private fun refreshFolderCounts(folders: List<Folder>, docs: List<Document>) {
        val countMap = docs.filter { it.folderId > 0 }.groupBy { it.folderId }
        val list = folders.map { folder ->
            FolderWithCount(
                folder = folder,
                docCount = countMap[folder.id]?.size ?: 0
            )
        }
        _foldersWithCount.value = list
    }

    private fun applyFilterAndSort() {
        val folder = _selectedFolder.value
        val query = _searchQuery.value.trim().lowercase(Locale.getDefault())
        val sort = _sortOrder.value

        var filtered = _rawDocuments.value

        // Filter by folder if selected
        if (folder != null) {
            filtered = filtered.filter { it.folderId == folder.id }
        }

        // Filter by search query
        if (query.isNotBlank()) {
            filtered = filtered.filter { it.title.lowercase(Locale.getDefault()).contains(query) }
        }

        // Apply sort
        filtered = when (sort) {
            DocSortOrder.DATE_DESC -> filtered.sortedByDescending { it.modifiedTime }
            DocSortOrder.DATE_ASC -> filtered.sortedBy { it.modifiedTime }
            DocSortOrder.NAME_ASC -> filtered.sortedBy { it.title.lowercase(Locale.getDefault()) }
            DocSortOrder.PAGE_COUNT_DESC -> filtered.sortedByDescending { it.pageCount }
        }

        _documents.value = filtered
    }

    fun selectFolder(folder: Folder?) {
        _selectedFolder.value = folder
        applyFilterAndSort()
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        applyFilterAndSort()
    }

    fun setSortOrder(order: DocSortOrder) {
        _sortOrder.value = order
        applyFilterAndSort()
    }

    fun toggleGridView() {
        _isGridView.value = !_isGridView.value
    }

    fun toggleSelectMode() {
        _isSelectMode.value = !_isSelectMode.value
        if (!_isSelectMode.value) {
            _selectedDocIds.value = emptySet()
        }
    }

    fun toggleSelectDoc(id: Long) {
        val current = _selectedDocIds.value.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            current.add(id)
        }
        _selectedDocIds.value = current
        if (current.isNotEmpty() && !_isSelectMode.value) {
            _isSelectMode.value = true
        }
    }

    fun selectAllDocs() {
        _selectedDocIds.value = _documents.value.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selectedDocIds.value = emptySet()
        _isSelectMode.value = false
    }

    fun batchDeleteSelected() {
        val ids = _selectedDocIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.batchDeleteDocuments(ids)
            clearSelection()
        }
    }

    fun batchMoveSelected(targetFolderId: Long) {
        val ids = _selectedDocIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.batchMoveToFolder(ids, targetFolderId)
            clearSelection()
        }
    }

    fun createFolder(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.createFolder(name)
        }
    }

    fun renameFolder(folderId: Long, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            repository.renameFolder(folderId, newName)
        }
    }

    fun deleteFolder(folder: Folder) {
        viewModelScope.launch {
            repository.deleteFolder(folder)
            if (_selectedFolder.value?.id == folder.id) {
                _selectedFolder.value = null
            }
        }
    }

    fun moveDocumentToFolder(docId: Long, folderId: Long) {
        viewModelScope.launch {
            repository.moveDocumentToFolder(docId, folderId)
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
        val currentFolderId = _selectedFolder.value?.id ?: -1L
        viewModelScope.launch(Dispatchers.IO) {
            val title = "Import " + SimpleDateFormat("dd-MM-yyyy HH.mm", Locale.getDefault()).format(Date())
            val docId = repository.createNewDocument(title, currentFolderId)
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
        val currentFolderId = _selectedFolder.value?.id ?: -1L
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            try {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@launch
                val pdfRenderer = PdfRenderer(pfd)
                val count = pdfRenderer.pageCount
                if (count > 0) {
                    val title = "PDF " + SimpleDateFormat("dd-MM-yyyy HH.mm", Locale.getDefault()).format(Date())
                    val docId = repository.createNewDocument(title, currentFolderId)

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
