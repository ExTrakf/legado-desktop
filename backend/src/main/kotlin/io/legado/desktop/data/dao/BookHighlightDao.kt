package io.legado.desktop.data.dao

import io.legado.desktop.data.entities.BookHighlight
import kotlinx.coroutines.flow.Flow

interface BookHighlightDao {

    
    val all: List<BookHighlight>

    
    fun getByBook(bookUrl: String): List<BookHighlight>

    
    fun flowByBook(bookUrl: String): Flow<List<BookHighlight>>

    
    fun flowSearch(bookUrl: String, key: String): Flow<List<BookHighlight>>

    fun insert(vararg highlight: BookHighlight)

    
    fun pinLayoutTitleLength(
        bookUrl: String,
        chapterUrl: String,
        layoutTitleLength: Int
    )

    
    fun bindChapterUrl(times: List<Long>, chapterUrl: String)

    
    fun updateBookMetadata(bookUrl: String, bookName: String, bookAuthor: String)

    fun update(highlight: BookHighlight)

    fun delete(vararg highlight: BookHighlight)
}
