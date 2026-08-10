package io.legado.desktop.data.dao

import io.legado.desktop.data.entities.SearchKeyword
import kotlinx.coroutines.flow.Flow


interface SearchKeywordDao {

val all: List<SearchKeyword>

    fun flowByUsage(): Flow<List<SearchKeyword>>

    fun flowByTime(): Flow<List<SearchKeyword>>

    fun flowSearch(key: String): Flow<List<SearchKeyword>>

    fun get(key: String): SearchKeyword?

    fun insert(vararg keywords: SearchKeyword)

    fun update(vararg keywords: SearchKeyword)

    fun delete(vararg keywords: SearchKeyword)

    fun deleteAll()

}