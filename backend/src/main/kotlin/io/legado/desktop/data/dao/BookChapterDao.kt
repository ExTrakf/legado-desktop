package io.legado.desktop.data.dao

import io.legado.desktop.data.entities.BookChapter

interface BookChapterDao {

    fun search(bookUrl: String, key: String): List<BookChapter>

    fun search(bookUrl: String, key: String, start: Int, end: Int): List<BookChapter>

    fun searchIndexes(bookUrl: String, key: String, start: Int, end: Int): List<Int>

    fun getChapterList(bookUrl: String): List<BookChapter>

    fun getChapterList(bookUrl: String, start: Int, end: Int): List<BookChapter>

    fun getChapter(bookUrl: String, index: Int): BookChapter?

    fun getChapter(bookUrl: String, title: String): BookChapter?

    fun getChapterCount(bookUrl: String): Int

    fun insert(vararg bookChapter: BookChapter)

    fun update(vararg bookChapter: BookChapter)

    fun updateContentMetadata(bookUrl: String, index: Int, title: String, imgUrl: String?)

    fun delByBook(bookUrl: String)

    fun upWordCount(bookUrl: String, url: String, wordCount: String)

}
