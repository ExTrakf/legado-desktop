package io.legado.desktop.data.dao

import com.google.gson.JsonObject
import io.legado.desktop.constant.BookType
import io.legado.desktop.data.entities.Book
import io.legado.desktop.data.entities.BookCacheCleanupSnapshot
import io.legado.desktop.data.entities.BookCacheInfo
import io.legado.desktop.data.entities.BookGroup
import io.legado.desktop.data.entities.BookSource
import io.legado.desktop.help.book.isNotShelf
import io.legado.desktop.utils.GSON
import io.legado.desktop.utils.fromJsonObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface BookDao {

    fun flowByGroup(groupId: Long): Flow<List<Book>> {
        return when (groupId) {
            BookGroup.IdRoot -> flowRoot()
            BookGroup.IdAll -> flowAll()
            BookGroup.IdLocal -> flowLocal()
            BookGroup.IdAudio -> flowAudio()
            BookGroup.IdNetNone -> flowNetNoGroup()
            BookGroup.IdLocalNone -> flowLocalNoGroup()
            BookGroup.IdVideo -> flowVideo()
            BookGroup.IdError -> flowUpdateError()
            else -> flowByUserGroup(groupId)
        }.map { list ->
            list.filterNot { it.isNotShelf }
        }
    }

    
    fun flowRoot(): Flow<List<Book>>

    fun flowAll(): Flow<List<Book>>

    fun flowAudio(): Flow<List<Book>>

    fun flowVideo(): Flow<List<Book>>

    fun flowLocal(): Flow<List<Book>>

    
    fun flowNetNoGroup(): Flow<List<Book>>

    
    fun flowLocalNoGroup(): Flow<List<Book>>

    fun flowByUserGroup(group: Long): Flow<List<Book>>

    fun flowSearch(key: String): Flow<List<Book>>

    fun flowUpdateError(): Flow<List<Book>>

    fun getBooksByGroup(group: Long): List<Book>

    fun findByName(vararg names: String): List<Book>

    fun getBookByFileName(fileName: String): Book?

val localBookFileNames: List<String>

    
    val localBookAlternateOrigins: List<String>

    fun getBook(bookUrl: String): Book?

    fun getBook(name: String, author: String): Book?

    fun getAllUseBookSource(): List<BookSource>

    fun getBookByOrigin(name: String, origin: String): Book?

val noGroupSize: Int

val webBooks: List<Book>

val hasUpdateBooks: List<Book>

val all: List<Book>

    fun getCacheCleanupBooks(): List<BookCacheInfo>

    fun getCacheCleanupImageBooks(): List<Book>

    fun getCacheCleanupSnapshot(includeImageBooks: Boolean): BookCacheCleanupSnapshot {
        return BookCacheCleanupSnapshot(
            books = getCacheCleanupBooks(),
            imageBooks = if (includeImageBooks) getCacheCleanupImageBooks() else emptyList(),
        )
    }

    fun getByTypeOnLine(type: Int): List<Book>

val lastReadBook: Book?

    
    val lastReadBookOnShelf: Book?

val allBookUrls: List<String>

    fun findExistingBookUrls(bookUrls: List<String>): List<String>

val allBookCount: Int

    fun flowShelfBookCount(): Flow<Int>

    
    val readingCount: Int

val minOrder: Int

val maxOrder: Int

    fun has(bookUrl: String): Boolean

    fun has(name: String, author: String): Boolean

    
    fun hasFile(fileName: String): Boolean

    fun insert(vararg book: Book)

    fun insertIgnore(book: Book): Long

    fun update(vararg book: Book)

    fun getReadConfigJson(bookUrl: String): String?

    fun updateReadConfigJson(bookUrl: String, readConfig: String?)

    fun updatePreservingReadConfig(book: Book) {
        val readConfig = getReadConfigJson(book.bookUrl)
        update(book)
        updateReadConfigJson(book.bookUrl, readConfig)
    }

    fun updateAudioPlayMode(bookUrl: String, playMode: Int) {
        updateReadConfigJson(bookUrl, getReadConfigJson(bookUrl).withAudioPlayMode(playMode))
    }

    fun updateAudioPlaySpeed(bookUrl: String, playSpeed: Float) {
        updateReadConfigJson(bookUrl, getReadConfigJson(bookUrl).withAudioPlaySpeed(playSpeed))
    }

    fun delete(vararg book: Book)

    fun replace(oldBook: Book, newBook: Book) {
        delete(oldBook)
        insert(newBook)
    }

    fun upProgress(bookUrl: String, pos: Int)

    fun updateShelfState(bookUrl: String, type: Int, order: Int)

    fun upGroup(oldGroupId: Long, newGroupId: Long)

    fun removeGroup(group: Long)

    fun deleteNotShelfBook()
}

internal fun String?.withAudioPlayMode(playMode: Int): String {
    return withAudioPlayPreference("playMode", playMode)
}

internal fun String?.withAudioPlaySpeed(playSpeed: Float): String {
    return withAudioPlayPreference("playSpeed", playSpeed)
}

private fun String?.withAudioPlayPreference(key: String, value: Number): String {
    val readConfig = GSON.fromJsonObject<JsonObject>(this).getOrNull() ?: JsonObject().apply {
        addProperty("useGlobalAudioSkip", true)
    }
    readConfig.addProperty(key, value)
    return GSON.toJson(readConfig)
}
