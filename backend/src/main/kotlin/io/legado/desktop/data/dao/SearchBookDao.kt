package io.legado.desktop.data.dao

import io.legado.desktop.data.entities.SearchBook

internal const val GROUP_TRIM_CHARACTERS =
    "char(9,10,11,12,13,28,29,30,31,32,160,5760,8192,8193,8194,8195,8196," +
        "8197,8198,8199,8200,8201,8202,8232,8233,8239,8287,12288)"

internal const val NON_EMPTY_SOURCE_GROUP_CONDITION =
    "trim(:sourceGroup, $GROUP_TRIM_CHARACTERS) <> ''"

internal const val SOURCE_GROUP_MEMBERSHIP_FILTER = """
and """

interface SearchBookDao {

    fun getSearchBook(bookUrl: String): SearchBook?

    fun getFirstByNameAuthor(name: String, author: String): SearchBook?

    
    fun changeSourceByGroup(name: String, author: String, sourceGroup: String): List<SearchBook>

    
    fun changeSourceSearch(
        name: String,
        author: String,
        key: String,
        sourceGroup: String
    ): List<SearchBook>

    
    fun getEnableHasCover(name: String, author: String): List<SearchBook>

    fun insert(vararg searchBook: SearchBook): List<Long>

    fun clear(name: String, author: String)

    fun clearExpired(time: Long)

    fun update(vararg searchBook: SearchBook)

    fun delete(vararg searchBook: SearchBook)
}
