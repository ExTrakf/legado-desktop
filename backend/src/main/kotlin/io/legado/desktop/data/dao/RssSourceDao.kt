package io.legado.desktop.data.dao

import io.legado.desktop.constant.AppPattern
import io.legado.desktop.data.entities.RssSource
import io.legado.desktop.utils.cnCompare
import io.legado.desktop.utils.splitNotBlank
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

private const val RSS_SOURCE_GROUP_FILTER = """
trim(:sourceGroup, $GROUP_TRIM_CHARACTERS) <> ''
and exists """

private const val RSS_SOURCE_NO_GROUP_FILTER = """
trim(coalesce(sourceGroup, ''), $GROUP_TRIM_CHARACTERS) in ('', '未分组')
"""

interface RssSourceDao {

    fun getByKey(key: String): RssSource?

    fun getRssSources(vararg sourceUrls: String): List<RssSource>

    fun findExistingSourceUrls(sourceUrls: List<String>): List<String>

val all: List<RssSource>

val size: Int

    fun flowAll(): Flow<List<RssSource>>

    
    fun flowSearch(key: String): Flow<List<RssSource>>

    
    fun flowGroupSearch(sourceGroup: String): Flow<List<RssSource>>

    fun flowEnabled(): Flow<List<RssSource>>

    fun flowDisabled(): Flow<List<RssSource>>

    fun flowLogin(): Flow<List<RssSource>>

    
    fun flowNoGroup(): Flow<List<RssSource>>

    
    fun flowEnabled(searchKey: String): Flow<List<RssSource>>

    
    fun flowEnabledByGroup(sourceGroup: String): Flow<List<RssSource>>

    fun flowGroupsUnProcessed(): Flow<List<String>>

    fun flowEnabledGroupsUnProcessed(): Flow<List<String>>

val allGroupsUnProcessed: List<String>

val minOrder: Int

val maxOrder: Int

    fun insert(vararg rssSource: RssSource)

    fun update(vararg rssSource: RssSource)

    fun delete(vararg rssSource: RssSource)

    fun delete(sourceUrl: String)

    fun deleteDefault()

val noGroup: List<RssSource>

    fun getByGroup(group: String): List<RssSource>

    fun has(key: String): Boolean

    fun enable(sourceUrl: String, enable: Boolean)

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

    fun flowGroups(): Flow<List<String>> {
        return flowGroupsUnProcessed().map { list ->
            dealGroups(list)
        }.flowOn(IO)
    }

    fun flowEnabledGroups(): Flow<List<String>> {
        return flowEnabledGroupsUnProcessed().map { list ->
            dealGroups(list)
        }.flowOn(IO)
    }

}
