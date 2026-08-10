package io.legado.desktop.data.dao

import io.legado.desktop.constant.AppPattern
import io.legado.desktop.data.entities.ReplaceRule
import io.legado.desktop.utils.cnCompare
import io.legado.desktop.utils.splitNotBlank
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

private const val REPLACE_RULE_GROUP_FILTER = """
trim(:groupName, $GROUP_TRIM_CHARACTERS) <> ''
and exists """

private const val REPLACE_RULE_NO_GROUP_FILTER = """
trim(coalesce(`group`, ''), $GROUP_TRIM_CHARACTERS) in ('', '未分组')
"""

interface ReplaceRuleDao {

    fun flowAll(): Flow<List<ReplaceRule>>

    fun flowSearch(key: String): Flow<List<ReplaceRule>>

    fun flowEnabled(): Flow<List<ReplaceRule>>

    fun flowDisabled(): Flow<List<ReplaceRule>>

    
    fun flowGroupSearch(groupName: String): Flow<List<ReplaceRule>>

    fun flowGroupsUnProcessed(): Flow<List<String>>

    
    fun flowNoGroup(): Flow<List<ReplaceRule>>

val minOrder: Int

val maxOrder: Int

val all: List<ReplaceRule>

val allGroupsUnProcessed: List<String>

val allEnabled: List<ReplaceRule>

    fun findById(id: Long): ReplaceRule?

    fun findByIds(vararg ids: Long): List<ReplaceRule>

    
    fun findEnabledByContentScope(name: String, origin: String): List<ReplaceRule>

    
    fun findEnabledByTitleScope(name: String, origin: String): List<ReplaceRule>

    fun getByGroup(group: String): List<ReplaceRule>

val noGroup: List<ReplaceRule>

val summary: Int

    fun enableAll(enable: Boolean)

    fun insert(vararg replaceRule: ReplaceRule): List<Long>

    fun update(vararg replaceRules: ReplaceRule)

    fun delete(vararg replaceRules: ReplaceRule)

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
}
