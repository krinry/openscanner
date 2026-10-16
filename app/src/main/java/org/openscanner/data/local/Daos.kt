package org.openscanner.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.openscanner.data.model.Document
import org.openscanner.data.model.Page

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY modifiedTime DESC")
    fun getAllDocuments(): Flow<List<Document>>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getDocumentById(id: Long): Document?

    @Query("""
        SELECT DISTINCT d.* FROM documents d 
        LEFT JOIN pages p ON d.id = p.documentId 
        WHERE d.title LIKE '%' || :query || '%' OR (p.ocrText IS NOT NULL AND p.ocrText LIKE '%' || :query || '%') 
        ORDER BY d.modifiedTime DESC
    """)
    fun searchDocuments(query: String): Flow<List<Document>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: Document): Long

    @Update
    suspend fun updateDocument(document: Document)

    @Delete
    suspend fun deleteDocument(document: Document)

    @Query("UPDATE documents SET pageCount = :count, modifiedTime = :time WHERE id = :docId")
    suspend fun updatePageCount(docId: Long, count: Int, time: Long = System.currentTimeMillis())
}

@Dao
interface PageDao {
    @Query("SELECT * FROM pages WHERE documentId = :docId ORDER BY pageIndex ASC")
    fun getPagesForDocument(docId: Long): Flow<List<Page>>

    @Query("SELECT * FROM pages WHERE documentId = :docId ORDER BY pageIndex ASC")
    suspend fun getPagesListForDocument(docId: Long): List<Page>

    @Query("SELECT * FROM pages WHERE id = :pageId")
    suspend fun getPageById(pageId: Long): Page?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPage(page: Page): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPages(pages: List<Page>): List<Long>

    @Update
    suspend fun updatePage(page: Page)

    @Delete
    suspend fun deletePage(page: Page)

    @Query("DELETE FROM pages WHERE documentId = :docId")
    suspend fun deletePagesForDocument(docId: Long)
}
