package io.legado.desktop.data.dao

import io.legado.desktop.constant.AppPattern
import io.legado.desktop.data.entities.BookSource
import io.legado.desktop.data.entities.BookSourcePart
import io.legado.desktop.utils.cnCompare
import io.legado.desktop.utils.splitNotBlank
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

private const val BOOK_SOURCE_NO_GROUP_FILTER = """
trim(coalesce(bookSourceGroup, ''), $GROUP_TRIM_CHARACTERS) in ('', '未分组')
"""

interface BookSourceDao {

    fun flowAll(): Flow<List<BookSourcePart>>

    
    fun flowSearch(searchKey: String): Flow<List<BookSourcePart>>

    
    fun search(searchKey: String): List<BookSource>

    
    fun flowSearchEnabled(searchKey: String): Flow<List<BookSourcePart>>

    
    fun flowGroupSearch(sourceGroup: String): Flow<List<BookSourcePart>>

    
    fun groupSearch(sourceGroup: String): List<BookSource>

    fun flowEnabled(): Flow<List<BookSourcePart>>

    fun flowDisabled(): Flow<List<BookSourcePart>>

    
    fun flowExplore(): Flow<List<BookSourcePart>>

    fun flowLogin(): Flow<List<BookSourcePart>>

    
    fun flowNoGroup(): Flow<List<BookSourcePart>>

    fun flowEnabledExplore(): Flow<List<BookSourcePart>>

    fun flowDisabledExplore(): Flow<List<BookSourcePart>>

    
    fun flowExplore(key: String): Flow<List<BookSourcePart>>

    
    fun flowGroupExplore(sourceGroup: String): Flow<List<BookSourcePart>>

    fun flowGroupsUnProcessed(): Flow<List<String>>

    
    fun flowEnabledGroupsUnProcessed(): Flow<List<String>>

    
    fun flowExploreGroupsUnProcessed(): Flow<List<String>>

    
    fun getByGroup(group: String): List<BookSource>

    
    fun getEnabledByGroup(group: String): List<BookSource>

    
    fun getEnabledPartByGroup(sourceGroup: String): List<BookSourcePart>

    
    fun getEnabledByType(type: Int): List<BookSource>

    fun getBookSourceAddBook(baseUrl: String): BookSource?

    
    val hasBookUrlPattern: List<BookSourcePart>

val noGroup: List<BookSource>

val all: List<BookSource>

val allPart: List<BookSourcePart>

val allEnabled: List<BookSource>

val allEnabledPart: List<BookSourcePart>

val allDisabled: List<BookSource>

    
    val allNoGroup: List<BookSource>

val allEnabledExplore: List<BookSource>

val allDisabledExplore: List<BookSource>

val allLogin: List<BookSource>

    
    val allTextEnabledPart: List<BookSourcePart>

    
    val allGroupsUnProcessed: List<String>

    
    val allEnabledGroupsUnProcessed: List<String>

    fun getBookSource(key: String): BookSource?

    fun getBookSources(keys: List<String>): List<BookSource>

    fun getBookSourcePart(key: String): BookSourcePart?

    fun allCount(): Int

    fun has(key: String): Boolean

    fun getMainJs(key: String): String?

    fun hasJsSource(key: String): Boolean {
        return !getMainJs(key).isNullOrBlank()
    }

    fun insert(vararg bookSource: BookSource)

    fun update(vararg bookSource: BookSource)

    
    fun updateCheckResult(
        bookSourceUrl: String,
        bookSourceGroup: String?,
        bookSourceComment: String?,
        respondTime: Long,
        expectedLastUpdateTime: Long,
        expectedBookSourceGroup: String?,
        expectedBookSourceComment: String?,
        expectedRespondTime: Long,
    ): Int

    fun delete(vararg bookSource: BookSource)

    fun delete(key: String)

    fun delete(bookSources: List<BookSourcePart>) {
        for (bs in bookSources) {
            delete(bs.bookSourceUrl)
        }
    }

val minOrder: Int

val maxOrder: Int

    
    val hasDuplicateOrder: Boolean

    fun enable(bookSourceUrl: String, enable: Boolean)

    fun enable(enable: Boolean, bookSources: List<BookSourcePart>) {
        for (bs in bookSources) {
            enable(bs.bookSourceUrl, enable)
        }
    }

    fun enableExplore(bookSourceUrl: String, enable: Boolean)

    fun enableExplore(enable: Boolean, bookSources: List<BookSourcePart>) {
        for (bs in bookSources) {
            enableExplore(bs.bookSourceUrl, enable)
        }
    }

    
    fun upOrder(bookSourceUrl: String, customOrder: Int)

    fun upOrder(bookSources: List<BookSourcePart>) {
        for (bs in bookSources) {
            upOrder(bs.bookSourceUrl, bs.customOrder)
        }
    }

    fun upOrder(bookSource: BookSourcePart) {
        upOrder(bookSource.bookSourceUrl, bookSource.customOrder)
    }

    
    fun upGroup(bookSourceUrl: String, bookSourceGroup: String)

    fun upGroup(bookSources: List<BookSourcePart>) {
        for (bs in bookSources) {
            bs.bookSourceGroup?.let { upGroup(bs.bookSourceUrl, it) }
        }
    }

    private fun dealGroups(list: List<String>): List<String> {
        val groups = linkedSetOf<String>()
        list.forEach {
            it.splitNotBlank(AppPattern.splitGroupRegex).forEach { group ->
                groups.add(group)
            }
        }
        return groups.sortedWith { o1, o2 ->
            o1.cnCompare(o2)
        }
    }

    fun allGroups(): List<String> = dealGroups(allGroupsUnProcessed)

    fun allEnabledGroups(): List<String> = dealGroups(allEnabledGroupsUnProcessed)

    fun flowGroups(): Flow<List<String>> {
        return flowGroupsUnProcessed().map { list ->
            dealGroups(list)
        }.flowOn(IO)
    }

    fun flowExploreGroups(): Flow<List<String>> {
        return flowExploreGroupsUnProcessed().map { list ->
            dealGroups(list)
        }.flowOn(IO)
    }

    fun flowEnabledGroups(): Flow<List<String>> {
        return flowEnabledGroupsUnProcessed().map { list ->
            dealGroups(list)
        }.flowOn(IO)
    }
}
