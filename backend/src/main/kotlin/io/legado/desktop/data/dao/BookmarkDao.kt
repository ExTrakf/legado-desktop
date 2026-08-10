package io.legado.desktop.data.dao

import io.legado.desktop.data.entities.Bookmark
import kotlinx.coroutines.flow.Flow


interface BookmarkDao {

    
    val all: List<Bookmark>

    fun flowAll(): Flow<List<Bookmark>>

    
    fun flowByBook(bookName: String, bookAuthor: String): Flow<List<Bookmark>>

    
    fun flowSearch(bookName: String, bookAuthor: String, key: String): Flow<List<Bookmark>>

    
    fun getByBook(bookName: String, bookAuthor: String): List<Bookmark>

    
    fun search(bookName: String, bookAuthor: String, key: String): List<Bookmark>

    fun insert(vararg bookmark: Bookmark)

    fun update(bookmark: Bookmark)

    fun delete(vararg bookmark: Bookmark)

}
