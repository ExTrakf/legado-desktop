package io.legado.desktop.data.dao

import io.legado.desktop.data.entities.DictRule
import kotlinx.coroutines.flow.Flow


interface DictRuleDao {

val all: List<DictRule>

val enabled: List<DictRule>

    fun flowAll(): Flow<List<DictRule>>

    fun getByName(name: String): DictRule?

    fun insert(vararg dictRule: DictRule)

    fun update(vararg dictRule: DictRule)

    fun delete(vararg dictRule: DictRule)

}